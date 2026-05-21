"""Kaggle runner for OCR candidate fine-tuning and performance comparison."""

from __future__ import annotations

import argparse
import csv
import importlib
import json
import random
import subprocess
import sys
import urllib.request
from pathlib import Path
from typing import Any

import numpy as np
import torch


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
DEFAULT_OUTPUT_DIR = Path("/kaggle/working/candidate_finetune")

if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

REMOTE_MODULE_DIR = Path("/kaggle/working/medlog_ocr_modules")
RAW_BASE = "https://raw.githubusercontent.com/Tinnci/anshin/master/seven_segment_ocr"


def ensure_remote_file(relative_path: str, target_name: str | None = None) -> Path:
    REMOTE_MODULE_DIR.mkdir(parents=True, exist_ok=True)
    target = REMOTE_MODULE_DIR / (target_name or Path(relative_path).name)
    if not target.exists():
        url = f"{RAW_BASE}/{relative_path}"
        print(f"downloading {url}", flush=True)
        urllib.request.urlretrieve(url, target)
    if str(REMOTE_MODULE_DIR) not in sys.path:
        sys.path.insert(0, str(REMOTE_MODULE_DIR))
    return target


try:
    from kaggle_domain_adaptation_kernel.kaggle_domain_adaptation import (  # type: ignore  # noqa: E402
        Sample,
        assert_runtime_supported,
        cleanup_synthetic_images,
        collect_runtime_info,
        count_samples,
        find_real_dataset,
        generate_synthetic_dataset,
        read_labeled_dataset,
        train_student,
        write_json,
        write_ppocr_rec_files,
        write_startup_artifacts,
    )
except ModuleNotFoundError:
    ensure_remote_file("kaggle_domain_adaptation_kernel/kaggle_domain_adaptation.py")
    from kaggle_domain_adaptation import (  # type: ignore  # noqa: E402
        Sample,
        assert_runtime_supported,
        cleanup_synthetic_images,
        collect_runtime_info,
        count_samples,
        find_real_dataset,
        generate_synthetic_dataset,
        read_labeled_dataset,
        train_student,
        write_json,
        write_ppocr_rec_files,
        write_startup_artifacts,
    )


LIGHT_SVTR_CANDIDATES = {
    "light_svtr_tiny": "tiny",
    "light_svtr_base": "base",
    "light_svtr_large": "large",
}
TRAINABLE_CANDIDATES = {
    **LIGHT_SVTR_CANDIDATES,
    "fastvit_t8_ctc": "fastvit_t8_ctc",
}
NON_TRAINABLE_CANDIDATES = {
    "parseq": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PARSeq ONNX adapter is implemented, but no fine-tuning adapter is implemented.",
    },
    "ppocrv5_mobile_rec": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PaddleOCR fine-tuning requires a separate PaddleOCR training config.",
    },
    "ppocrv5_server_rec": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PaddleOCR fine-tuning requires a separate PaddleOCR training config.",
    },
    "repsvtr": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PaddleOCR fine-tuning requires a separate PaddleOCR training config.",
    },
    "svtrv2_server": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PaddleOCR fine-tuning requires a separate PaddleOCR training config.",
    },
    "trocr_small_printed": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "TrOCR ONNX evaluation exists, but no project fine-tuning adapter is implemented.",
    },
    "trocr_base_printed": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "TrOCR ONNX evaluation exists, but no project fine-tuning adapter is implemented.",
    },
    "mlkit_text_recognition_bundled": {
        "status": "official_runtime_only",
        "candidate_type": "closed_sdk",
        "reason": "Closed SDK; use official Android runtime prediction import.",
    },
    "siglip_nano": {
        "status": "blocked_missing_checkpoint",
        "candidate_type": "blocked",
        "reason": "No concrete official Google SigLIP-Nano 15M checkpoint/repo id is selected.",
    },
}


def parse_candidate_selection(value: str) -> list[str]:
    selected = [part.strip() for part in value.split(",") if part.strip()]
    return selected or ["all"]


def build_candidate_plan(selected: list[str]) -> list[dict[str, str]]:
    include_all = selected == ["all"]
    ids = list(TRAINABLE_CANDIDATES) + list(NON_TRAINABLE_CANDIDATES)
    rows: list[dict[str, str]] = []
    for candidate_id in ids:
        if not include_all and candidate_id not in selected:
            continue
        if candidate_id in TRAINABLE_CANDIDATES:
            rows.append(
                {
                    "id": candidate_id,
                    "status": "trainable",
                    "candidate_type": "trainable",
                    "architecture": TRAINABLE_CANDIDATES[candidate_id],
                }
            )
        else:
            rows.append({"id": candidate_id, **NON_TRAINABLE_CANDIDATES[candidate_id]})
    return rows


