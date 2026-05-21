import io
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from kaggle_domain_adaptation_kernel import kaggle_domain_adaptation as kernel


class KaggleLoggingTest(unittest.TestCase):
    def test_tee_streams_duplicate_stdout_and_stderr_to_run_log(self):
        with tempfile.TemporaryDirectory() as tmp:
            log_path = Path(tmp) / "run.log"
            stdout = io.StringIO()
            stderr = io.StringIO()
            with log_path.open("w", encoding="utf-8") as log_file:
                tee_stdout, tee_stderr = kernel.make_tee_streams(stdout, stderr, log_file)
                tee_stdout.write("stdout marker\n")
                tee_stderr.write("stderr marker\n")
                tee_stdout.flush()
                tee_stderr.flush()

            self.assertEqual(stdout.getvalue(), "stdout marker\n")
            self.assertEqual(stderr.getvalue(), "stderr marker\n")
            log_text = log_path.read_text(encoding="utf-8")
            self.assertIn("stdout marker", log_text)
            self.assertIn("stderr marker", log_text)

    def test_startup_artifacts_create_run_log_and_runtime_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)
            runtime = {"python": "test-python", "cuda_available": False}

            run_log = kernel.write_startup_artifacts(output_dir, runtime, install_tee=False)

            self.assertEqual(run_log, output_dir / "run.log")
            self.assertTrue(run_log.exists())
            report = json.loads((output_dir / "runtime_report.json").read_text())
            self.assertEqual(report["python"], "test-python")
            self.assertFalse(report["cuda_available"])

    def test_early_output_dir_parser_supports_kaggle_default_and_overrides(self):
        self.assertEqual(
            kernel.parse_early_output_dir([]),
            Path("/kaggle/working/domain_adaptation"),
        )
        self.assertEqual(
            kernel.parse_early_output_dir(["--output-dir", "custom/output"]),
            Path("custom/output"),
        )
        self.assertEqual(
            kernel.parse_early_output_dir(["--output-dir=other/output"]),
            Path("other/output"),
        )

    def test_incompatible_cuda_devices_are_reported_before_training(self):
        runtime = {
            "cuda_available": True,
            "gpu_devices": [
                {"name": "Tesla P100-PCIE-16GB", "capability": [6, 0]},
                {"name": "Tesla T4", "capability": [7, 5]},
            ],
        }

        unsupported = kernel.find_unsupported_cuda_devices(runtime)

        self.assertEqual(len(unsupported), 1)
        self.assertEqual(unsupported[0]["name"], "Tesla P100-PCIE-16GB")


if __name__ == "__main__":
    unittest.main()
