from __future__ import annotations

import json
from pathlib import Path
from typing import Any


METRIC_KEYS = {
    "exact": ["exact", "exact_match", "exact_match_rate", "accuracy"],
    "cer": ["cer", "character_error_rate"],
    "digit_accuracy": ["digit_accuracy", "digit_acc"],
    "latency_ms": ["latency_ms", "mean_latency_ms", "avg_latency_ms"],
    "model_size_bytes": ["model_size_bytes", "size_bytes", "file_size_bytes"],
}


def scan_leaderboard(runs_root: str | Path) -> list[dict[str, Any]]:
    root = Path(runs_root)
    rows: list[dict[str, Any]] = []
    if not root.exists():
        return rows
    for report_path in sorted(root.rglob("run_report.json")):
        run_dir = report_path.parent
        report = _read_json(report_path) or {}
        run_name = str(report.get("run", {}).get("name") or run_dir.name)
        status = "failed" if report.get("summary", {}).get("failed", 0) else "ok"
        metric_rows = _metric_rows(run_dir)
        if not metric_rows:
            rows.append(_row(run_name=run_name, source=_source_for(run_dir), task_id="", model_id="", status=status, metrics={}, path=report_path))
        for metric in metric_rows:
            rows.append(
                _row(
                    run_name=run_name,
                    source=_source_for(run_dir),
                    task_id=str(metric.get("task_id", "")),
                    model_id=str(metric.get("model_id") or metric.get("id") or metric.get("name") or metric.get("task_id") or ""),
                    status=str(metric.get("status") or status),
                    metrics=metric,
                    path=Path(str(metric.get("_path", report_path))),
                )
            )
    rows.sort(key=lambda row: (_numeric(row.get("exact")), -_numeric(row.get("cer"), reverse=True)), reverse=True)
    return rows


def _metric_rows(run_dir: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for path in sorted(run_dir.rglob("*.json")):
        if path.name in {"pipeline_task.json", "run_report.json", "dataset_profile.json"}:
            continue
        data = _read_json(path)
        if not isinstance(data, dict):
            continue
        candidates: list[dict[str, Any]] = []
        for key in ("results", "models", "candidates"):
            value = data.get(key)
            if isinstance(value, list):
                candidates.extend(item for item in value if isinstance(item, dict))
        if not candidates and any(_pick_metric(data, names) is not None for names in METRIC_KEYS.values()):
            candidates = [data]
        for candidate in candidates:
            row = dict(candidate)
            row["_path"] = str(path)
            rows.append(row)
    return rows


def _row(*, run_name: str, source: str, task_id: str, model_id: str, status: str, metrics: dict[str, Any], path: Path) -> dict[str, Any]:
    return {
        "run_name": run_name,
        "source": source,
        "task_id": task_id,
        "model_id": model_id,
        "exact": _pick_metric(metrics, METRIC_KEYS["exact"]),
        "cer": _pick_metric(metrics, METRIC_KEYS["cer"]),
        "digit_accuracy": _pick_metric(metrics, METRIC_KEYS["digit_accuracy"]),
        "latency_ms": _pick_metric(metrics, METRIC_KEYS["latency_ms"]),
        "model_size_bytes": _pick_metric(metrics, METRIC_KEYS["model_size_bytes"]),
        "status": status,
        "timestamp": _timestamp(path),
        "path": str(path),
    }


def _pick_metric(row: dict[str, Any], names: list[str]) -> float | None:
    for name in names:
        value = row.get(name)
        if isinstance(value, (int, float)):
            return float(value)
    metrics = row.get("metrics")
    if isinstance(metrics, dict):
        return _pick_metric(metrics, names)
    return None


def _read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def _source_for(run_dir: Path) -> str:
    return "kaggle" if "kaggle" in {part.lower() for part in run_dir.parts} else "local"


def _timestamp(path: Path) -> str:
    try:
        return path.stat().st_mtime_ns.__str__()
    except OSError:
        return ""


def _numeric(value: Any, reverse: bool = False) -> float:
    if value is None:
        return float("inf") if reverse else float("-inf")
    return float(value)
