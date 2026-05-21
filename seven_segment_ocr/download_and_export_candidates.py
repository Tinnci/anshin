"""Prepare export plans for OCR model candidates.

This tool intentionally starts with a dry-run plan because PaddleOCR, PARSeq,
and TrOCR export paths pull heavy framework dependencies. The generated JSON is
used to audit what will be downloaded/exported before mutating the environment.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def build_export_plan(output_dir: Path) -> dict[str, object]:
    output_dir = Path(output_dir)
    return {
        "mode": "dry_run",
        "output_dir": str(output_dir),
        "sources": [
            "https://www.paddleocr.ai/main/en/version2.x/legacy/paddle2onnx.html",
            "https://www.paddleocr.ai/latest/en/version3.x/deployment/obtaining_onnx_models.html",
            "https://huggingface.co/docs/optimum/exporters/onnx/overview",
            "https://github.com/baudm/parseq",
        ],
        "candidates": [
            {
                "id": "light_svtr_exported",
                "family": "LightSVTR",
                "status": "ready",
                "onnx_path": "exported/svtr_seven_seg.onnx",
                "command": None,
            },
            {
                "id": "light_svtr_kaggle_domain",
                "family": "LightSVTR",
                "status": "ready_if_artifact_downloaded",
                "onnx_path": str(output_dir / "svtr_seven_seg_domain.onnx"),
                "command": "kaggle kernels output tiiann/seven-segment-ocr-domain-adaptation -p <artifact-dir>",
            },
            {
                "id": "ppocrv5_mobile_rec",
                "family": "PaddleOCR PP-OCRv5",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "ppocrv5_mobile_rec.onnx"),
                "command": "paddle2onnx --model_dir <PP-OCRv5_mobile_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/ppocrv5_mobile_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "en_ppocrv5_mobile_rec",
                "family": "PaddleOCR PP-OCRv5",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "en_ppocrv5_mobile_rec.onnx"),
                "command": "paddle2onnx --model_dir <en_PP-OCRv5_mobile_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/en_ppocrv5_mobile_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "ppocrv5_server_rec",
                "family": "PaddleOCR PP-OCRv5",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "ppocrv5_server_rec.onnx"),
                "command": "paddle2onnx --model_dir <PP-OCRv5_server_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/ppocrv5_server_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "repsvtr",
                "family": "RepSVTR",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "repsvtr.onnx"),
                "command": "paddle2onnx --model_dir <RepSVTR_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/repsvtr.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "svtrv2_server",
                "family": "SVTRv2",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "svtrv2_server.onnx"),
                "command": "paddle2onnx --model_dir <SVTRv2_server_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/svtrv2_server.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "parseq",
                "family": "PARSeq",
                "status": "needs_torch_checkpoint_export",
                "onnx_path": str(output_dir / "parseq.onnx"),
                "command": "python <parseq-export-script> --checkpoint <parseq-checkpoint> --output exported_candidates/parseq.onnx",
            },
            {
                "id": "trocr_small_printed",
                "family": "TrOCR",
                "status": "needs_huggingface_optimum_export",
                "onnx_path": str(output_dir / "trocr_small_printed"),
                "command": "optimum-cli export onnx --model microsoft/trocr-small-printed --task image-to-text exported_candidates/trocr_small_printed",
            },
            {
                "id": "trocr_base_printed",
                "family": "TrOCR",
                "status": "needs_huggingface_optimum_export",
                "onnx_path": str(output_dir / "trocr_base_printed"),
                "command": "optimum-cli export onnx --model microsoft/trocr-base-printed --task image-to-text exported_candidates/trocr_base_printed",
            },
            {
                "id": "mlkit_text_recognition_bundled",
                "family": "Google ML Kit Text Recognition",
                "status": "needs_android_runtime_predictions",
                "onnx_path": None,
                "command": "Run the Android benchmark exporter on device and import mlkit_predictions.json.",
            },
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default="exported_candidates")
    parser.add_argument("--output", default="export_candidates_plan.json")
    parser.add_argument("--dry-run", action="store_true", help="Write an export plan without downloading")
    args = parser.parse_args(argv)
    if not args.dry_run:
        raise SystemExit("Only --dry-run is implemented; review the generated plan before enabling downloads.")
    plan = build_export_plan(Path(args.output_dir))
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"output": str(output_path), "candidates": len(plan["candidates"])}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
