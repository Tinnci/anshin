"""Prepare and execute export and download plans for OCR model candidates.

This tool supports both a dry-run plan generation and real programmatic model downloading
using huggingface_hub, modelscope, and standard urllib downloaders.
"""

from __future__ import annotations

import argparse
import json
import tarfile
import urllib.request
import shutil
from pathlib import Path


PADDLE_INFERENCE_URLS = {
    "ppocrv5_mobile_rec": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv5_mobile_rec_infer.tar",
    "ppocrv5_server_rec": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/PP-OCRv5_server_rec_infer.tar",
    "en_ppocrv5_mobile_rec": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/en_PP-OCRv4_mobile_rec_infer.tar",
    "repsvtr": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/ch_RepSVTR_rec_infer.tar",
    "svtrv2_server": "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/ch_SVTRv2_rec_infer.tar",
}


def build_export_plan(output_dir: Path) -> dict[str, object]:
    output_dir = Path(output_dir)
    return {
        "mode": "plan",
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
                "id": "app_dequant_svtr",
                "family": "LightSVTR",
                "status": "ready",
                "onnx_path": "exported_candidates/app_svtr_dequant.onnx",
                "command": "python dequantize_onnx.py --input ../app/src/main/assets/svtr_seven_seg.onnx --output exported_candidates/app_svtr_dequant.onnx",
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
                "download_url": PADDLE_INFERENCE_URLS["ppocrv5_mobile_rec"],
                "command": "paddle2onnx --model_dir <PP-OCRv5_mobile_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/ppocrv5_mobile_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "en_ppocrv5_mobile_rec",
                "family": "PaddleOCR PP-OCRv5",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "en_ppocrv5_mobile_rec.onnx"),
                "download_url": PADDLE_INFERENCE_URLS["en_ppocrv5_mobile_rec"],
                "command": "paddle2onnx --model_dir <en_PP-OCRv4_mobile_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/en_ppocrv5_mobile_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "ppocrv5_server_rec",
                "family": "PaddleOCR PP-OCRv5",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "ppocrv5_server_rec.onnx"),
                "download_url": PADDLE_INFERENCE_URLS["ppocrv5_server_rec"],
                "command": "paddle2onnx --model_dir <PP-OCRv5_server_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/ppocrv5_server_rec.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "repsvtr",
                "family": "RepSVTR",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "repsvtr.onnx"),
                "download_url": PADDLE_INFERENCE_URLS["repsvtr"],
                "command": "paddle2onnx --model_dir <ch_RepSVTR_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/repsvtr.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "svtrv2_server",
                "family": "SVTRv2",
                "status": "needs_paddle_static_model_export",
                "onnx_path": str(output_dir / "svtrv2_server.onnx"),
                "download_url": PADDLE_INFERENCE_URLS["svtrv2_server"],
                "command": "paddle2onnx --model_dir <ch_SVTRv2_rec_infer> --model_filename inference.pdmodel --params_filename inference.pdiparams --save_file exported_candidates/svtrv2_server.onnx --opset_version 11 --enable_onnx_checker True",
            },
            {
                "id": "parseq",
                "family": "PARSeq",
                "status": "needs_torch_checkpoint_export",
                "onnx_path": str(output_dir / "parseq.onnx"),
                "command": "python <parseq-export-script> --checkpoint <parseq-checkpoint> --output exported_candidates/parseq.onnx",
            },
            {
                "id": "fastvit_t8_ctc",
                "family": "Apple FastViT",
                "status": "needs_two_stage_finetune",
                "onnx_path": str(output_dir / "fastvit_t8_ctc" / "fastvit_t8_ctc.onnx"),
                "source": "timm/fastvit_t8.apple_in1k",
                "command": "python train_fastvit_ctc.py --dataset /tmp/medlog_bare_benchmark --output-dir exported_candidates/fastvit_t8_ctc",
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


def _download_hf_or_ms_repo(repo_id: str, target_dir: Path, use_modelscope: bool):
    target_dir.mkdir(parents=True, exist_ok=True)
    if use_modelscope:
        from modelscope import snapshot_download
        print(f"Using ModelScope to download {repo_id}...")
        model_dir = snapshot_download(repo_id)
        shutil.copytree(model_dir, target_dir, dirs_exist_ok=True)
    else:
        from huggingface_hub import snapshot_download
        print(f"Using Hugging Face to download {repo_id}...")
        snapshot_download(repo_id=repo_id, local_dir=target_dir)
    print(f"Download complete: {target_dir}")


def _download_tar_and_extract(url: str, target_dir: Path):
    target_dir.mkdir(parents=True, exist_ok=True)
    tar_path = target_dir / "model.tar"
    print(f"Downloading {url} to {tar_path}...")
    urllib.request.urlretrieve(url, tar_path)
    print(f"Extracting {tar_path} into {target_dir}...")
    with tarfile.open(tar_path) as tar:
        _safe_extract_tar(tar, target_dir)
    tar_path.unlink()
    print(f"Extraction complete: {target_dir}")


def _safe_extract_tar(tar: tarfile.TarFile, target_dir: Path) -> None:
    target_root = target_dir.resolve()
    for member in tar.getmembers():
        destination = (target_dir / member.name).resolve()
        if target_root not in [destination, *destination.parents]:
            raise ValueError(f"Refusing unsafe tar member: {member.name}")
    tar.extractall(path=target_dir)


def download_candidate(candidate_id: str, output_dir: Path, use_modelscope: bool = False):
    output_dir = Path(output_dir)
    
    if candidate_id == "trocr_small_printed":
        repo_id = "LLM-Research/trocr-small-printed" if use_modelscope else "microsoft/trocr-small-printed"
        _download_hf_or_ms_repo(repo_id, output_dir / "trocr_small_printed", use_modelscope)
    elif candidate_id == "trocr_base_printed":
        repo_id = "LLM-Research/trocr-base-printed" if use_modelscope else "microsoft/trocr-base-printed"
        _download_hf_or_ms_repo(repo_id, output_dir / "trocr_base_printed", use_modelscope)
    elif candidate_id == "parseq":
        repo_id = "tiiann/parseq-tiny" if use_modelscope else "baudm/parseq-tiny"
        _download_hf_or_ms_repo(repo_id, output_dir / "parseq", use_modelscope)
    elif candidate_id in PADDLE_INFERENCE_URLS:
        _download_tar_and_extract(PADDLE_INFERENCE_URLS[candidate_id], output_dir / candidate_id)
    else:
        print(f"Manual download required for '{candidate_id}' or not supported for direct download.")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default="exported_candidates")
    parser.add_argument("--output", default="export_candidates_plan.json")
    parser.add_argument("--dry-run", action="store_true", help="Write an export plan without downloading")
    parser.add_argument("--download", default=None, help="Specific candidate ID to download, or 'all'")
    parser.add_argument("--use-modelscope", action="store_true", help="Use ModelScope instead of Hugging Face")
    args = parser.parse_args(argv)

    plan = build_export_plan(Path(args.output_dir))
    
    if args.dry_run:
        plan["mode"] = "dry_run"
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(plan, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps({"output": str(output_path), "candidates": len(plan["candidates"])}))
        return 0

    if args.download:
        downloadable = [
            "trocr_small_printed",
            "trocr_base_printed",
            "parseq",
            "ppocrv5_mobile_rec",
            "en_ppocrv5_mobile_rec",
            "ppocrv5_server_rec",
            "repsvtr",
            "svtrv2_server",
        ]
        to_download = downloadable if args.download == "all" else [args.download]
        for cid in to_download:
            try:
                download_candidate(cid, Path(args.output_dir), args.use_modelscope)
            except Exception as e:
                print(f"Error downloading candidate '{cid}': {e}")
        return 0

    # If neither dry-run nor download is specified, print help or default to dry-run
    parser.print_help()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
