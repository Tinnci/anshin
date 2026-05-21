"""FastViT-T8 backbone with a CTC OCR head for seven-segment recognition."""

from __future__ import annotations

import json
from pathlib import Path

import torch
import torch.nn.functional as F
import torch.nn as nn


IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


class FastViTCTC(nn.Module):
    """Wrap a FastViT feature extractor with a lightweight CTC head.

    The model returns logits in the same layout as LightSVTR: [T, B, C].
    """

    def __init__(
        self,
        *,
        num_classes: int,
        backbone: nn.Module,
        feature_channels: int,
        neck_channels: int = 128,
        feature_indices: tuple[int, ...] | None = None,
        dropout: float = 0.1,
    ) -> None:
        super().__init__()
        self.backbone = backbone
        self.feature_indices = feature_indices
        self.neck = nn.Sequential(
            nn.Conv2d(feature_channels, neck_channels, kernel_size=1, bias=False),
            nn.BatchNorm2d(neck_channels),
            nn.GELU(),
        )
        self.norm = nn.LayerNorm(neck_channels)
        self.dropout = nn.Dropout(dropout)
        self.ctc_head = nn.Linear(neck_channels, num_classes)

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        features = self.backbone(images)
        if isinstance(features, (list, tuple)):
            selected = [features[index] for index in self.feature_indices] if self.feature_indices else list(features)
            target_size = selected[0].shape[-2:]
            aligned = [
                F.interpolate(feature, size=target_size, mode="bilinear", align_corners=False)
                for feature in selected
            ]
            feature = torch.cat(aligned, dim=1)
        else:
            feature = features
        feature = self.neck(feature)
        sequence = feature.mean(dim=2).permute(0, 2, 1)
        sequence = self.dropout(self.norm(sequence))
        logits = self.ctc_head(sequence)
        return logits.permute(1, 0, 2)


def build_fastvit_t8_ctc(
    *,
    num_classes: int,
    pretrained: bool = True,
    neck_channels: int = 128,
    feature_indices: tuple[int, ...] | None = None,
) -> FastViTCTC:
    import timm

    backbone = timm.create_model(
        "fastvit_t8.apple_in1k",
        pretrained=pretrained,
        features_only=True,
    )
    all_channels = backbone.feature_info.channels()
    selected_indices = feature_indices or tuple(range(len(all_channels)))
    channels = sum(all_channels[index] for index in selected_indices)
    return FastViTCTC(
        num_classes=num_classes,
        backbone=backbone,
        feature_channels=channels,
        neck_channels=neck_channels,
        feature_indices=selected_indices,
    )


def set_backbone_trainable(model: FastViTCTC, trainable: bool) -> None:
    for param in model.backbone.parameters():
        param.requires_grad = trainable


def export_fastvit_onnx(
    model: FastViTCTC,
    *,
    onnx_path: str | Path,
    metadata_path: str | Path,
    img_h: int = 128,
    img_w: int = 256,
    opset_version: int = 17,
) -> dict[str, object]:
    model.eval()
    output = Path(onnx_path)
    metadata = Path(metadata_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    metadata.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.randn(1, 3, img_h, img_w, dtype=torch.float32)
    torch.onnx.export(
        model,
        dummy,
        str(output),
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch", 3: "width"},
            "output": {0: "time", 1: "batch"},
        },
        opset_version=opset_version,
        do_constant_folding=True,
        dynamo=False,
    )
    payload = {
        "adapter": "torch_ctc_onnx",
        "backbone": "fastvit_t8.apple_in1k",
        "image_shape": [3, img_h, img_w],
        "mean": IMAGENET_MEAN,
        "std": IMAGENET_STD,
        "reparameterized": False,
    }
    metadata.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return {
        "onnx_path": str(output),
        "metadata_path": str(metadata),
        "model_bytes": output.stat().st_size if output.exists() else None,
    }
