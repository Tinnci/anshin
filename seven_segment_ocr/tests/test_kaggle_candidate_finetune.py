import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from PIL import Image
import yaml

from kaggle_candidate_finetune_kernel import kaggle_candidate_finetune as kernel


class KaggleCandidateFinetuneTest(unittest.TestCase):
    def test_candidate_plan_marks_trainable_and_blocked_models(self):
        plan = kernel.build_candidate_plan(["all"])
        by_id = {row["id"]: row for row in plan}

        self.assertEqual(by_id["light_svtr_tiny"]["status"], "trainable")
        self.assertEqual(by_id["light_svtr_base"]["status"], "trainable")
        self.assertEqual(by_id["light_svtr_large"]["status"], "trainable")
        self.assertEqual(by_id["fastvit_t8_ctc"]["status"], "trainable")
        self.assertEqual(by_id["ppocrv5_mobile_rec"]["status"], "trainable")
        self.assertEqual(by_id["ppocrv5_server_rec"]["status"], "trainable")
        self.assertEqual(by_id["repsvtr"]["status"], "trainable")
        self.assertEqual(by_id["svtrv2_server"]["status"], "trainable")
        self.assertEqual(by_id["ppocrv5_mobile_rec"]["candidate_type"], "paddleocr_trainable")
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

    def test_materialize_paddleocr_config_points_to_kaggle_dataset_and_pretrained_weights(self):
        base_config = {
            "Global": {
                "epoch_num": 75,
                "save_model_dir": "./output/base",
                "pretrained_model": "",
                "character_dict_path": "ppocr/utils/dict/ppocrv5_dict.txt",
                "eval_batch_step": [0, 2000],
            },
            "Optimizer": {"lr": {"learning_rate": 0.0005, "warmup_epoch": 5}},
            "Train": {
                "dataset": {"data_dir": "./train_data", "label_file_list": ["train.txt"]},
                "sampler": {"first_bs": 128},
                "loader": {"batch_size_per_card": 128, "num_workers": 8},
            },
            "Eval": {
                "dataset": {"data_dir": "./train_data", "label_file_list": ["val.txt"]},
                "loader": {"batch_size_per_card": 128, "num_workers": 4},
            },
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            base_path = root / "base.yml"
            base_path.write_text(yaml.safe_dump(base_config), encoding="utf-8")
            ppocr_dir = root / "ppocr_rec"
            ppocr_dir.mkdir()
            for name in ["rec_train.txt", "rec_val.txt", "seven_segment_dict.txt"]:
                (ppocr_dir / name).write_text("", encoding="utf-8")
            candidate_dir = root / "ppocrv5_mobile_rec"
            pretrained_path = candidate_dir / "pretrained.pdparams"
            args = SimpleNamespace(
                paddle_epochs=3,
                paddle_batch_size=7,
                paddle_eval_batch_size=5,
                paddle_lr=0.0002,
                paddle_warmup_epoch=0,
                paddle_eval_step=11,
                paddle_num_workers=2,
            )

            config_path = kernel.materialize_paddleocr_config(
                base_config_path=base_path,
                candidate_id="ppocrv5_mobile_rec",
                ppocr_dir=ppocr_dir,
                candidate_dir=candidate_dir,
                pretrained_path=pretrained_path,
                args=args,
            )

            config = yaml.safe_load(config_path.read_text(encoding="utf-8"))

        self.assertEqual(config["Global"]["epoch_num"], 3)
        self.assertEqual(config["Global"]["pretrained_model"], str(pretrained_path))
        self.assertEqual(config["Global"]["character_dict_path"], str(ppocr_dir / "seven_segment_dict.txt"))
        self.assertEqual(config["Global"]["eval_batch_step"], [0, 11])
        self.assertEqual(config["Optimizer"]["lr"]["learning_rate"], 0.0002)
        self.assertEqual(config["Optimizer"]["lr"]["warmup_epoch"], 0)
        self.assertEqual(config["Train"]["dataset"]["data_dir"], "/")
        self.assertEqual(config["Train"]["dataset"]["label_file_list"], [str(ppocr_dir / "rec_train.txt")])
        self.assertEqual(config["Train"]["sampler"]["first_bs"], 7)
        self.assertEqual(config["Train"]["loader"]["batch_size_per_card"], 7)
        self.assertEqual(config["Eval"]["dataset"]["label_file_list"], [str(ppocr_dir / "rec_val.txt")])
        self.assertEqual(config["Eval"]["loader"]["batch_size_per_card"], 5)

    def test_paddleocr_result_row_uses_ocr_eval_metrics(self):
        result = kernel.build_paddleocr_result_row(
            candidate_id="ppocrv5_mobile_rec",
            architecture="PP-OCRv5 mobile rec",
            onnx_path=Path("/kaggle/working/model.onnx"),
            report={
                "results": [
                    {
                        "metrics": {
                            "exact": 0.8,
                            "cer": 0.1,
                            "digit_accuracy": 0.9,
                        },
                        "latency": {
                            "mean_ms": 12.5,
                            "throughput_sps": 80.0,
                        },
                    }
                ]
            },
        )

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["candidate_type"], "paddleocr_trainable")
        self.assertEqual(result["test_exact"], 0.8)
        self.assertEqual(result["cer"], 0.1)
        self.assertEqual(result["digit_accuracy"], 0.9)
        self.assertEqual(result["mean_latency_ms"], 12.5)
        self.assertEqual(result["throughput_sps"], 80.0)

    def test_build_paddle_install_command_uses_official_cuda_index(self):
        args = SimpleNamespace(
            paddle_package="paddlepaddle-gpu==3.3.0",
            paddle_index_url="https://www.paddlepaddle.org.cn/packages/stable/cu126/",
            paddle_extra_index_url="https://pypi.org/simple",
        )

        command = kernel.build_paddle_install_command(args)

        self.assertIn("paddlepaddle-gpu==3.3.0", command)
        self.assertIn("-i", command)
        self.assertIn("https://www.paddlepaddle.org.cn/packages/stable/cu126/", command)
        self.assertIn("--extra-index-url", command)
        self.assertIn("https://pypi.org/simple", command)


if __name__ == "__main__":
    unittest.main()
