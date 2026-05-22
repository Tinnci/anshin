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
import yaml


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
PADDLEOCR_RAW_BASE = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main"
PADDLEOCR_PRETRAINED_BASE = "https://paddle-model-ecology.bj.bcebos.com/paddlex/official_pretrained_model"
PADDLEOCR_CANDIDATES = {
    "ppocrv5_mobile_rec": {
        "architecture": "PP-OCRv5 mobile rec",
        "base_config_url": f"{PADDLEOCR_RAW_BASE}/configs/rec/PP-OCRv5/PP-OCRv5_mobile_rec.yml",
        "pretrained_url": f"{PADDLEOCR_PRETRAINED_BASE}/PP-OCRv5_mobile_rec_pretrained.pdparams",
    },
    "ppocrv5_server_rec": {
        "architecture": "PP-OCRv5 server rec",
        "base_config_url": f"{PADDLEOCR_RAW_BASE}/configs/rec/PP-OCRv5/PP-OCRv5_server_rec.yml",
        "pretrained_url": f"{PADDLEOCR_PRETRAINED_BASE}/PP-OCRv5_server_rec_pretrained.pdparams",
    },
    "repsvtr": {
        "architecture": "RepSVTR GTC rec",
        "base_config_url": f"{PADDLEOCR_RAW_BASE}/configs/rec/SVTRv2/ch_RepSVTR_rec_gtc.yml",
        "pretrained_url": f"{PADDLEOCR_PRETRAINED_BASE}/ch_RepSVTR_rec_pretrained.pdparams",
    },
    "svtrv2_server": {
        "architecture": "SVTRv2 GTC rec",
        "base_config_url": f"{PADDLEOCR_RAW_BASE}/configs/rec/SVTRv2/ch_SVTRv2_rec_gtc.yml",
        "pretrained_url": f"{PADDLEOCR_PRETRAINED_BASE}/ch_SVTRv2_rec_pretrained.pdparams",
    },
}
TRAINABLE_CANDIDATES = {
    **LIGHT_SVTR_CANDIDATES,
    **{candidate_id: spec["architecture"] for candidate_id, spec in PADDLEOCR_CANDIDATES.items()},
    "fastvit_t8_ctc": "fastvit_t8_ctc",
}
NON_TRAINABLE_CANDIDATES = {
    "parseq": {
        "status": "eval_only",
        "candidate_type": "eval_only",
        "reason": "PARSeq ONNX adapter is implemented, but no fine-tuning adapter is implemented.",
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
            candidate_type = "paddleocr_trainable" if candidate_id in PADDLEOCR_CANDIDATES else "trainable"
            rows.append(
                {
                    "id": candidate_id,
                    "status": "trainable",
                    "candidate_type": candidate_type,
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


def download_file(url: str, output_path: Path) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if not output_path.exists():
        print(f"downloading {url}", flush=True)
        urllib.request.urlretrieve(url, output_path)
    return output_path


def materialize_paddleocr_config(
    *,
    base_config_path: Path,
    candidate_id: str,
    ppocr_dir: Path,
    candidate_dir: Path,
    pretrained_path: Path,
    args: argparse.Namespace,
) -> Path:
    config = yaml.safe_load(base_config_path.read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise ValueError(f"Invalid PaddleOCR config: {base_config_path}")

    global_config = config.setdefault("Global", {})
    global_config["epoch_num"] = args.paddle_epochs
    global_config["save_model_dir"] = str(candidate_dir / "output")
    global_config["save_epoch_step"] = 1
    global_config["eval_batch_step"] = [0, args.paddle_eval_step]
    global_config["pretrained_model"] = str(pretrained_path)
    global_config["character_dict_path"] = str(ppocr_dir / "seven_segment_dict.txt")
    global_config["use_space_char"] = True
    global_config["save_res_path"] = str(candidate_dir / f"{candidate_id}_predicts.txt")

    optimizer = config.setdefault("Optimizer", {})
    lr_config = optimizer.setdefault("lr", {})
    lr_config["learning_rate"] = args.paddle_lr
    lr_config["warmup_epoch"] = args.paddle_warmup_epoch

    train_config = config.setdefault("Train", {})
    train_dataset = train_config.setdefault("dataset", {})
    train_dataset["data_dir"] = "/"
    train_dataset["label_file_list"] = [str(ppocr_dir / "rec_train.txt")]
    train_sampler = train_config.setdefault("sampler", {})
    train_sampler["first_bs"] = args.paddle_batch_size
    train_loader = train_config.setdefault("loader", {})
    train_loader["batch_size_per_card"] = args.paddle_batch_size
    train_loader["num_workers"] = args.paddle_num_workers

    eval_config = config.setdefault("Eval", {})
    eval_dataset = eval_config.setdefault("dataset", {})
    eval_dataset["data_dir"] = "/"
    eval_dataset["label_file_list"] = [str(ppocr_dir / "rec_val.txt")]
    eval_loader = eval_config.setdefault("loader", {})
    eval_loader["batch_size_per_card"] = args.paddle_eval_batch_size
    eval_loader["num_workers"] = args.paddle_num_workers

    config_path = candidate_dir / f"{candidate_id}_train.yml"
    candidate_dir.mkdir(parents=True, exist_ok=True)
    config_path.write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf-8")
    return config_path


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


def build_paddleocr_result_row(
    *,
    candidate_id: str,
    architecture: str,
    onnx_path: Path,
    report: dict[str, Any],
) -> dict[str, Any]:
    result = (report.get("results") or [{}])[0]
    metrics = result.get("metrics", {}) if isinstance(result, dict) else {}
    latency = result.get("latency", {}) if isinstance(result, dict) else {}
    return {
        "model_id": candidate_id,
        "candidate_type": "paddleocr_trainable",
        "status": "ok",
        "architecture": architecture,
        "onnx_path": str(onnx_path),
        "test_exact": metrics.get("exact"),
        "cer": metrics.get("cer"),
        "digit_accuracy": metrics.get("digit_accuracy"),
        "mean_latency_ms": latency.get("mean_ms"),
        "throughput_sps": latency.get("throughput_sps"),
    }


def ensure_paddleocr_dependencies(args: argparse.Namespace) -> Path:
    paddleocr_dir = Path(args.paddleocr_dir)
    if not paddleocr_dir.exists():
        subprocess.run(
            [
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                args.paddleocr_ref,
                "https://github.com/PaddlePaddle/PaddleOCR.git",
                str(paddleocr_dir),
            ],
            check=True,
        )
    if not args.paddle_skip_install:
        subprocess.run([sys.executable, "-m", "pip", "install", "paddlepaddle-gpu"], check=True)
        subprocess.run([sys.executable, "-m", "pip", "install", "-r", str(paddleocr_dir / "requirements.txt")], check=True)
        subprocess.run([sys.executable, "-m", "pip", "install", "paddle2onnx"], check=True)
    return paddleocr_dir


def run_command(command: list[str], *, cwd: Path, log_path: Path) -> None:
    with log_path.open("a", encoding="utf-8") as log:
        log.write("$ " + " ".join(command) + "\n")
        log.flush()
        subprocess.run(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT, check=True)


def run_paddleocr_candidate(
    candidate_id: str,
    samples: list[Sample],
    output_dir: Path,
    args: argparse.Namespace,
) -> dict[str, Any]:
    spec = PADDLEOCR_CANDIDATES[candidate_id]
    candidate_dir = output_dir / candidate_id
    ppocr_dir = output_dir / "ppocr_rec"
    paddleocr_dir = ensure_paddleocr_dependencies(args)
    base_config_path = download_file(str(spec["base_config_url"]), candidate_dir / "base_train.yml")
    pretrained_path = download_file(str(spec["pretrained_url"]), candidate_dir / "pretrained.pdparams")
    config_path = materialize_paddleocr_config(
        base_config_path=base_config_path,
        candidate_id=candidate_id,
        ppocr_dir=ppocr_dir,
        candidate_dir=candidate_dir,
        pretrained_path=pretrained_path,
        args=args,
    )
    log_path = candidate_dir / "paddleocr_train.log"
    run_command([sys.executable, "tools/train.py", "-c", str(config_path)], cwd=paddleocr_dir, log_path=log_path)
    checkpoint_prefix = candidate_dir / "output" / "best_accuracy"
    inference_dir = candidate_dir / "inference"
    run_command(
        [
            sys.executable,
            "tools/export_model.py",
            "-c",
            str(config_path),
            "-o",
            f"Global.checkpoints={checkpoint_prefix}",
            f"Global.save_inference_dir={inference_dir}",
        ],
        cwd=paddleocr_dir,
        log_path=log_path,
    )
    onnx_path = candidate_dir / f"{candidate_id}.onnx"
    run_command(
        [
            "paddle2onnx",
            "--model_dir",
            str(inference_dir),
            "--model_filename",
            "inference.pdmodel",
            "--params_filename",
            "inference.pdiparams",
            "--save_file",
            str(onnx_path),
            "--opset_version",
            "11",
            "--enable_onnx_checker",
            "True",
        ],
        cwd=paddleocr_dir,
        log_path=log_path,
    )
    ensure_remote_file("ocr_model_eval.py")
    from ocr_model_eval import evaluate_onnx_model, load_labeled_dataset

    manifest_dir = write_absolute_manifest(samples, candidate_dir / "eval_manifest")
    eval_samples = load_labeled_dataset(manifest_dir)
    eval_result = evaluate_onnx_model(
        candidate_id,
        onnx_path,
        eval_samples,
        adapter="paddleocr_ctc",
        metadata_path=inference_dir / "inference.yml",
        batch_size=args.paddle_eval_batch_size,
    )
    report = {"results": [eval_result]}
    (candidate_dir / "paddleocr_eval_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    return build_paddleocr_result_row(
        candidate_id=candidate_id,
        architecture=str(spec["architecture"]),
        onnx_path=onnx_path,
        report=report,
    )


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
    if candidate_id in PADDLEOCR_CANDIDATES:
        return run_paddleocr_candidate(candidate_id, samples, output_dir, args)
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
    parser.add_argument("--paddleocr-dir", default="/kaggle/working/PaddleOCR")
    parser.add_argument("--paddleocr-ref", default="main")
    parser.add_argument("--paddle-skip-install", action="store_true")
    parser.add_argument("--paddle-epochs", type=int, default=10)
    parser.add_argument("--paddle-batch-size", type=int, default=32)
    parser.add_argument("--paddle-eval-batch-size", type=int, default=32)
    parser.add_argument("--paddle-lr", type=float, default=2e-4)
    parser.add_argument("--paddle-warmup-epoch", type=int, default=1)
    parser.add_argument("--paddle-eval-step", type=int, default=500)
    parser.add_argument("--paddle-num-workers", type=int, default=2)
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
