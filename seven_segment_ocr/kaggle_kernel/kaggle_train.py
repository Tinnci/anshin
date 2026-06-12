"""
七段数码管 OCR 训练 v10 - Kaggle TPU/GPU 版
=============================================
v10 新增:
- 多行血压计 LCD 训练数据 (高压/低压/脉率分行)
- 输入高度 64→128 以容纳多行
- 字符集新增 '\\n' 用于行分隔 (16类)
- CNN 后两层 MaxPool 只缩高度不缩宽度 (T=64 时间步)
- 增大旋转/透视增强范围
- 默认 TPU 训练 (Kaggle TPU v3-8)

使用方法:
1. 在 Kaggle 创建一个新 Notebook
2. 开启 TPU 加速器 (Settings → Accelerator → TPU VM v3-8)
3. 粘贴此脚本到一个 Code Cell 中运行
4. 结果输出到 /kaggle/working/ 目录
5. 下载 crnn_seven_seg.onnx 到本地

或者通过 Kaggle API:
    kaggle kernels push -p kaggle_kernel/
"""

# %% [markdown]
# # 七段数码管 OCR - GPU 训练

# %%
import math
import os
import random
import re
import sys
import threading
import time
import traceback
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from PIL import Image

# ─────────────────────────────────────────────────────────
# TPU/GPU 设备检测 + 诊断工具
# ─────────────────────────────────────────────────────────

def _flush_print(*args, **kwargs):
    """强制刷新 print（Kaggle notebook 有时缓冲 stdout）。"""
    print(*args, **kwargs)
    sys.stdout.flush()

class WatchdogTimer:
    """看门狗计时器 — 如果操作超时则打印诊断信息并可选退出。

    用于检测 TPU 冻结：XLA 编译卡死、设备通信超时等。
    """
    def __init__(self, timeout_sec, label="operation", exit_on_timeout=False):
        self.timeout_sec = timeout_sec
        self.label = label
        self.exit_on_timeout = exit_on_timeout
        self._timer = None
        self._start_time = None

    def _on_timeout(self):
        elapsed = time.time() - self._start_time
        msg = (f"\n⚠️  WATCHDOG TIMEOUT: '{self.label}' 超时 "
               f"({elapsed:.0f}s > {self.timeout_sec}s 限制)")
        _flush_print(msg)
        _flush_print(f"    这通常意味着 TPU/XLA 编译卡死或设备通信失败。")
        _flush_print(f"    当前线程堆栈:")
        # 打印所有线程堆栈用于诊断
        for tid, frame in sys._current_frames().items():
            if tid == threading.main_thread().ident:
                _flush_print(f"    --- 主线程 (tid={tid}) ---")
                for line in traceback.format_stack(frame):
                    _flush_print(f"    {line.rstrip()}")
        if self.exit_on_timeout:
            _flush_print(f"\n❌ exit_on_timeout=True, 强制退出进程 (exit code 1)")
            os._exit(1)  # os._exit 以绕过 Python 清理（可能也卡住）

    def __enter__(self):
        self._start_time = time.time()
        self._timer = threading.Timer(self.timeout_sec, self._on_timeout)
        self._timer.daemon = True
        self._timer.start()
        return self

    def __exit__(self, *exc):
        if self._timer:
            self._timer.cancel()
        return False

    @property
    def elapsed(self):
        return time.time() - self._start_time if self._start_time else 0


# TPU 初始化超时: 如果 60s 内设备未就绪，打印诊断并退出
try:
    with WatchdogTimer(60, "TPU 设备初始化", exit_on_timeout=True):
        import torch_xla
        import torch_xla.core.xla_model as xm
        try:
            device = torch_xla.device()         # torch_xla >= 2.x
        except AttributeError:
            device = xm.xla_device()             # torch_xla 1.x
    _IS_TPU = True
    _flush_print(f"🚀 使用设备: TPU ({device})")
except ImportError:
    _IS_TPU = False
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    _flush_print(f"🚀 使用设备: {device}")
    if torch.cuda.is_available():
        print(f"   GPU: {torch.cuda.get_device_name()}")
        print(f"   显存: {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")

# ─────────────────────────────────────────────────────────
# 数据生成（完整的七段管渲染 + 增强管道）
# ─────────────────────────────────────────────────────────

