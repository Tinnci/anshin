import json
import tempfile
import unittest
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


if __name__ == "__main__":
    unittest.main()
