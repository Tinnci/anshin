import json
import tempfile
import unittest
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper
from PIL import Image

from ocr_model_eval import (
    evaluate_imported_predictions,
    format_results_table,
    load_labeled_dataset,
    main,
    measure_model_file,
    normalize_text,
    summarize_text_metrics,
)


class OcrModelEvalTest(unittest.TestCase):
    def test_load_labeled_dataset_reads_sequences_csv_images_dir(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "images").mkdir()
            Image.new("L", (16, 8), 0).save(root / "images" / "seq_000001.png")
            (root / "sequences.csv").write_text(
                "filename,label\nseq_000001.png,138/88\n",
                encoding="utf-8",
            )

            samples = load_labeled_dataset(root)

        self.assertEqual(len(samples), 1)
        self.assertEqual(samples[0].image_path.name, "seq_000001.png")
        self.assertEqual(samples[0].label, "138/88")

    def test_text_metrics_report_exact_cer_and_digit_accuracy(self):
        samples = [
            ("120/80", "120/80"),
            ("97.2", "97.3"),
            ("abc", ""),
        ]

        metrics = summarize_text_metrics(samples)

        self.assertAlmostEqual(metrics["exact"], 1 / 3)
        self.assertAlmostEqual(metrics["normalized_exact"], 1 / 3)
        self.assertGreater(metrics["cer"], 0)
        self.assertLess(metrics["digit_accuracy"], 1)
        self.assertEqual(normalize_text("  120  80\n"), "120 80")

    def test_measure_model_file_counts_onnx_initializers(self):
        with tempfile.TemporaryDirectory() as tmp:
            model_path = Path(tmp) / "tiny.onnx"
            weight = helper.make_tensor(
                "weight",
                TensorProto.FLOAT,
                dims=[2, 3],
                vals=np.ones((2, 3), dtype=np.float32).flatten(),
            )
            node = helper.make_node("Identity", inputs=["input"], outputs=["output"])
            graph = helper.make_graph(
                [node],
                "tiny",
                [helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 2])],
                [helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 2])],
                initializer=[weight],
            )
            onnx.save(helper.make_model(graph), model_path)

            info = measure_model_file(model_path)

        self.assertGreater(info["model_bytes"], 0)
        self.assertEqual(info["parameter_count"], 6)
        self.assertEqual(info["format"], "onnx")

    def test_imported_predictions_use_same_metrics_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "images").mkdir()
            Image.new("L", (16, 8), 0).save(root / "images" / "a.png")
            Image.new("L", (16, 8), 0).save(root / "images" / "b.png")
            (root / "sequences.csv").write_text(
                "filename,label\na.png,123\nb.png,456\n",
                encoding="utf-8",
            )
            predictions = root / "mlkit.json"
            predictions.write_text(
                json.dumps(
                    {
                        "model_id": "mlkit_text_recognition_bundled",
                        "backend": "android_mlkit",
                        "model_bytes": 1234,
                        "predictions": [
                            {"filename": "a.png", "text": "123", "latency_ms": 9.0},
                            {"filename": "b.png", "text": "45G", "latency_ms": 11.0},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = evaluate_imported_predictions(
                load_labeled_dataset(root),
                predictions,
            )

        self.assertEqual(result["model_id"], "mlkit_text_recognition_bundled")
        self.assertEqual(result["backend"], "android_mlkit")
        self.assertEqual(result["capacity"]["model_bytes"], 1234)
        self.assertAlmostEqual(result["latency_ms"]["mean"], 10.0)
        self.assertEqual(result["metrics"]["exact"], 0.5)

    def test_cli_records_model_error_without_stopping_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "images").mkdir()
            Image.new("L", (16, 8), 0).save(root / "images" / "a.png")
            (root / "sequences.csv").write_text(
                "filename,label\na.png,123\n",
                encoding="utf-8",
            )
            output = root / "results.json"

            exit_code = main(
                [
                    "--dataset",
                    str(root),
                    "--output",
                    str(output),
                    "--onnx-model",
                    "missing:/no/such/model.onnx",
                ]
            )

            payload = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(exit_code, 0)
        self.assertEqual(payload["results"][0]["model_id"], "missing")
        self.assertEqual(payload["results"][0]["status"], "error")
        self.assertIn("error", payload["results"][0])

    def test_format_results_table_includes_capacity_latency_and_metrics(self):
        table = format_results_table(
            [
                {
                    "model_id": "tiny",
                    "backend": "onnxruntime",
                    "capacity": {"model_bytes": 1024 * 1024, "parameter_count": 12345},
                    "latency_ms": {"mean": 1.25, "p50": 1.1, "p95": 2.0},
                    "metrics": {"exact": 0.5, "cer": 0.1, "digit_accuracy": 0.75},
                },
                {
                    "model_id": "broken",
                    "backend": "onnxruntime",
                    "status": "error",
                    "error": {"message": "unsupported op"},
                    "capacity": {},
                    "latency_ms": {"mean": None, "p50": None, "p95": None},
                    "metrics": {"exact": 0.0, "cer": 0.0, "digit_accuracy": 0.0},
                },
            ]
        )

        self.assertIn("tiny", table)
        self.assertIn("1.00 MB", table)
        self.assertIn("12,345", table)
        self.assertIn("50.00%", table)
        self.assertIn("ERROR", table)


if __name__ == "__main__":
    unittest.main()
