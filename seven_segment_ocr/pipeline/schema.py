from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


SUPPORTED_DATASET_KINDS = {"image_text", "voc", "yolo"}
SUPPORTED_TASK_TYPES = {
    "dataset.inspect",
    "dataset.prepare_recognition",
    "dataset.prepare_detection",
    "train.fastvit_ctc",
    "train.light_svtr_domain",
    "train.light_crnn",
    "eval.ocr_candidates",
    "infer.single_image",
    "export.android_assets",
}


class PipelineConfigError(ValueError):
    """Raised when a pipeline task config is invalid."""


@dataclass(frozen=True)
class RunConfig:
    name: str
    output_dir: Path
    seed: int = 42
    environment: str = "local"


@dataclass(frozen=True)
class DatasetConfig:
    id: str
    kind: str
    root: Path
    labels: str | None = None
    image_column: str = "filename"
    label_column: str = "label"
    split_column: str | None = "split"
    data_yaml: str | None = None
    source: str | None = None
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class TaskConfig:
    id: str
    type: str
    dataset: str | None = None
    depends_on: tuple[str, ...] = ()
    params: dict[str, Any] = field(default_factory=dict)
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class KaggleConfig:
    kernel_id: str = "tiiann/seven-segment-ocr-pipeline"
    title: str = "Seven Segment OCR Pipeline"
    accelerator: str = "NvidiaTeslaT4"
    enable_gpu: bool = True
    enable_tpu: bool = False
    enable_internet: bool = True
    dataset_sources: tuple[str, ...] = ()
    input_mounts: dict[str, str] = field(default_factory=dict)
    raw: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class PipelineConfig:
    schema_version: int
    run: RunConfig
    datasets: dict[str, DatasetConfig]
    tasks: list[TaskConfig]
    kaggle: KaggleConfig
    source_path: Path | None = None
    raw: dict[str, Any] = field(default_factory=dict)


def load_pipeline_config(path: str | Path) -> PipelineConfig:
    config_path = Path(path)
    raw = _read_config(config_path)
    config = parse_pipeline_config(raw, source_path=config_path)
    return config


def parse_pipeline_config(raw: dict[str, Any], *, source_path: Path | None = None) -> PipelineConfig:
    if int(raw.get("schema_version", 0)) != 1:
        raise PipelineConfigError("schema_version must be 1")
    run_raw = _require_mapping(raw, "run")
    run_name = str(run_raw.get("name") or "").strip()
    if not run_name:
        raise PipelineConfigError("run.name is required")
    output_dir = Path(str(run_raw.get("output_dir") or f"runs/{run_name}"))
    run = RunConfig(
        name=run_name,
        output_dir=output_dir,
        seed=int(run_raw.get("seed", 42)),
        environment=str(run_raw.get("environment", "local")),
    )

    datasets_raw = _require_mapping(raw, "datasets")
    datasets: dict[str, DatasetConfig] = {}
    for dataset_id, dataset_raw_any in datasets_raw.items():
        if not isinstance(dataset_raw_any, dict):
            raise PipelineConfigError(f"datasets.{dataset_id} must be a mapping")
        kind = str(dataset_raw_any.get("kind") or "")
        if kind not in SUPPORTED_DATASET_KINDS:
            raise PipelineConfigError(f"unsupported dataset kind for {dataset_id}: {kind}")
        root_value = dataset_raw_any.get("root")
        if not root_value:
            raise PipelineConfigError(f"datasets.{dataset_id}.root is required")
        datasets[str(dataset_id)] = DatasetConfig(
            id=str(dataset_id),
            kind=kind,
            root=Path(str(root_value)),
            labels=_optional_str(dataset_raw_any.get("labels")),
            image_column=str(dataset_raw_any.get("image_column", "filename")),
            label_column=str(dataset_raw_any.get("label_column", "label")),
            split_column=_optional_str(dataset_raw_any.get("split_column", "split")),
            data_yaml=_optional_str(dataset_raw_any.get("data_yaml")),
            source=_optional_str(dataset_raw_any.get("source")),
            raw=dict(dataset_raw_any),
        )

    tasks_raw = raw.get("tasks")
    if not isinstance(tasks_raw, list) or not tasks_raw:
        raise PipelineConfigError("tasks must be a non-empty list")
    tasks: list[TaskConfig] = []
    seen: set[str] = set()
    for task_raw_any in tasks_raw:
        if not isinstance(task_raw_any, dict):
            raise PipelineConfigError("each task must be a mapping")
        task_id = str(task_raw_any.get("id") or "").strip()
        if not task_id:
            raise PipelineConfigError("task.id is required")
        if task_id in seen:
            raise PipelineConfigError(f"duplicate task id: {task_id}")
        task_type = str(task_raw_any.get("type") or "")
        if task_type not in SUPPORTED_TASK_TYPES:
            raise PipelineConfigError(f"unsupported task type for {task_id}: {task_type}")
        dataset_id = _optional_str(task_raw_any.get("dataset"))
        if dataset_id and dataset_id not in datasets:
            raise PipelineConfigError(f"task {task_id} references unknown dataset: {dataset_id}")
        depends_on = tuple(str(dep) for dep in task_raw_any.get("depends_on", []) or [])
        tasks.append(
            TaskConfig(
                id=task_id,
                type=task_type,
                dataset=dataset_id,
                depends_on=depends_on,
                params=dict(task_raw_any.get("params") or {}),
                raw=dict(task_raw_any),
            )
        )
        seen.add(task_id)
    _validate_task_graph(tasks)

    kaggle_raw = dict(raw.get("kaggle") or {})
    kaggle = KaggleConfig(
        kernel_id=str(kaggle_raw.get("kernel_id", "tiiann/seven-segment-ocr-pipeline")),
        title=str(kaggle_raw.get("title", "Seven Segment OCR Pipeline")),
        accelerator=str(kaggle_raw.get("accelerator", "NvidiaTeslaT4")),
        enable_gpu=bool(kaggle_raw.get("enable_gpu", True)),
        enable_tpu=bool(kaggle_raw.get("enable_tpu", False)),
        enable_internet=bool(kaggle_raw.get("enable_internet", True)),
        dataset_sources=tuple(str(item) for item in kaggle_raw.get("dataset_sources", []) or []),
        input_mounts=dict(kaggle_raw.get("input_mounts") or {}),
        raw=kaggle_raw,
    )
    return PipelineConfig(
        schema_version=1,
        run=run,
        datasets=datasets,
        tasks=tasks,
        kaggle=kaggle,
        source_path=source_path,
        raw=dict(raw),
    )


