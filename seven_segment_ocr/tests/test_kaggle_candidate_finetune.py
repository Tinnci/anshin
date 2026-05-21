import tempfile
import unittest
from pathlib import Path

from PIL import Image

from kaggle_candidate_finetune_kernel import kaggle_candidate_finetune as kernel


class KaggleCandidateFinetuneTest(unittest.TestCase):
    def test_candidate_plan_marks_trainable_and_blocked_models(self):
        plan = kernel.build_candidate_plan(["all"])
        by_id = {row["id"]: row for row in plan}

        self.assertEqual(by_id["light_svtr_tiny"]["status"], "trainable")
        self.assertEqual(by_id["light_svtr_base"]["status"], "trainable")
        self.assertEqual(by_id["light_svtr_large"]["status"], "trainable")
        self.assertEqual(by_id["fastvit_t8_ctc"]["status"], "trainable")
        self.assertEqual(by_id["siglip_nano"]["status"], "blocked_missing_checkpoint")
        self.assertEqual(by_id["parseq"]["status"], "eval_only")

    def test_parse_candidate_selection_supports_csv_and_all(self):
        self.assertEqual(kernel.parse_candidate_selection("all"), ["all"])
        self.assertEqual(
            kernel.parse_candidate_selection(" light_svtr_tiny,fastvit_t8_ctc "),
            ["light_svtr_tiny", "fastvit_t8_ctc"],
        )

    def test_write_absolute_manifest_for_fastvit_training(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            image_path = root / "source.png"
            Image.new("RGB", (16, 8), (255, 255, 255)).save(image_path)
            sample = kernel.Sample(
                image_path=image_path,
                label="123",
                split="train",
                source="synthetic",
            )

            manifest_dir = kernel.write_absolute_manifest([sample], root / "manifest")

            text = (manifest_dir / "sequences.csv").read_text(encoding="utf-8")

        self.assertIn(str(image_path), text)
        self.assertIn("123", text)

    def test_format_result_table_includes_status_and_metrics(self):
        table = kernel.format_candidate_table(
            [
                {
                    "model_id": "light_svtr_tiny",
                    "status": "ok",
                    "candidate_type": "trainable",
                    "test_exact": 0.75,
                    "test_loss": 0.2,
                    "onnx_path": "/tmp/model.onnx",
                },
                {
                    "model_id": "siglip_nano",
                    "status": "blocked_missing_checkpoint",
                    "candidate_type": "blocked",
                    "reason": "No official checkpoint selected",
                },
            ]
        )

        self.assertIn("light_svtr_tiny", table)
        self.assertIn("75.00%", table)
        self.assertIn("blocked_missing_checkpoint", table)


if __name__ == "__main__":
    unittest.main()