from lcd_synthesis import (
    add_medical_label,
    augment_geometric,
    augment_image,
    augment_photometric,
    embed_with_margin,
    pick_lcd_theme,
    render_multiline_bp,
    render_number,
)

# ─────────────────────────────────────────────────────────
# 数据集生成 (在内存中)
# ─────────────────────────────────────────────────────────

CHARS = "0123456789/. -\n"
BLANK = 0
CHAR_TO_IDX = {ch: i + 1 for i, ch in enumerate(CHARS)}
IDX_TO_CHAR = {i + 1: ch for i, ch in enumerate(CHARS)}
NUM_CLASSES = len(CHARS) + 1  # 16 = 15 chars + blank

def encode_label(text):
    return [CHAR_TO_IDX[ch] for ch in text if ch in CHAR_TO_IDX]

def decode_prediction(indices):
    result = []
    prev = BLANK
    for idx in indices:
        if idx != BLANK and idx != prev:
            if idx in IDX_TO_CHAR:
                result.append(IDX_TO_CHAR[idx])
        prev = idx
    return "".join(result)


def postprocess_ctc(text):
    """CTC 解码后处理：修正常见错误模式。"""
    if not text:
        return text
    # 去除首尾空格
    text = text.strip()
    # 合并连续空格
    while "  " in text:
        text = text.replace("  ", " ")
    # 去除首尾多余的分隔符
    text = text.strip("/.-")
    # 去除连续重复的分隔符 (如 "//" -> "/", ".." -> ".")
    text = re.sub(r'([/.\-])\1+', r'\1', text)
    return text