def pipeline_config_to_dict(config: PipelineConfig) -> dict[str, Any]:
    return {
        "schema_version": config.schema_version,
        "run": {
            "name": config.run.name,
            "output_dir": str(config.run.output_dir),
            "seed": config.run.seed,
            "environment": config.run.environment,
        },
        "datasets": {
            key: {k: v for k, v in {
                "kind": dataset.kind,
                "root": str(dataset.root),
                "labels": dataset.labels,
                "image_column": dataset.image_column,
                "label_column": dataset.label_column,
                "split_column": dataset.split_column,
                "data_yaml": dataset.data_yaml,
                "source": dataset.source,
            }.items() if v is not None}
            for key, dataset in config.datasets.items()
        },
        "tasks": [
            {k: v for k, v in {
                "id": task.id,
                "type": task.type,
                "dataset": task.dataset,
                "depends_on": list(task.depends_on),
                "params": task.params,
            }.items() if v not in (None, [], {})}
            for task in config.tasks
        ],
        "kaggle": {
            "kernel_id": config.kaggle.kernel_id,
            "title": config.kaggle.title,
            "accelerator": config.kaggle.accelerator,
            "enable_gpu": config.kaggle.enable_gpu,
            "enable_tpu": config.kaggle.enable_tpu,
            "enable_internet": config.kaggle.enable_internet,
            "dataset_sources": list(config.kaggle.dataset_sources),
            "input_mounts": config.kaggle.input_mounts,
        },
    }


def write_pipeline_json(config: PipelineConfig, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(pipeline_config_to_dict(config), indent=2, ensure_ascii=False), encoding="utf-8")


def _read_config(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        raw = json.loads(text)
    else:
        raw = yaml.safe_load(text)
    if not isinstance(raw, dict):
        raise PipelineConfigError("pipeline config must be a mapping")
    return raw


def _require_mapping(raw: dict[str, Any], key: str) -> dict[str, Any]:
    value = raw.get(key)
    if not isinstance(value, dict):
        raise PipelineConfigError(f"{key} must be a mapping")
    return value


def _optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    return text if text else None


def _validate_task_graph(tasks: list[TaskConfig]) -> None:
    by_id = {task.id: task for task in tasks}
    for task in tasks:
        for dep in task.depends_on:
            if dep not in by_id:
                raise PipelineConfigError(f"task {task.id} depends on unknown task: {dep}")

    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(task_id: str) -> None:
        if task_id in visited:
            return
        if task_id in visiting:
            raise PipelineConfigError(f"task dependency cycle includes: {task_id}")
        visiting.add(task_id)
        for dep in by_id[task_id].depends_on:
            visit(dep)
        visiting.remove(task_id)
        visited.add(task_id)

    for task in tasks:
        visit(task.id)

