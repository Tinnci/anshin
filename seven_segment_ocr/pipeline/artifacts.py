from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def read_json(path: str | Path, default: Any = None) -> Any:
    candidate = Path(path)
    if not candidate.exists():
        return default
    return json.loads(candidate.read_text(encoding="utf-8"))


def collect_run_artifacts(run_dir: str | Path) -> list[dict[str, Any]]:
    root = Path(run_dir)
    report = read_json(root / "run_report.json", default={}) or {}
    artifacts = list(report.get("artifacts", []))
    if artifacts:
        return artifacts
    discovered: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        role = _role_for(path)
        if role:
            discovered.append({"path": str(path), "role": role})
    return discovered


def _role_for(path: Path) -> str | None:
    name = path.name
    if name == "dataset_profile.json":
        return "dataset_profile"
    if name == "labels.csv":
        return "recognition_manifest"
    if name == "data.yaml":
        return "detection_data_yaml"
    if name.endswith("predictions.json"):
        return "predictions"
    if name.endswith("results.json") or name == "run_report.json":
        return "evaluation_results"
    if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
        return "image_preview"
    if path.suffix.lower() in {".onnx", ".pth", ".pt"}:
        return "model"
    return None
