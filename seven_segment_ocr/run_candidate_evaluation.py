"""Run an integrated evaluation over every configured OCR model candidate."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from dequantize_onnx import convert_integer_ops_to_float
from ocr_model_eval import (
    SCHEMA_VERSION,
    evaluate_imported_predictions,
    evaluate_onnx_model,
    format_results_table,
    load_labeled_dataset,
    summarize_latencies,
)


PROJECT_DIR = Path(__file__).resolve().parent


def _resolve_path(path_value: str | None, base_dir: Path = PROJECT_DIR) -> Path | None:
    if not path_value:
        return None
    path = Path(path_value)
    return path if path.is_absolute() else base_dir / path


def _pending_result(model_id: str, backend: str, reason: str) -> dict[str, object]:
    return {
        "model_id": model_id,
        "backend": backend,
        "status": "pending",
        "reason": reason,
        "capacity": {},
        "latency_ms": summarize_latencies([]),
        "metrics": _empty_metrics(),
        "samples": [],
    }


def _error_result(model_id: str, backend: str, error: Exception) -> dict[str, object]:
    return {
        "model_id": model_id,
        "backend": backend,
        "status": "error",
        "error": {"type": type(error).__name__, "message": str(error)},
        "capacity": {},
        "latency_ms": summarize_latencies([]),
        "metrics": _empty_metrics(),
        "samples": [],
    }


def _empty_metrics() -> dict[str, None]:
    return {
        "exact": None,
        "normalized_exact": None,
        "cer": None,
        "digit_accuracy": None,
    }


def _prediction_import_for_candidate(
    candidate: dict[str, Any],
    prediction_imports: dict[str, Path],
) -> Path | None:
    candidate_id = str(candidate.get("id", ""))
    if candidate_id in prediction_imports:
        return prediction_imports[candidate_id]
    if candidate.get("family") == "Google ML Kit Text Recognition":
        return prediction_imports.get("mlkit")
    return None


def run_candidate_evaluation(
    *,
    dataset: Path,
    candidate_config: Path,
    output: Path,
    table_output: Path | None = None,
    limit: int | None = None,
    warmup: int = 2,
    batch_size: int = 1,
    prediction_imports: dict[str, Path] | None = None,
    force_prepare: bool = True,
) -> dict[str, object]:
    samples = load_labeled_dataset(dataset)
    selected_samples = samples[:limit] if limit else samples
    config = json.loads(candidate_config.read_text(encoding="utf-8"))
    prediction_imports = prediction_imports or {}
    results: list[dict[str, object]] = []
    preparation: list[dict[str, object]] = []

    for candidate in config.get("candidates", []):
        candidate_id = str(candidate.get("id", "unnamed_candidate"))
        adapter = str(candidate.get("preferred_adapter", "unknown"))
        local_path = _resolve_path(candidate.get("local_path"))
        prediction_path = prediction_imports.get(candidate_id)
        if prediction_path and prediction_path.exists():
            results.append(evaluate_imported_predictions(selected_samples, prediction_path))
            continue

        try:
            if adapter == "dequantize_onnx_then_onnx":
                source_path = _resolve_path(candidate.get("source_path"))
                if source_path is None or not source_path.exists():
                    results.append(
                        _pending_result(
                            candidate_id,
                            adapter,
                            f"source model missing: {candidate.get('source_path')}",
                        )
                    )
                    continue
                if local_path is None:
                    results.append(_pending_result(candidate_id, adapter, "local_path missing"))
                    continue
                if force_prepare or not local_path.exists():
                    report = convert_integer_ops_to_float(source_path, local_path)
                    preparation.append({"model_id": candidate_id, **report})
                results.append(
                    evaluate_onnx_model(
                        candidate_id,
                        local_path,
                        selected_samples,
                        limit=None,
                        warmup=warmup,
                    )
                )
            elif adapter == "onnx":
                if local_path is None or not local_path.exists():
                    results.append(
                        _pending_result(
                            candidate_id,
                            adapter,
                            f"ONNX model missing: {candidate.get('local_path')}",
                        )
                    )
                    continue
                results.append(
                    evaluate_onnx_model(
                        candidate_id,
                        local_path,
                        selected_samples,
                        limit=None,
                        warmup=warmup,
                    )
                )
            elif adapter == "paddle_export_then_onnx":
                if local_path is None or not local_path.exists():
                    results.append(
                        _pending_result(
                            candidate_id,
                            adapter,
                            f"Paddle ONNX model missing: {candidate.get('local_path')}",
                        )
                    )
                    continue
                metadata_path = _resolve_path(candidate.get("metadata_path"))
                if metadata_path is None or not metadata_path.exists():
                    results.append(
                        _pending_result(
                            candidate_id,
                            adapter,
                            f"Paddle metadata missing: {candidate.get('metadata_path')}",
                        )
                    )
                    continue
                results.append(
                    evaluate_onnx_model(
                        candidate_id,
                        local_path,
                        selected_samples,
                        limit=None,
                        warmup=warmup,
                        adapter="paddleocr_ctc",
                        metadata_path=metadata_path,
                        batch_size=batch_size,
                    )
                )
            elif adapter == "official_runtime_prediction_import":
                prediction_path = _prediction_import_for_candidate(candidate, prediction_imports)
                if prediction_path is None or not prediction_path.exists():
                    results.append(
                        _pending_result(
                            candidate_id,
                            adapter,
                            "prediction JSON missing for official-runtime model",
                        )
                    )
                    continue
                results.append(evaluate_imported_predictions(selected_samples, prediction_path))
            else:
                results.append(
                    _pending_result(
                        candidate_id,
                        adapter,
                        f"adapter requires external export before evaluation: {adapter}",
                    )
                )
        except Exception as exc:
            results.append(_error_result(candidate_id, adapter, exc))

    summary = {
        "ok": sum(1 for result in results if result.get("status", "ok") == "ok"),
        "pending": sum(1 for result in results if result.get("status") == "pending"),
        "error": sum(1 for result in results if result.get("status") == "error"),
    }
    payload = {
        "schema_version": SCHEMA_VERSION,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "path": str(dataset.resolve()),
            "sample_count": len(selected_samples),
        },
        "candidate_config": str(candidate_config.resolve()),
        "preparation": preparation,
        "summary": summary,
        "results": results,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    table = format_results_table(results)
    if table_output:
        table_output.parent.mkdir(parents=True, exist_ok=True)
        table_output.write_text(table + "\n", encoding="utf-8")
    return payload


def _parse_prediction_imports(values: list[str]) -> dict[str, Path]:
    imports: dict[str, Path] = {}
    for value in values:
        if ":" in value:
            key, path = value.split(":", 1)
            imports[key] = Path(path)
        else:
            imports[Path(value).stem] = Path(value)
    return imports


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--candidate-config", default="model_candidates.json")
    parser.add_argument("--output", required=True)
    parser.add_argument("--table-output", default=None)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--warmup", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument(
        "--import-predictions",
        action="append",
        default=[],
        metavar="ID:PATH",
        help="Prediction JSON for official-runtime candidates, e.g. mlkit:path.json",
    )
    parser.add_argument("--print-table", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    table_output = Path(args.table_output) if args.table_output else None
    payload = run_candidate_evaluation(
        dataset=Path(args.dataset),
        candidate_config=Path(args.candidate_config),
        output=Path(args.output),
        table_output=table_output,
        limit=args.limit,
        warmup=args.warmup,
        batch_size=args.batch_size,
        prediction_imports=_parse_prediction_imports(args.import_predictions),
    )
    if args.print_table:
        print(format_results_table(payload["results"]))
    print(json.dumps({"output": args.output, **payload["summary"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
