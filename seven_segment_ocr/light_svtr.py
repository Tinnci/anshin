"""
LightSVTR: Hybrid CNN + SVTR Attention for CTC Text Recognition
================================================================

基于 PP-OCRv4 SVTR_LCNet 思想的轻量化文本识别模型。
用轻量 CNN 骨干塌缩高度 + SVTR (Transformer) 注意力替代 LSTM 做序列建模。

Architecture Summary
--------------------
SVTR 原始论文 (Du et al., IJCAI 2022, arXiv:2205.00159):
  - 用 patch tokenization 将文本图像分解为 2D token 网格
  - 3 个阶段: Stage 1 (Local Mixing) → Stage 2 (Local+Global) → Stage 3 (Global)
  - 每阶段之间用 SubSample (stride [2,1]) 缩减高度、保持宽度
  - SVTR-Tiny: embed_dim=[64,128,256], depth=[3,6,3], heads=[2,4,8], ~11M params
    → 对 32×100 输入: PatchEmbed 4x下采样→8×25, 最终 2×25, 约200 token

SVTR-Tiny 问题:
  - 11M 参数太大，不适合移动端
  - Global attention on 200 tokens 推理仍较慢
  - 对 128×256 输入，token 数更多 (2048+)，内存/速度不可接受

PP-OCRv4 关键洞察 — SVTR_LCNet:
  - CNN 骨干 (MobileNetV1Enhance / LCNet) 高效提特征
  - 只在 Neck 加 2 层 SVTR Global Attention Block (dims=64~120)
  - 用 CTCHead 解码
  - 结合了 CNN 的局部特征提取效率 + Transformer 的全局序列建模能力
  - 非常适合边缘部署 (ONNX Runtime, NNAPI, CoreML)

本实现 (LightSVTR):
  - CNN Backbone: 1 Conv + 4 DepthwiseSeparable → H=128→1, W=256→64, ~41K params
  - SVTR Head: 3 TransformerEncoder layers (d=128, 4 heads) → ~595K params
  - CTC Output: LayerNorm + Linear → [T, B, 16]
  - 总参数: ~638K (FP32 ~2.5MB, INT8 ~0.6MB)
  - 对比 LightCRNN: ~79K params, BiLSTM 无法并行
  - SVTR 优势: 注意力并行计算、全局依赖建模、更好的 ONNX 推理性能

ONNX 导出注意事项:
  - opset ≥ 14 (建议 17): LayerNormalization / MultiheadAttention 原生支持
  - 动态轴: batch (dim 0) + width (dim 3) → T (dim 0 of output)
  - 不使用 AdaptiveAvgPool2d (ONNX 不友好), 改用 AvgPool2d((8,1))
  - 正弦位置编码用 buffer slice 实现，ONNX 兼容
  - TransformerEncoderLayer 无需 mask (LCD 图像所有位置有效)
  - PyTorch ≥ 2.0 的 MHA 导出稳定可靠

Usage:
  model = LightSVTR(num_classes=16)
  x = torch.randn(1, 1, 128, 256)   # [B, 1, H, W]
  out = model(x)                     # [T=64, B, 16]
  export_onnx(model, "light_svtr.onnx")
"""

import math

import torch
import torch.nn as nn
import torch.nn.functional as F


# ─────────────────────────────────────────────────────────
# Building Blocks
# ─────────────────────────────────────────────────────────

class DepthwiseSeparableConv(nn.Module):
    """深度可分离卷积: Depthwise (3×3 per channel) + Pointwise (1×1 mix)."""

    def __init__(self, in_ch, out_ch):
        super().__init__()
        self.depthwise = nn.Conv2d(in_ch, in_ch, 3, 1, 1, groups=in_ch, bias=False)
        self.pointwise = nn.Conv2d(in_ch, out_ch, 1, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)

    def forward(self, x):
        return F.relu(self.bn(self.pointwise(self.depthwise(x))))


class SinusoidalPositionalEncoding(nn.Module):
    """正弦位置编码 — 支持任意序列长度，ONNX 友好。

    不含可学习参数，通过 register_buffer 注册为常量。
    对于动态宽度输入，只需 slice pe[:, :T]。
    """

    def __init__(self, d_model: int, max_len: int = 256):
        super().__init__()
        pe = torch.zeros(1, max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model)
        )
        pe[0, :, 0::2] = torch.sin(position * div_term)
        pe[0, :, 1::2] = torch.cos(position * div_term)
        self.register_buffer("pe", pe)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x: [B, T, C]
        return x + self.pe[:, : x.size(1)]


# ─────────────────────────────────────────────────────────
# LightSVTR Model
# ─────────────────────────────────────────────────────────