class OnTheFlySeqDataset(Dataset):
    """混合策略数据集：预生成基础池 + 实时轻量增强，兼顾多样性与速度。

    - 每个 epoch 开始时生成 pool_size 张「基础图」（渲染 + 嵌入 + 标签，但不做增强）
    - __getitem__ 从池中随机取一张，实时施加增强（快速）
    - 这样每个 epoch 看到 virtual_size 张不同增强的图，且池子 epoch 间刷新
    - 支持课程学习：通过 set_epoch() 调整难度分布
    """

    POOL_SIZE = 30000  # 基础图池大小（含几何增强，30K 提升多样性）

    def __init__(self, virtual_size=100000, target_h=128, max_w=256, seed=None):
        self.virtual_size = virtual_size
        self.target_h = target_h
        self.max_w = max_w
        self._epoch = 0
        self._total_epochs = 60
        self._seed = seed
        self._pool = []  # [(PIL.Image, text), ...]
        self._cache = None
        if seed is not None:
            self._pregenerate(seed)

    def set_epoch(self, epoch, total_epochs=60):
        """设置当前 epoch，刷新基础图池 + 调整难度。"""
        self._epoch = epoch
        self._total_epochs = total_epochs
        # 每个 epoch 重建基础图池（10K 张新渲染）
        self._rebuild_pool()

    def _pregenerate(self, seed):
        """预生成固定验证数据（保证每次 epoch 评估一致）。"""
        old_rng = random.getstate()
        old_np_rng = np.random.get_state()
        random.seed(seed)
        np.random.seed(seed)
        self._cache = []
        for i in range(self.virtual_size):
            self._cache.append(self._generate_one(difficulty="normal"))
        random.setstate(old_rng)
        np.random.set_state(old_np_rng)

    def _get_difficulty(self):
        """课程学习难度调度：前期偏 easy，后期偏 hard。"""
        progress = self._epoch / max(1, self._total_epochs - 1)
        if progress < 0.15:
            # 前 15%: 60% easy, 30% normal, 10% hard
            r = random.random()
            return "easy" if r < 0.6 else ("normal" if r < 0.9 else "hard")
        elif progress < 0.4:
            # 15-40%: 20% easy, 50% normal, 30% hard
            r = random.random()
            return "easy" if r < 0.2 else ("normal" if r < 0.7 else "hard")
        else:
            # 40-100%: 5% easy, 25% normal, 70% hard
            r = random.random()
            return "easy" if r < 0.05 else ("normal" if r < 0.30 else "hard")

    def __len__(self):
        return self.virtual_size

    def __getitem__(self, idx):
        if self._cache is not None:
            return self._cache[idx]
        if not self._pool:
            self._rebuild_pool()
        # 从池中取基础图（已含几何增强），在线施加轻量光度增强
        pool_idx = idx % len(self._pool)
        img, text = self._pool[pool_idx]
        difficulty = self._get_difficulty()
        img = augment_photometric(img.copy(), difficulty)
        return self._to_tensor(img, text)

    def _rebuild_pool(self):
        """重建基础图池：渲染 + 几何增强。"""
        t0 = time.time()
        self._pool = []
        for _ in range(self.POOL_SIZE):
            img, text = self._render_base()
            # 在 pool 阶段施加重量级几何增强
            difficulty = self._get_difficulty()
            img = augment_geometric(img, difficulty)
            self._pool.append((img, text))
        print(f"  [Pool] 生成 {self.POOL_SIZE} 张基础图+几何增强, 耗时 {time.time()-t0:.1f}s")

    def _render_base(self):
        """渲染一张基础图（含标签/嵌入，不含增强）。"""
        # 25% 概率生成多行 BP 图像
        if random.random() < 0.25:
            theme = pick_lcd_theme()
            img, text = render_multiline_bp(theme)
            if random.random() < 0.35:
                img = add_medical_label(img, category="bp")
            if random.random() < 0.25:
                img = embed_with_margin(img, random.uniform(1.3, 2.0))
            return img, text

        generators = [
            (lambda: f"{random.randint(80,200)}{random.choice(['/',' '])}{random.randint(40,130)}", 0.25, "bp"),
            (lambda: str(random.randint(40, 200)), 0.15, "hr"),
            (lambda: str(round(random.uniform(35.0, 42.0), 1)), 0.10, "temp"),
            (lambda: str(round(random.uniform(2.0, 30.0), 1)), 0.10, "spo2"),
            (lambda: str(random.randint(85, 100)), 0.10, "spo2"),
            (lambda: str(round(random.uniform(30.0, 150.0), 1)), 0.05, "weight"),
            (lambda: "".join([str(random.randint(0,9)) for _ in range(random.randint(1,6))]), 0.25, "generic"),
        ]
        r = random.random()
        cumul = 0.0
        gen_func, label_cat = generators[-1][0], "generic"
        for func, w, cat in generators:
            cumul += w
            if r < cumul:
                gen_func, label_cat = func, cat
                break

        text = gen_func()
        theme = pick_lcd_theme()
        dw = random.randint(18, 55)
        dh = random.randint(35, 95)
        sp = random.choice([
            random.randint(0, 2), random.randint(3, 10), random.randint(3, 10),
            random.randint(11, 22), random.randint(3, 14),
        ])

        img = render_number(
            text, digit_width=dw, digit_height=dh,
            thickness=random.randint(2, max(3, dw // 4)),
            theme=theme, gap=random.randint(0, 4),
            spacing=sp, padding=random.randint(3, 22),
            skew=random.uniform(-0.18, 0.18),
            show_dim=random.random() < 0.5,
            use_textured_bg=random.random() < 0.4,
        )

        if random.random() < 0.35:
            img = add_medical_label(img, category=label_cat)
        if random.random() < 0.30:
            img = embed_with_margin(img, random.uniform(1.3, 2.5))

        return img, text

    def _generate_one(self, difficulty="normal"):
        """完整生成一张图（渲染+增强），用于验证集预生成。"""
        img, text = self._render_base()
        img = augment_image(img, difficulty)
        return self._to_tensor(img, text)

    def _to_tensor(self, img, text):
        """PIL Image → tensor + label encoding。"""
        gray = img.convert("L")
        ratio = self.target_h / gray.height
        new_w = min(int(gray.width * ratio), self.max_w)
        gray = gray.resize((new_w, self.target_h), Image.LANCZOS)
        padded = Image.new("L", (self.max_w, self.target_h), 0)
        padded.paste(gray, (0, 0))
        tensor = torch.from_numpy(np.array(padded, dtype=np.float32) / 255.0).unsqueeze(0)
        encoded = encode_label(text)
        return tensor, torch.tensor(encoded, dtype=torch.long), len(encoded), new_w


# ─────────────────────────────────────────────────────────
# 模型
# ─────────────────────────────────────────────────────────

class DepthwiseSeparableConv(nn.Module):
    def __init__(self, in_ch, out_ch, kernel=3, stride=1, padding=1):
        super().__init__()
        self.depthwise = nn.Conv2d(in_ch, in_ch, kernel, stride, padding, groups=in_ch, bias=False)
        self.pointwise = nn.Conv2d(in_ch, out_ch, 1, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)

    def forward(self, x):
        return F.relu(self.bn(self.pointwise(self.depthwise(x))))


class LightCRNN(nn.Module):
    def __init__(self, num_classes=NUM_CLASSES, rnn_hidden=96):
        super().__init__()
        # v10: H=128, 前两层 pool(2,2) 同时缩 H/W，后两层 pool(2,1) 只缩 H
        # 使宽度方向保留更多时间步 (T=64)
        # H: 128→64→32→16→8→1  W: 256→128→64→64→64→64 → T=64
        self.cnn = nn.Sequential(
            nn.Conv2d(1, 24, 3, 1, 1, bias=False), nn.BatchNorm2d(24), nn.ReLU(), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(24, 48), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(48, 64), nn.MaxPool2d((2, 1)),  # 只缩高度
            DepthwiseSeparableConv(64, 96), nn.MaxPool2d((2, 1)),  # 只缩高度
            DepthwiseSeparableConv(96, 96), nn.AvgPool2d((8, 1)),  # H=8→1
        )
        self.rnn = nn.LSTM(input_size=96, hidden_size=rnn_hidden, num_layers=2, bidirectional=True, batch_first=False, dropout=0.3)
        self.drop = nn.Dropout(0.2)
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        conv = self.cnn(x).squeeze(2).permute(2, 0, 1)
        rnn_out, _ = self.rnn(conv)
        return self.fc(self.drop(rnn_out))


class SinusoidalPositionalEncoding(nn.Module):
    """正弦位置编码 — 支持任意序列长度，ONNX 友好。"""
    def __init__(self, d_model, max_len=256):
        super().__init__()
        pe = torch.zeros(1, max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(torch.arange(0, d_model, 2).float() * (-math.log(10000.0) / d_model))
        pe[0, :, 0::2] = torch.sin(position * div_term)
        pe[0, :, 1::2] = torch.cos(position * div_term)
        self.register_buffer('pe', pe)

    def forward(self, x):
        return x + self.pe[:, :x.size(1)]


class LightSVTR(nn.Module):
    """Hybrid CNN backbone + SVTR attention for CTC (replaces BiLSTM with Transformer).

    PP-OCRv4 SVTR_LCNet 思想: 轻量 CNN 提特征 + Transformer 序列建模。
    CNN: H=128→1, W=256→64 (T=64 时间步)
    SVTR: 3× TransformerEncoderLayer (d=128, 4 heads, FFN=512)
    ~638K 参数 (vs LightCRNN ~79K)，但注意力可并行、全局感受野。
    """
    def __init__(self, num_classes=NUM_CLASSES, d_model=128, nhead=4,
                 num_layers=3, dim_feedforward=512, dropout=0.1):
        super().__init__()
        # CNN Backbone: H=128→1, W→T=W/4
        self.backbone = nn.Sequential(
            nn.Conv2d(1, 32, 3, stride=2, padding=1, bias=False), nn.BatchNorm2d(32), nn.GELU(),
            DepthwiseSeparableConv(32, 64), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(64, 96), nn.MaxPool2d((2, 1)),
            DepthwiseSeparableConv(96, d_model), nn.MaxPool2d((2, 1)),
            DepthwiseSeparableConv(d_model, d_model), nn.AvgPool2d((8, 1)),
        )
        self.pos_enc = SinusoidalPositionalEncoding(d_model, max_len=256)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model, nhead=nhead, dim_feedforward=dim_feedforward,
            dropout=dropout, activation='gelu', batch_first=True, norm_first=True,
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.norm = nn.LayerNorm(d_model)
        self.fc = nn.Linear(d_model, num_classes)

    def forward(self, x):
        conv = self.backbone(x).squeeze(2).permute(0, 2, 1)  # [B, T, d_model]
        conv = self.pos_enc(conv)
        out = self.transformer(conv)
        out = self.fc(self.norm(out))
        return out.permute(1, 0, 2)  # [T, B, num_classes]


# ─────────────────────────────────────────────────────────
# 训练
# ─────────────────────────────────────────────────────────

def ctc_collate(batch):
    images, labels, label_lengths, img_widths = zip(*batch)
    return torch.stack(images, 0), torch.cat(labels, 0), torch.tensor(label_lengths, dtype=torch.long), None

# %%
# 配置
MODEL_TYPE = "svtr"  # "crnn" 或 "svtr" — 选择模型架构
NUM_TRAIN = 100000   # 每 epoch 虚拟大小（实时生成，每次看到的都不同）
NUM_VAL = 5000       # 验证集（固定种子，保证可比性）
EPOCHS = 60          # v10: 多行+H=128 需要更多训练
BATCH_SIZE = 128 if _IS_TPU else 64  # TPU 内存充足，加大 batch
LR = 0.0008 if _IS_TPU else 0.0005   # 大 batch 适当增大 LR
OUTPUT_DIR = Path("/kaggle/working") if os.path.exists("/kaggle") else Path("kaggle_output")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

print("=" * 60)
model_label = "LightSVTR" if MODEL_TYPE == "svtr" else "LightCRNN"
print(f"七段数码管 {model_label} 训练 (GPU/TPU) — On-the-fly 生成")
print("=" * 60)

# 数据集
train_dataset = OnTheFlySeqDataset(virtual_size=NUM_TRAIN)
val_dataset = OnTheFlySeqDataset(virtual_size=NUM_VAL, seed=999)  # 验证集固定种子

_pin = not _IS_TPU
_nw = 4 if not _IS_TPU else 0  # Kaggle T4 有 4 vCPU，全部用于增强
train_loader = DataLoader(
    train_dataset, batch_size=BATCH_SIZE, shuffle=False, collate_fn=ctc_collate,
    num_workers=_nw, pin_memory=_pin, prefetch_factor=4 if _nw > 0 else None,
    persistent_workers=False,  # 每 epoch 重建池后 worker 需要 re-fork 获取新 pool
)
val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, collate_fn=ctc_collate, num_workers=0, pin_memory=_pin)

if MODEL_TYPE == "svtr":
    model = LightSVTR().to(device)
else:
    model = LightCRNN().to(device)
optimizer = torch.optim.Adam(model.parameters(), lr=LR, weight_decay=1e-4)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=1e-6)
ctc_loss = nn.CTCLoss(blank=BLANK, zero_infinity=True)

param_count = sum(p.numel() for p in model.parameters())
print(f"模型: {model_label} ({param_count:,} 参数, {param_count * 4 / 1024:.0f} KB FP32)")
print(f"输入尺寸: H=128, W=256 (v10 multi-line)")
print(f"字符集: {repr(CHARS)} ({NUM_CLASSES} 类含 blank)")
print(f"训练集: {len(train_dataset)}/epoch (池 {OnTheFlySeqDataset.POOL_SIZE} 基础图+几何增强 × 在线光度增强), 验证集: {len(val_dataset)}")
print(f"Epochs: {EPOCHS}, Batch: {BATCH_SIZE}, LR: {LR}")
print(f"策略: 25% 多行BP + 75% 单行 | 池中 渲染+几何增强(重), __getitem__ 仅光度增强(轻)")
print(f"课程学习: epoch 0-15% easy为主 → 15-40% normal为主 → 40%+ hard为主")
print()

best_val_loss = float("inf")
best_acc = 0.0
epoch_history = []

# TPU 批次超时配置
_BATCH_TIMEOUT = 600 if _IS_TPU else 0   # TPU 首批编译可能要 5-10 分钟
_BATCH_TIMEOUT_AFTER_WARMUP = 120        # 预热后每批不应超过 2 分钟
_batch_warmed_up = False                  # 首批编译完成后设为 True

for epoch in range(EPOCHS):
    # 课程学习：更新难度分布
    train_dataset.set_epoch(epoch, EPOCHS)
    # 训练
    model.train()
    total_loss = 0.0
    num_batches = 0
    t0 = time.time()

    for images, labels, label_lengths, _ in train_loader:
        # TPU 看门狗: 检测 XLA 编译卡死 / 设备通信超时
        batch_timeout = _BATCH_TIMEOUT if not _batch_warmed_up else _BATCH_TIMEOUT_AFTER_WARMUP
        try:
            if _IS_TPU and batch_timeout > 0:
                with WatchdogTimer(batch_timeout,
                        f"epoch {epoch+1} batch {num_batches+1}"
                        + (" (XLA首次编译)" if not _batch_warmed_up else ""),
                        exit_on_timeout=True):
                    images = images.to(device)
                    labels = labels.to(device)
                    output = model(images)
                    T, B = output.size(0), images.size(0)
                    log_probs = F.log_softmax(output, dim=2)
                    loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
                    optimizer.zero_grad()
                    loss.backward()
                    torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
                    xm.optimizer_step(optimizer)
                    xm.mark_step()
                    total_loss += loss.item()
                if not _batch_warmed_up:
                    _batch_warmed_up = True
                    _flush_print(f"  ✓ TPU XLA 首批编译完成 ({time.time() - t0:.1f}s)")
            else:
                images = images.to(device)
                labels = labels.to(device)
                output = model(images)
                T, B = output.size(0), images.size(0)
                log_probs = F.log_softmax(output, dim=2)
                loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
                optimizer.zero_grad()
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
                optimizer.step()
                total_loss += loss.item()
        except Exception as e:
            _flush_print(f"\n❌ 训练批次异常 (epoch {epoch+1}, batch {num_batches+1}): {type(e).__name__}: {e}")
            _flush_print(traceback.format_exc())
            if _IS_TPU:
                _flush_print("TPU 错误通常不可恢复，终止训练。")
                # 保存当前模型（如果有改善过的话）
                try:
                    model.cpu()
                    torch.save(model.state_dict(), OUTPUT_DIR / ("svtr_best.pth" if MODEL_TYPE == "svtr" else "crnn_best.pth"))
                    _flush_print(f"  💾 已保存当前模型到 {OUTPUT_DIR}")
                except Exception:
                    pass
                os._exit(1)
            raise  # GPU/CPU 上重新抛出
        num_batches += 1

    avg_train_loss = total_loss / max(num_batches, 1)
    train_time = time.time() - t0
    scheduler.step()

    # 验证
    model.eval()
    val_loss_total = 0.0
    val_batches = 0
    correct = 0
    correct_post = 0
    total = 0
    sample_preds = []  # 收集样本用于日志
    error_examples = []  # 收集错误样本

    with torch.no_grad():
        for images, labels, label_lengths, _ in val_loader:
            try:
                if _IS_TPU:
                    with WatchdogTimer(_BATCH_TIMEOUT_AFTER_WARMUP,
                            f"val epoch {epoch+1} batch {val_batches+1}",
                            exit_on_timeout=True):
                        images = images.to(device)
                        labels = labels.to(device)
                        output = model(images)
                        T, B = output.size(0), images.size(0)
                        log_probs = F.log_softmax(output, dim=2)
                        loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
                        val_loss_total += loss.item()
                else:
                    images = images.to(device)
                    labels = labels.to(device)
                    output = model(images)
                    T, B = output.size(0), images.size(0)
                    log_probs = F.log_softmax(output, dim=2)
                    loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
                    val_loss_total += loss.item()
            except Exception as e:
                _flush_print(f"\n❌ 验证异常 (epoch {epoch+1}, batch {val_batches+1}): {type(e).__name__}: {e}")
                _flush_print(traceback.format_exc())
                if _IS_TPU:
                    _flush_print("TPU 验证异常，终止训练。")
                    os._exit(1)
                raise
            val_batches += 1

            preds = output.argmax(dim=2)
            offset = 0
            for b in range(B):
                raw_text = decode_prediction(preds[:, b].cpu().tolist())
                post_text = postprocess_ctc(raw_text)
                llen = label_lengths[b].item()
                true_text = "".join(IDX_TO_CHAR.get(i, "?") for i in labels[offset:offset+llen].cpu().tolist())
                if raw_text == true_text:
                    correct += 1
                if post_text == true_text:
                    correct_post += 1
                elif len(error_examples) < 15:
                    error_examples.append((true_text, raw_text, post_text))
                if len(sample_preds) < 8:
                    sample_preds.append((true_text, raw_text, post_text))
                total += 1
                offset += llen

    avg_val_loss = val_loss_total / max(val_batches, 1)
    accuracy = correct / max(total, 1)
    accuracy_post = correct_post / max(total, 1)
    val_time = time.time() - t0 - train_time
    lr_now = optimizer.param_groups[0]["lr"]

    epoch_history.append({
        "epoch": epoch + 1,
        "train_loss": avg_train_loss,
        "val_loss": avg_val_loss,
        "acc_raw": accuracy,
        "acc_post": accuracy_post,
        "lr": lr_now,
    })

    # ── 每 epoch 都输出基本信息 ──
    improved_marker = ""
    if avg_val_loss < best_val_loss:
        improved_marker = " ★ best_loss"
        best_val_loss = avg_val_loss
        best_pth_name = "svtr_best.pth" if MODEL_TYPE == "svtr" else "crnn_best.pth"
        torch.save(model.state_dict(), OUTPUT_DIR / best_pth_name)
    if accuracy_post > best_acc:
        improved_marker += " ★ best_acc"
        best_acc = accuracy_post

    _flush_print(f"  [{epoch+1:3d}/{EPOCHS}] "
          f"loss={avg_train_loss:.4f}/{avg_val_loss:.4f} "
          f"acc={accuracy:.1%}→{accuracy_post:.1%} "
          f"lr={lr_now:.2e} "
          f"t={train_time:.1f}+{val_time:.1f}s"
          f"{improved_marker}")

    # ── 每 5 个 epoch 输出详细日志 ──
    if (epoch + 1) % 5 == 0 or epoch == 0 or (epoch + 1) == EPOCHS:
        print(f"\n  {'─' * 50}")
        print(f"  📊 Epoch {epoch+1} 详细报告")
        print(f"  {'─' * 50}")

        # 样本预测
        print(f"  🔍 样本预测 (最多8个):")
        for gt, raw, post in sample_preds:
            status = "✓" if post == gt else "✗"
            if raw != post:
                print(f"    {status} GT='{gt}' → Raw='{raw}' → Post='{post}'")
            else:
                print(f"    {status} GT='{gt}' → Pred='{raw}'")

        # 错误样本
        if error_examples:
            print(f"  ❌ 错误样本 (最多15个):")
            for gt, raw, post in error_examples[:10]:
                if raw != post:
                    print(f"    GT='{gt}' → Raw='{raw}' → Post='{post}'")
                else:
                    print(f"    GT='{gt}' → Pred='{raw}'")

        # 后处理提升统计
        delta = accuracy_post - accuracy
        if delta > 0:
            print(f"  📈 后处理提升: {accuracy:.1%} → {accuracy_post:.1%} (+{delta:.1%})")
        else:
            print(f"  📈 后处理: {accuracy:.1%} → {accuracy_post:.1%} (无提升)")

        # 训练历史趋势
        if len(epoch_history) >= 5:
            recent = epoch_history[-5:]
            loss_trend = recent[-1]["val_loss"] - recent[0]["val_loss"]
            acc_trend = recent[-1]["acc_post"] - recent[0]["acc_post"]
            print(f"  📉 最近5epoch趋势: val_loss {loss_trend:+.4f}, acc {acc_trend:+.1%}")

        print(f"  {'─' * 50}\n")

print(f"\n{'=' * 60}")
print(f"✅ 训练完成!")
print(f"  最佳验证 Loss: {best_val_loss:.4f}")
print(f"  最佳准确率 (含后处理): {best_acc:.1%}")
print(f"{'=' * 60}")

# %%
# ONNX 导出
print("\n📦 导出 ONNX...")
best_pth_name = "svtr_best.pth" if MODEL_TYPE == "svtr" else "crnn_best.pth"
model.load_state_dict(torch.load(OUTPUT_DIR / best_pth_name, map_location="cpu"))
model.eval().cpu()

dummy = torch.randn(1, 1, 128, 256)  # H=128 for multi-line support
onnx_filename = "svtr_seven_seg.onnx" if MODEL_TYPE == "svtr" else "crnn_seven_seg.onnx"
onnx_path = OUTPUT_DIR / onnx_filename

torch.onnx.export(
    model, dummy, str(onnx_path),
    input_names=["input"], output_names=["output"],
    dynamic_axes={"input": {3: "width"}, "output": {0: "time_steps"}},
    opset_version=17, dynamo=False,
)

size_kb = os.path.getsize(onnx_path) / 1024
print(f"  ONNX 导出完成: {onnx_path}")
print(f"  大小: {size_kb:.0f} KB")

# 验证
try:
    import onnxruntime as ort
    sess = ort.InferenceSession(str(onnx_path))
    out = sess.run(None, {"input": dummy.numpy()})
    print(f"  ONNX 验证成功, 输出形状: {out[0].shape}")
except ImportError:
    print("  (跳过 ONNX Runtime 验证, 未安装)")

print(f"\n🎉 完成! 模型在: {onnx_path}")
if MODEL_TYPE == "svtr":
    print(f"   下载此文件并复制到 app/src/main/assets/svtr_seven_seg.onnx")
else:
    print(f"   下载此文件并复制到 app/src/main/assets/crnn_seven_seg.onnx")
