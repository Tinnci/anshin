from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from .schema import PipelineConfig, TaskConfig, pipeline_config_to_dict

CACHE_VERSION = 1


def hash_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compute_task_hash(
    task: TaskConfig,
    config: PipelineConfig,
    *,
    project_dir: Path,
    dependency_artifacts: dict[str, list[str | Path]] | None = None,
    dependency_state: dict[str, Any] | None = None,
) -> str:
    dataset = config.datasets.get(task.dataset) if task.dataset else None
    payload: dict[str, Any] = {
        "cache_version": CACHE_VERSION,
        "schema_version": config.schema_version,
        "task": {
            "id": task.id,
            "type": task.type,
            "dataset": task.dataset,
            "depends_on": list(task.depends_on),
            "params": task.params,
        },
        "dataset": None if dataset is None else {
            "id": dataset.id,
            "kind": dataset.kind,
            "root": str(_resolve(dataset.root, project_dir)),
            "labels": dataset.labels,
            "image_column": dataset.image_column,
            "label_column": dataset.label_column,
            "split_column": dataset.split_column,
            "data_yaml": dataset.data_yaml,
            "source": dataset.source,
        },
        "dependencies": {},
        "dependency_state": dependency_state or {},
    }
    for dep_id, paths in sorted((dependency_artifacts or {}).items()):
        dep_rows = []
        for path_value in paths:
            path = Path(path_value)
            dep_rows.append(
                {
                    "path": str(path),
                    "exists": path.exists(),
                    "sha256": hash_file(path) if path.exists() and path.is_file() else None,
                }
            )
        payload["dependencies"][dep_id] = dep_rows
    return hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()


def load_task_cache(run_dir: str | Path) -> dict[str, Any]:
    path = Path(run_dir) / ".task_cache.json"
    if not path.exists():
        return {"schema_version": CACHE_VERSION, "tasks": {}}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"schema_version": CACHE_VERSION, "tasks": {}}
    if not isinstance(raw, dict):
        return {"schema_version": CACHE_VERSION, "tasks": {}}
    raw.setdefault("schema_version", CACHE_VERSION)
    raw.setdefault("tasks", {})
    return raw


def save_task_cache(run_dir: str | Path, cache: dict[str, Any]) -> None:
    path = Path(run_dir) / ".task_cache.json"
    path.write_text(json.dumps(cache, indent=2, ensure_ascii=False, sort_keys=True), encoding="utf-8")


def cache_entry_is_valid(entry: dict[str, Any] | None, input_hash: str) -> bool:
    if not isinstance(entry, dict):
        return False
    if entry.get("input_hash") != input_hash:
        return False
    if entry.get("status") not in {"completed", "skipped"}:
        return False
    artifacts = entry.get("artifacts", [])
    if not isinstance(artifacts, list):
        return False
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            return False
        path = Path(str(artifact.get("path", "")))
        if not path.exists():
            return False
    return True


def artifact_paths(artifacts: list[dict[str, Any]] | None) -> list[Path]:
    paths: list[Path] = []
    for artifact in artifacts or []:
        if not isinstance(artifact, dict):
            continue
        path = Path(str(artifact.get("path", "")))
        if path:
            paths.append(path)
    return paths


def _resolve(path: Path, project_dir: Path) -> Path:
    return path if path.is_absolute() else project_dir / path