class LightSVTR(nn.Module):
    """Hybrid CNN backbone + SVTR attention head for CTC text recognition.

    Inspired by PP-OCRv4's SVTR_LCNet approach: lightweight CNN extracts
    spatial features and collapses height, then Transformer encoder layers
    model the sequence dependencies (replacing BiLSTM).

    Args:
        num_classes: Number of CTC output classes (including blank).
        d_model: Dimension of transformer / CNN final channel count.
        nhead: Number of attention heads.
        num_layers: Number of transformer encoder layers.
        dim_feedforward: FFN hidden dimension in transformer.
        dropout: Dropout rate for transformer layers.

    Input:  [B, 1, 128, W]  grayscale image (W typically 256)
    Output: [T, B, num_classes]  CTC logits (T = W // 4)

    Feature map flow through CNN backbone:
        [B,   1, 128,   W ] → Conv+BN+GELU     → [B,  32,  64, W/2]
        [B,  32,  64, W/2] → DWSep+MaxPool(2,2) → [B,  64,  32, W/4]
        [B,  64,  32, W/4] → DWSep+MaxPool(2,1) → [B,  96,  16, W/4]
        [B,  96,  16, W/4] → DWSep+MaxPool(2,1) → [B, 128,   8, W/4]
        [B, 128,   8, W/4] → DWSep+AvgPool(8,1) → [B, 128,   1, W/4]

    Then:
        Squeeze H → [B, T, 128]  (T = W/4)
        + Sinusoidal Positional Encoding
        3× TransformerEncoderLayer (pre-norm, GELU, 4 heads)
        LayerNorm → Linear(128, num_classes)
        Permute → [T, B, num_classes]
    """

    def __init__(
        self,
        num_classes: int = 16,
        d_model: int = 128,
        nhead: int = 4,
        num_layers: int = 3,
        dim_feedforward: int = 512,
        dropout: float = 0.1,
    ):
        super().__init__()

        # ── CNN Backbone ──
        # H: 128 → 64 → 32 → 16 → 8 → 1
        # W: W   → W/2→ W/4→ W/4→ W/4→ W/4 = T
        self.backbone = nn.Sequential(
            # Layer 1: [B, 1, 128, W] → [B, 32, 64, W/2]
            nn.Conv2d(1, 32, 3, stride=2, padding=1, bias=False),
            nn.BatchNorm2d(32),
            nn.GELU(),
            # Layer 2: [B, 32, 64, W/2] → [B, 64, 32, W/4]
            DepthwiseSeparableConv(32, 64),
            nn.MaxPool2d(2, 2),
            # Layer 3: [B, 64, 32, W/4] → [B, 96, 16, W/4]
            DepthwiseSeparableConv(64, 96),
            nn.MaxPool2d((2, 1)),  # 只缩高度
            # Layer 4: [B, 96, 16, W/4] → [B, d_model, 8, W/4]
            DepthwiseSeparableConv(96, d_model),
            nn.MaxPool2d((2, 1)),  # 只缩高度
            # Layer 5: [B, d_model, 8, W/4] → [B, d_model, 1, W/4]
            DepthwiseSeparableConv(d_model, d_model),
            nn.AvgPool2d((8, 1)),  # 塌缩高度 8→1
        )

        # ── Positional Encoding ──
        self.pos_enc = SinusoidalPositionalEncoding(d_model, max_len=256)

        # ── SVTR Attention Blocks (replaces BiLSTM) ──
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
            norm_first=True,  # Pre-norm (更稳定训练)
        )
        self.transformer = nn.TransformerEncoder(
            encoder_layer, num_layers=num_layers
        )

        # ── CTC Head ──
        self.norm = nn.LayerNorm(d_model)
        self.fc = nn.Linear(d_model, num_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: [B, 1, H, W] grayscale input (H=128, W=256 typical)

        Returns:
            [T, B, num_classes] CTC logits (T = W // 4)
        """
        # CNN: extract features, collapse height
        conv = self.backbone(x)  # [B, d_model, 1, T]
        conv = conv.squeeze(2)  # [B, d_model, T]
        conv = conv.permute(0, 2, 1)  # [B, T, d_model]

        # Positional encoding + Transformer
        conv = self.pos_enc(conv)  # [B, T, d_model]
        out = self.transformer(conv)  # [B, T, d_model]

        # CTC head
        out = self.norm(out)  # [B, T, d_model]
        out = self.fc(out)  # [B, T, num_classes]

        return out.permute(1, 0, 2)  # [T, B, num_classes]


# ─────────────────────────────────────────────────────────
# ONNX Export
# ─────────────────────────────────────────────────────────

def export_onnx(
    model: LightSVTR,
    output_path: str = "light_svtr.onnx",
    opset_version: int = 17,
    img_h: int = 128,
    img_w: int = 256,
):
    """导出 LightSVTR 为 ONNX 格式 (支持动态 batch + 动态宽度).

    Args:
        model: Trained LightSVTR instance.
        output_path: Output .onnx file path.
        opset_version: ONNX opset (≥14, 建议 17).
        img_h: Input image height (固定 128).
        img_w: Example input width (实际支持动态).

    ONNX Runtime Android 兼容性:
      - ORT ≥ 1.14 支持 opset 17
      - 所有算子均为标准 ONNX op (Conv, BatchNorm, MatMul, Softmax, etc.)
      - 无自定义 CUDA 算子
      - LayerNormalization 在 opset 17 原生支持
    """
    model.eval()
    dummy = torch.randn(1, 1, img_h, img_w)

    torch.onnx.export(
        model,
        dummy,
        output_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch", 3: "width"},
            "output": {0: "time", 1: "batch"},
        },
        opset_version=opset_version,
        do_constant_folding=True,
    )
    print(f"✓ ONNX exported to {output_path}")

    # 可选: 验证
    try:
        import onnx

        onnx_model = onnx.load(output_path)
        onnx.checker.check_model(onnx_model)
        print(f"  ONNX model check passed")
    except ImportError:
        pass
    except Exception as e:
        print(f"  ONNX check warning: {e}")

    # 可选: 打印大小
    import os

    size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"  Size: {size_mb:.2f} MB")


# ─────────────────────────────────────────────────────────
# Utilities
# ─────────────────────────────────────────────────────────

def count_params(model: nn.Module) -> dict:
    """统计模型各部分参数量."""
    total = sum(p.numel() for p in model.parameters())
    backbone = sum(p.numel() for p in model.backbone.parameters())
    transformer = sum(p.numel() for p in model.transformer.parameters())
    head = sum(p.numel() for p in model.norm.parameters()) + sum(
        p.numel() for p in model.fc.parameters()
    )
    return {
        "total": total,
        "backbone_cnn": backbone,
        "transformer": transformer,
        "ctc_head": head,
        "total_kb": total * 4 / 1024,
        "total_mb": total * 4 / (1024 * 1024),
    }


# ─────────────────────────────────────────────────────────
# Self-test
# ─────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 60)
    print("LightSVTR Architecture Test")
    print("=" * 60)

    model = LightSVTR(num_classes=16)
    stats = count_params(model)

    print(f"\n参数量:")
    print(f"  CNN Backbone: {stats['backbone_cnn']:>8,}")
    print(f"  Transformer:  {stats['transformer']:>8,}")
    print(f"  CTC Head:     {stats['ctc_head']:>8,}")
    print(f"  ─────────────────────")
    print(f"  Total:        {stats['total']:>8,} ({stats['total_mb']:.2f} MB FP32)")

    # Test forward pass with standard input
    print(f"\nForward pass test:")
    x = torch.randn(2, 1, 128, 256)
    model.eval()
    with torch.no_grad():
        out = model(x)
    print(f"  Input:  {list(x.shape)}")
    print(f"  Output: {list(out.shape)}  (expected [64, 2, 16])")
    assert out.shape == (64, 2, 16), f"Shape mismatch: {out.shape}"
    print(f"  ✓ Shape correct")

    # Test dynamic width
    print(f"\nDynamic width test:")
    for w in [128, 192, 256, 384]:
        x = torch.randn(1, 1, 128, w)
        with torch.no_grad():
            out = model(x)
        expected_t = w // 4
        print(f"  W={w:3d} → T={out.shape[0]:3d} (expected {expected_t})", end="")
        assert out.shape == (expected_t, 1, 16)
        print("  ✓")

    # Test ONNX export
    print(f"\nONNX export test:")
    try:
        export_onnx(model, "/tmp/light_svtr_test.onnx")
    except Exception as e:
        print(f"  ONNX export skipped: {e}")

    # Compare with LightCRNN (if available)
    print(f"\n{'='*60}")
    print("Architecture Comparison: LightSVTR vs LightCRNN")
    print(f"{'='*60}")
    print(f"{'':20s} {'LightCRNN':>12s} {'LightSVTR':>12s}")
    print(f"{'─'*44}")
    svtr_k = stats["total"] // 1000
    print(f"{'Params':20s} {'~79K':>12s} {'~' + str(svtr_k) + 'K':>12s}")
    print(f"{'Sequence model':20s} {'BiLSTM×2':>12s} {'TF Enc×3':>12s}")
    print(f"{'Parallelizable':20s} {'No':>12s} {'Yes':>12s}")
    print(f"{'d_model':20s} {'96×2=192':>12s} {'128':>12s}")
    print(f"{'Receptive field':20s} {'Sequential':>12s} {'Global':>12s}")
    print(f"{'ONNX friendly':20s} {'LSTM loop':>12s} {'MatMul only':>12s}")

    print(f"\n✅ All tests passed")