def write_absolute_manifest(samples: list[Sample], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    with (output_dir / "sequences.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["filename", "label"])
        writer.writeheader()
        for sample in samples:
            writer.writerow({"filename": str(sample.image_path), "label": sample.label})
    return output_dir


def _metric(value: Any) -> float | None:
    return float(value) if isinstance(value, (int, float)) else None


def _format_pct(value: Any) -> str:
    metric = _metric(value)
    return "n/a" if metric is None else f"{metric:.2%}"


def _format_float(value: Any) -> str:
    metric = _metric(value)
    return "n/a" if metric is None else f"{metric:.4f}"


def format_candidate_table(results: list[dict[str, Any]]) -> str:
    headers = ["model", "type", "status", "test_exact", "test_loss", "artifact"]
    rows = []
    for result in results:
        rows.append(
            [
                str(result.get("model_id", "")),
                str(result.get("candidate_type", "")),
                str(result.get("status", "")),
                _format_pct(result.get("test_exact")),
                _format_float(result.get("test_loss")),
                str(result.get("onnx_path") or result.get("reason") or ""),
            ]
        )
    widths = [
        max(len(headers[index]), *(len(row[index]) for row in rows)) if rows else len(headers[index])
        for index in range(len(headers))
    ]
    lines = [
        " | ".join(headers[index].ljust(widths[index]) for index in range(len(headers))),
        " | ".join("-" * widths[index] for index in range(len(headers))),
    ]
    lines.extend(
        " | ".join(row[index].ljust(widths[index]) for index in range(len(headers)))
        for row in rows
    )
    return "\n".join(lines)


def prepare_samples(output_dir: Path, synthetic_samples: int, real_world_ratio: float, seed: int) -> list[Sample]:
    samples: list[Sample] = []
    real_root = find_real_dataset()
    if real_root:
        real_samples = read_labeled_dataset(real_root, source="real")
        samples.extend(real_samples)
        print(f"real dataset: {real_root} ({len(real_samples)} samples)", flush=True)
    else:
        print("no real labeled dataset found; training will use synthetic only", flush=True)
    synthetic = generate_synthetic_dataset(
        output_dir=output_dir,
        num_samples=synthetic_samples,
        real_world_ratio=real_world_ratio,
        seed=seed,
    )
    samples.extend(synthetic)
    print("sample counts:", json.dumps(count_samples(samples), ensure_ascii=False, indent=2), flush=True)
    write_ppocr_rec_files(samples, output_dir)
    return samples


def run_light_svtr_candidate(
    candidate_id: str,
    variant: str,
    samples: list[Sample],
    output_dir: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    candidate_dir = output_dir / candidate_id
    onnx_path = train_student(
        samples=samples,
        output_dir=candidate_dir,
        epochs=args.light_svtr_epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        real_weight=args.real_weight,
        model_variant=variant,
    )
    report = json.loads((candidate_dir / "evaluation_report.json").read_text(encoding="utf-8"))
    test = report.get("test", {})
    validation = report.get("validation", {})
    return {
        "model_id": candidate_id,
        "candidate_type": "trainable",
        "status": "ok",
        "architecture": f"LightSVTR-{variant}",
        "onnx_path": str(onnx_path),
        "test_exact": test.get("exact"),
        "test_loss": test.get("loss"),
        "val_exact": validation.get("exact"),
        "val_loss": validation.get("loss"),
    }


def ensure_fastvit_dependencies() -> None:
    for module_path in ["fastvit_ctc.py", "train_fastvit_ctc.py", "ocr_model_eval.py"]:
        if not (PROJECT_DIR / module_path).exists():
            ensure_remote_file(module_path)
    try:
        importlib.import_module("timm")
    except Exception:
        subprocess.run([sys.executable, "-m", "pip", "install", "timm"], check=True)


def run_fastvit_candidate(samples: list[Sample], output_dir: Path, args: argparse.Namespace) -> dict[str, Any]:
    ensure_fastvit_dependencies()
    from train_fastvit_ctc import train_fastvit_ctc

    candidate_id = "fastvit_t8_ctc"
    candidate_dir = output_dir / candidate_id
    manifest_dir = write_absolute_manifest(samples, candidate_dir / "manifest")
    report = train_fastvit_ctc(
        dataset=manifest_dir,
        output_dir=candidate_dir,
        stage1_epochs=args.fastvit_stage1_epochs,
        stage2_epochs=args.fastvit_stage2_epochs,
        batch_size=args.fastvit_batch_size,
        stage1_lr=args.fastvit_stage1_lr,
        stage2_lr=args.fastvit_stage2_lr,
        pretrained=not args.fastvit_no_pretrained,
    )
    test = report.get("test", {})
    return {
        "model_id": candidate_id,
        "candidate_type": "trainable",
        "status": "ok",
        "architecture": "FastViT-T8+CTC",
        "onnx_path": str(report.get("onnx_path", candidate_dir / "fastvit_t8_ctc.onnx")),
        "test_exact": test.get("exact"),
        "test_loss": test.get("loss"),
        "digit_accuracy": test.get("digit_accuracy"),
        "cer": test.get("cer"),
    }


def run_trainable_candidate(
    candidate_id: str,
    samples: list[Sample],
    output_dir: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    if candidate_id in LIGHT_SVTR_CANDIDATES:
        return run_light_svtr_candidate(
            candidate_id,
            LIGHT_SVTR_CANDIDATES[candidate_id],
            samples,
            output_dir,
            args,
        )
    if candidate_id == "fastvit_t8_ctc":
        return run_fastvit_candidate(samples, output_dir, args)
    raise ValueError(f"Unsupported trainable candidate: {candidate_id}")


def run_candidate_plan(plan: list[dict[str, str]], samples: list[Sample], output_dir: Path, args: argparse.Namespace) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for row in plan:
        candidate_id = row["id"]
        if row["status"] != "trainable":
            results.append({"model_id": candidate_id, **row})
            continue
        print(f"training candidate: {candidate_id}", flush=True)
        try:
            results.append(run_trainable_candidate(candidate_id, samples, output_dir, args))
        except Exception as exc:
            results.append(
                {
                    "model_id": candidate_id,
                    "candidate_type": "trainable",
                    "status": "error",
                    "error": {"type": type(exc).__name__, "message": str(exc)},
                }
            )
            print(f"candidate failed: {candidate_id}: {type(exc).__name__}: {exc}", flush=True)
    return results


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--candidates", default="all")
    parser.add_argument("--synthetic-samples", type=int, default=30000)
    parser.add_argument("--real-world-ratio", type=float, default=0.45)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=5e-4)
    parser.add_argument("--real-weight", type=int, default=4)
    parser.add_argument("--light-svtr-epochs", type=int, default=30)
    parser.add_argument("--fastvit-stage1-epochs", type=int, default=3)
    parser.add_argument("--fastvit-stage2-epochs", type=int, default=12)
    parser.add_argument("--fastvit-batch-size", type=int, default=16)
    parser.add_argument("--fastvit-stage1-lr", type=float, default=1e-3)
    parser.add_argument("--fastvit-stage2-lr", type=float, default=1e-4)
    parser.add_argument("--fastvit-no-pretrained", action="store_true")
    parser.add_argument("--keep-synthetic-images", action="store_true")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--plan-only", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    write_startup_artifacts(output_dir, install_tee=True)
    runtime = collect_runtime_info()
    assert_runtime_supported(runtime)
    selected = parse_candidate_selection(args.candidates)
    plan = build_candidate_plan(selected)
    write_json(output_dir / "candidate_plan.json", {"candidates": plan})
    if args.plan_only:
        print(json.dumps({"output_dir": str(output_dir), "candidates": len(plan)}, ensure_ascii=False))
        return 0
    samples = prepare_samples(output_dir, args.synthetic_samples, args.real_world_ratio, args.seed)
    results = run_candidate_plan(plan, samples, output_dir, args)
    payload = {
        "runtime": runtime,
        "sample_counts": count_samples(samples),
        "candidate_plan": plan,
        "results": results,
    }
    write_json(output_dir / "candidate_finetune_results.json", payload)
    table = format_candidate_table(results)
    (output_dir / "candidate_finetune_results.txt").write_text(table + "\n", encoding="utf-8")
    print(table, flush=True)
    cleanup_synthetic_images(output_dir, keep_synthetic_images=args.keep_synthetic_images)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
