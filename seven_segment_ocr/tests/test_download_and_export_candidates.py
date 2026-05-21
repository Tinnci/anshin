import json
import tempfile
import unittest
import unittest.mock
from pathlib import Path

from download_and_export_candidates import build_export_plan, main


class DownloadAndExportCandidatesTest(unittest.TestCase):
    def test_build_export_plan_covers_candidate_families(self):
        plan = build_export_plan(Path("exported_candidates"))
        ids = {item["id"] for item in plan["candidates"]}

        self.assertIn("ppocrv5_mobile_rec", ids)
        self.assertIn("repsvtr", ids)
        self.assertIn("svtrv2_server", ids)
        self.assertIn("parseq", ids)
        self.assertIn("trocr_small_printed", ids)
        self.assertIn("mlkit_text_recognition_bundled", ids)

    def test_dry_run_writes_plan_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "plan.json"

            exit_code = main(["--dry-run", "--output", str(output)])
            payload = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual(exit_code, 0)
        self.assertEqual(payload["mode"], "dry_run")
        self.assertGreaterEqual(len(payload["candidates"]), 8)

    @unittest.mock.patch("download_and_export_candidates._download_hf_or_ms_repo")
    @unittest.mock.patch("download_and_export_candidates._download_tar_and_extract")
    def test_download_candidates_calls_correct_downloaders(self, mock_tar_download, mock_repo_download):
        from download_and_export_candidates import download_candidate
        
        # Test trocr_small_printed with HuggingFace
        download_candidate("trocr_small_printed", Path("out"), use_modelscope=False)
        mock_repo_download.assert_called_with("microsoft/trocr-small-printed", Path("out/trocr_small_printed"), False)
        
        # Test trocr_small_printed with ModelScope
        mock_repo_download.reset_mock()
        download_candidate("trocr_small_printed", Path("out"), use_modelscope=True)
        mock_repo_download.assert_called_with("LLM-Research/trocr-small-printed", Path("out/trocr_small_printed"), True)

        # Test parseq with HuggingFace
        mock_repo_download.reset_mock()
        download_candidate("parseq", Path("out"), use_modelscope=False)
        mock_repo_download.assert_called_with("baudm/parseq-tiny", Path("out/parseq"), False)

        # Test parseq with ModelScope
        mock_repo_download.reset_mock()
        download_candidate("parseq", Path("out"), use_modelscope=True)
        mock_repo_download.assert_called_with("tiiann/parseq-tiny", Path("out/parseq"), True)

        # Test ppocrv5_mobile_rec (Paddle URL)
        download_candidate("ppocrv5_mobile_rec", Path("out"))
        mock_tar_download.assert_called_with(
            "https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_infer.tar",
            Path("out/ppocrv5_mobile_rec")
        )

        # Test en_ppocrv5_mobile_rec (Paddle URL)
        mock_tar_download.reset_mock()
        download_candidate("en_ppocrv5_mobile_rec", Path("out"))
        mock_tar_download.assert_called_with(
            "https://paddleocr.bj.bcebos.com/PP-OCRv4/english/en_PP-OCRv4_rec_infer.tar",
            Path("out/en_ppocrv5_mobile_rec")
        )

        # Test svtrv2_server (Paddle URL)
        mock_tar_download.reset_mock()
        download_candidate("svtrv2_server", Path("out"))
        mock_tar_download.assert_called_with(
            "https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_server_infer.tar",
            Path("out/svtrv2_server")
        )


if __name__ == "__main__":
    unittest.main()
