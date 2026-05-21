import tempfile
import unittest
import unittest.mock
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from fastvit_ctc import FastViTCTC, export_fastvit_onnx, set_backbone_trainable
from train_fastvit_ctc import preprocess_fastvit_image


class TinyBackbone(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.conv = torch.nn.Conv2d(3, 8, kernel_size=1)

    def forward(self, x):
        return [self.conv(x)]


class FastVitCtcTest(unittest.TestCase):
    def test_fastvit_ctc_outputs_time_batch_classes(self):
        model = FastViTCTC(
            num_classes=16,
            backbone=TinyBackbone(),
            feature_channels=8,
            neck_channels=12,
        )

        output = model(torch.randn(2, 3, 32, 64))

        self.assertEqual(output.shape, (64, 2, 16))

    def test_set_backbone_trainable_only_changes_backbone(self):
        model = FastViTCTC(
            num_classes=16,
            backbone=TinyBackbone(),
            feature_channels=8,
            neck_channels=12,
        )

        set_backbone_trainable(model, False)

        self.assertFalse(any(param.requires_grad for param in model.backbone.parameters()))
        self.assertTrue(any(param.requires_grad for param in model.ctc_head.parameters()))

        set_backbone_trainable(model, True)

        self.assertTrue(all(param.requires_grad for param in model.backbone.parameters()))

    def test_preprocess_fastvit_image_uses_rgb_imagenet_normalization(self):
        with tempfile.TemporaryDirectory() as tmp:
            image_path = Path(tmp) / "sample.png"
            Image.new("RGB", (8, 4), (255, 128, 0)).save(image_path)

            tensor = preprocess_fastvit_image(image_path)

        self.assertEqual(tensor.shape, (3, 128, 256))
        self.assertAlmostEqual(float(tensor[0, 0, 0]), (1.0 - 0.485) / 0.229, places=5)
        self.assertAlmostEqual(float(tensor[1, 0, 0]), ((128 / 255) - 0.456) / 0.224, places=5)
        self.assertAlmostEqual(float(tensor[2, 0, 0]), (0.0 - 0.406) / 0.225, places=5)

    def test_export_fastvit_onnx_writes_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            onnx_path = root / "fastvit.onnx"
            metadata_path = root / "fastvit.json"
            model = FastViTCTC(
                num_classes=16,
                backbone=TinyBackbone(),
                feature_channels=8,
                neck_channels=12,
            )

            with unittest.mock.patch("torch.onnx.export") as mock_export:
                export_fastvit_onnx(
                    model,
                    onnx_path=onnx_path,
                    metadata_path=metadata_path,
                    img_h=128,
                    img_w=256,
                )
            metadata_text = metadata_path.read_text(encoding="utf-8")

        mock_export.assert_called_once()
        self.assertIn('"adapter": "torch_ctc_onnx"', metadata_text)
        self.assertIn('"image_shape"', metadata_text)


if __name__ == "__main__":
    unittest.main()
