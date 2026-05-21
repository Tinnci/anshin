import json
import tempfile
import unittest
import unittest.mock
from pathlib import Path

from PIL import Image

from run_candidate_evaluation import main


class RunCandidateEvaluationTest(unittest.TestCase):
    def test_main_evaluates_available_models_and_marks_missing_candidates_pending(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dataset = root / "dataset"
            dataset.mkdir()
            (dataset / "images").mkdir()
            Image.new("L", (16, 8), 0).save(dataset / "images" / "a.png")
            (dataset / "sequences.csv").write_text(
                "filename,label\na.png,123\n",
                encoding="utf-8",
            )
            ready_model = root / "ready.onnx"
            ready_model.write_bytes(b"fake")
            source_model = root / "source.onnx"
            source_model.write_bytes(b"fake source")
            dequant_model = root / "dequant.onnx"
            config = root / "candidates.json"
            config.write_text(
                json.dumps(
                    {
                        "schema_version": 1,
                        "candidates": [
                            {
                                "id": "ready",
                                "preferred_adapter": "onnx",
                                "local_path": str(ready_model),
                            },
                            {
                                "id": "derived",
                                "preferred_adapter": "dequantize_onnx_then_onnx",
                                "source_path": str(source_model),
                                "local_path": str(dequant_model),
                            },
                            {
                                "id": "missing_open",
                                "preferred_adapter": "paddle_export_then_onnx",
                            },
                            {
                                "id": "ready_paddle",
                                "preferred_adapter": "paddle_export_then_onnx",
                                "local_path": str(ready_model),
                                "metadata_path": str(root / "inference.yml"),
                            },
                            {
                                "id": "ready_parseq",
                                "preferred_adapter": "torch_export_then_onnx",
                                "local_path": str(ready_model),
                                "metadata_path": str(root / "parseq_metadata.json"),
                            },
                            {
                                "id": "ready_torch_ctc",
                                "preferred_adapter": "torch_ctc_onnx",
                                "local_path": str(ready_model),
                                "metadata_path": str(root / "torch_ctc_metadata.json"),
                            },
                            {
                                "id": "mlkit",
                                "preferred_adapter": "official_runtime_prediction_import",
                                "onnx_supported": False,
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            output = root / "results.json"
            table = root / "results.txt"
            (root / "inference.yml").write_text(
                "PostProcess:\n  character_dict:\n    - '1'\n",
                encoding="utf-8",
            )
            (root / "parseq_metadata.json").write_text(
                json.dumps({"tokens": ["[E]", "1"]}),
                encoding="utf-8",
            )
            (root / "torch_ctc_metadata.json").write_text(
                json.dumps({"adapter": "torch_ctc_onnx"}),
                encoding="utf-8",
            )

            def fake_convert(_src, dst):
                Path(dst).write_bytes(b"converted")
                return {
                    "converted_conv_integer": 1,
                    "converted_matmul_integer": 0,
                    "skipped_integer_ops": 0,
                }

            def fake_eval(model_id, *_args, **_kwargs):
                return {
                    "model_id": model_id,
                    "backend": "onnxruntime",
                    "capacity": {"model_bytes": 4, "parameter_count": 1},
                    "latency_ms": {"mean": 1.0, "p50": 1.0, "p95": 1.0},
                    "metrics": {"exact": 1.0, "cer": 0.0, "digit_accuracy": 1.0},
                    "samples": [],
                }

            def fake_parseq_eval(model_id, output, *_args, **_kwargs):
                Path(output).write_text(
                    json.dumps(
                        {
                            "model_id": model_id,
                            "backend": "onnxruntime_parseq",
                            "model_bytes": 4,
                            "parameter_count": 1,
                            "predictions": [
                                {"filename": "a.png", "text": "123", "latency_ms": 1.0}
                            ],
                        }
                    ),
                    encoding="utf-8",
                )
                return {"output": str(output), "samples": 1}

            with unittest.mock.patch(
                "run_candidate_evaluation.convert_integer_ops_to_float",
                side_effect=fake_convert,
            ), unittest.mock.patch(
                "run_candidate_evaluation.evaluate_onnx_model",
                side_effect=fake_eval,
            ), unittest.mock.patch(
                "run_candidate_evaluation.evaluate_parseq_onnx",
                side_effect=fake_parseq_eval,
            ):
                exit_code = main(
                    [
                        "--dataset",
                        str(dataset),
                        "--candidate-config",
                        str(config),
                        "--output",
                        str(output),
                        "--table-output",
                        str(table),
                    ]
                )

            payload = json.loads(output.read_text(encoding="utf-8"))
            statuses = {
                result["model_id"]: result.get("status", "ok")
                for result in payload["results"]
            }
            table_text = table.read_text(encoding="utf-8")

        self.assertEqual(exit_code, 0)
        self.assertEqual(statuses["ready"], "ok")
        self.assertEqual(statuses["derived"], "ok")
        self.assertEqual(statuses["ready_paddle"], "ok")
        self.assertEqual(statuses["ready_parseq"], "ok")
        self.assertEqual(statuses["ready_torch_ctc"], "ok")
        self.assertEqual(statuses["missing_open"], "pending")
        self.assertEqual(statuses["mlkit"], "pending")
        self.assertIn("PENDING", table_text)


if __name__ == "__main__":
    unittest.main()
