import json
import tempfile
import unittest
import unittest.mock
from collections import OrderedDict
from pathlib import Path

import numpy as np
from PIL import Image

from evaluate_parseq_onnx import decode_parseq_logits, evaluate_parseq_onnx, preprocess_parseq_image
from export_parseq_onnx import load_parseq_for_export


class ParseqIntegrationTest(unittest.TestCase):
    def test_preprocess_parseq_image_returns_imagenet_normalized_nchw(self):
        with tempfile.TemporaryDirectory() as tmp:
            image_path = Path(tmp) / "sample.png"
            Image.new("RGB", (8, 4), (255, 128, 0)).save(image_path)

            tensor = preprocess_parseq_image(image_path)

        self.assertEqual(tensor.shape, (1, 3, 32, 128))
        self.assertAlmostEqual(float(tensor[0, 0, 0, 0]), (1.0 - 0.485) / 0.229, places=5)
        self.assertAlmostEqual(float(tensor[0, 1, 0, 0]), ((128 / 255) - 0.456) / 0.224, places=5)
        self.assertAlmostEqual(float(tensor[0, 2, 0, 0]), (0.0 - 0.406) / 0.225, places=5)

    def test_decode_parseq_logits_stops_at_eos(self):
        logits = np.zeros((1, 5, 4), dtype=np.float32)
        for step, index in enumerate([1, 2, 0, 3, 2]):
            logits[0, step, index] = 10.0

        text = decode_parseq_logits(logits, ["[E]", "1", "2", "3"])

        self.assertEqual(text, "12")

    def test_load_parseq_for_export_loads_raw_state_into_inner_model(self):
        class InnerModel:
            def __init__(self):
                self.decode_ar = True
                self.refine_iters = 1
                self.loaded = None

            def load_state_dict(self, state_dict):
                self.loaded = state_dict

        class HubModel:
            def __init__(self):
                self.model = InnerModel()

            def eval(self):
                return self

        checkpoint = OrderedDict([("pos_queries", object())])
        hub_model = HubModel()

        loaded = load_parseq_for_export(
            Path("checkpoint.pt"),
            hub_loader=lambda *args, **kwargs: hub_model,
            checkpoint_loader=lambda *_args, **_kwargs: checkpoint,
        )

        self.assertIs(loaded, hub_model)
        self.assertIs(hub_model.model.loaded, checkpoint)
        self.assertFalse(hub_model.model.decode_ar)
        self.assertEqual(hub_model.model.refine_iters, 0)

    def test_evaluate_parseq_onnx_writes_importable_prediction_json(self):
        class Input:
            name = "input"
            shape = ["batch", 3, 32, 128]

        class FakeSession:
            def __init__(self, *_args, **_kwargs):
                pass

            def get_inputs(self):
                return [Input()]

            def run(self, _output_names, feeds):
                batch = feeds["input"].shape[0]
                logits = np.zeros((batch, 4, 3), dtype=np.float32)
                logits[:, 0, 1] = 10.0
                logits[:, 1, 2] = 10.0
                logits[:, 2, 0] = 10.0
                return [logits]

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dataset = root / "dataset"
            dataset.mkdir()
            (dataset / "images").mkdir()
            Image.new("RGB", (16, 8), (255, 255, 255)).save(dataset / "images" / "a.png")
            (dataset / "sequences.csv").write_text("filename,label\na.png,12\n", encoding="utf-8")
            onnx_path = root / "parseq.onnx"
            onnx_path.write_bytes(b"fake")
            metadata_path = root / "parseq_metadata.json"
            metadata_path.write_text(json.dumps({"tokens": ["[E]", "1", "2"]}), encoding="utf-8")
            output = root / "parseq_predictions.json"

            with unittest.mock.patch(
                "evaluate_parseq_onnx.ort.InferenceSession",
                FakeSession,
            ), unittest.mock.patch(
                "evaluate_parseq_onnx.measure_model_file",
                return_value={"format": "onnx", "model_bytes": 4, "parameter_count": 1},
            ):
                payload = evaluate_parseq_onnx(
                    model_id="parseq",
                    onnx_path=onnx_path,
                    dataset=dataset,
                    output=output,
                    metadata_path=metadata_path,
                )
            output_exists = output.exists()

        self.assertEqual(payload["model_id"], "parseq")
        self.assertEqual(payload["backend"], "onnxruntime_parseq")
        self.assertEqual(payload["predictions"][0]["text"], "12")
        self.assertIn("throughput", payload)
        self.assertTrue(output_exists)


if __name__ == "__main__":
    unittest.main()
