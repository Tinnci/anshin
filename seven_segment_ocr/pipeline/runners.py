from __future__ import annotations

import json
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

from .datasets import inspect_dataset, prepare_detection_dataset, prepare_recognition_dataset
from .events import EventWriter
from .schema import DatasetConfig, PipelineConfig, TaskConfig, pipeline_config_to_dict, write_pipeline_json


def run_pipeline(config: PipelineConfig, *, project_dir: Path) -> dict[str, Any]:
    run_dir = _resolve(config.run.output_dir, project_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "logs").mkdir(exist_ok=True)
    write_pipeline_json(config, run_dir / "pipeline_task.json")
    if config.source_path and config.source_path.exists():
        shutil.copy2(config.source_path, run_dir / "pipeline_task.yaml")
    else:
        (run_dir / "pipeline_task.yaml").write_text(json.dumps(pipeline_config_to_dict(config), indent=2), encoding="utf-8")
    event_writer = EventWriter(run_dir / "events.jsonl", run_id=config.run.name)
    completed: list[str] = []
    failed: list[str] = []
    artifacts: list[dict[str, str]] = []
    start = time.time()
    for task in _topological_tasks(config.tasks):
        try:
            event_writer.task_started(task.id, task.type, {"params": task.params, "dataset": task.dataset})
            task_artifacts = _run_task(task, config, project_dir=project_dir, run_dir=run_dir, event_writer=event_writer)
            for artifact in task_artifacts:
                artifacts.append(artifact)
                event_writer.artifact(task.id, task.type, artifact["path"], role=artifact["role"], mime=artifact.get("mime"))
            completed.append(task.id)
            event_writer.task_finished(task.id, task.type, {"status": "ok", "artifacts": task_artifacts})
        except Exception as exc:
            failed.append(task.id)
            event_writer.task_failed(task.id, task.type, exc)
            break
    report = {
        "schema_version": 1,
        "run": {"name": config.run.name, "output_dir": str(run_dir)},
        "summary": {
            "completed": len(completed),
            "failed": len(failed),
            "duration_seconds": round(time.time() - start, 3),
        },
        "completed_tasks": completed,
        "failed_tasks": failed,
        "artifacts": artifacts,
    }
    (run_dir / "run_report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    return report


def _run_task(
    task: TaskConfig,
    config: PipelineConfig,
    *,
    project_dir: Path,
    run_dir: Path,
    event_writer: EventWriter,
) -> list[dict[str, str]]:
    if task.type == "dataset.inspect":
        dataset = _require_dataset(config, task)
        output_dir = run_dir / "data" / "previews" / (task.dataset or task.id)
        profile = inspect_dataset(_dataset_to_raw(dataset, project_dir), output_dir=output_dir)
        for warning in profile.get("warnings", []):
            event_writer.warning(task.id, task.type, code=str(warning.get("code", "dataset_warning")), message=str(warning.get("message", warning)))
        if profile.get("preview"):
            event_writer.sample_preview(task.id, task.type, str(profile["preview"]), {"dataset": task.dataset})
        return [{"path": str(output_dir / "dataset_profile.json"), "role": "dataset_profile", "mime": "application/json"}]
    if task.type == "dataset.prepare_recognition":
        dataset = _require_dataset(config, task)
        output_dir = run_dir / "data" / "prepared" / (task.dataset or task.id)
        prepare_recognition_dataset(_dataset_to_raw(dataset, project_dir), output_dir=output_dir)
        return [{"path": str(output_dir / "labels.csv"), "role": "recognition_manifest", "mime": "text/csv"}]
    if task.type == "dataset.prepare_detection":
        dataset = _require_dataset(config, task)
        output_dir = run_dir / "data" / "prepared" / (task.dataset or task.id)
        prepare_detection_dataset(_dataset_to_raw(dataset, project_dir), output_dir=output_dir)
        return [{"path": str(output_dir / "data.yaml"), "role": "detection_data_yaml", "mime": "application/x-yaml"}]
    if task.type == "train.fastvit_ctc":
        return _run_subprocess_task(
            [
                sys.executable,
                "train_fastvit_ctc.py",
                "--dataset",
                str(_resolve_dataset_root(config, task, project_dir, run_dir)),
                "--output-dir",
                str(run_dir / "train" / task.id),
                *(_fastvit_args(task.params)),
            ],
            cwd=project_dir,
            run_dir=run_dir,
            task=task,
            event_writer=event_writer,
            artifact_candidates=[(run_dir / "train" / task.id / "fastvit_t8_ctc_report.json", "training_report")],
        )
    if task.type == "train.light_svtr_domain":
        params = task.params
        return _run_subprocess_task(
            [
                sys.executable,
                "kaggle_domain_adaptation_kernel/kaggle_domain_adaptation.py",
                "--output-dir",
                str(run_dir / "train" / task.id),
                "--synthetic-samples",
                str(params.get("synthetic_samples", params.get("synthetic-samples", 2000))),
                "--epochs",
                str(params.get("epochs", 1)),
                "--batch-size",
                str(params.get("batch_size", params.get("batch-size", 16))),
                "--model-variant",
                str(params.get("model_variant", params.get("model-variant", "tiny"))),
            ],
            cwd=project_dir,
            run_dir=run_dir,
            task=task,
            event_writer=event_writer,
            artifact_candidates=[(run_dir / "train" / task.id / "evaluation_report.json", "evaluation_report")],
        )
    if task.type == "train.light_crnn":
        dataset_root = _resolve_dataset_root(config, task, project_dir, run_dir)
        return _run_subprocess_task(
            [
                sys.executable,
                "train.py",
                "--mode",
                str(task.params.get("mode", "crnn")),
                "--data-dir",
                str(dataset_root),
                "--output-dir",
                str(run_dir / "train" / task.id),
                "--epochs",
                str(task.params.get("epochs", 1)),
                "--batch-size",
                str(task.params.get("batch_size", 8)),
            ],
            cwd=project_dir,
            run_dir=run_dir,
            task=task,
            event_writer=event_writer,
            artifact_candidates=[(run_dir / "train" / task.id, "checkpoint_dir")],
        )
    if task.type == "eval.ocr_candidates":
        dataset_root = _resolve_dataset_root(config, task, project_dir, run_dir)
        output = run_dir / "eval" / f"{task.id}_results.json"
        table = run_dir / "eval" / f"{task.id}_results.txt"
        return _run_subprocess_task(
            [
                sys.executable,
                "run_candidate_evaluation.py",
                "--dataset",
                str(dataset_root),
                "--candidate-config",
                str(_resolve(Path(str(task.params.get("candidate_config", "model_candidates.json"))), project_dir)),
                "--output",
                str(output),
                "--table-output",
                str(table),
                "--limit",
                str(task.params.get("limit", 20)),
                "--batch-size",
                str(task.params.get("batch_size", 1)),
            ],
            cwd=project_dir,
            run_dir=run_dir,
            task=task,
            event_writer=event_writer,
            artifact_candidates=[(output, "evaluation_results"), (table, "evaluation_table")],
        )
    if task.type == "infer.single_image":
        return _run_inference_task(task, project_dir=project_dir, run_dir=run_dir)
    if task.type == "export.android_assets":
        return _run_export_android_assets(task, project_dir=project_dir, run_dir=run_dir)
    raise ValueError(f"unsupported task type: {task.type}")


def _run_subprocess_task(
    command: list[str],
    *,
    cwd: Path,
    run_dir: Path,
    task: TaskConfig,
    event_writer: EventWriter,
    artifact_candidates: list[tuple[Path, str]],
) -> list[dict[str, str]]:
    logs_dir = run_dir / "logs"
    logs_dir.mkdir(parents=True, exist_ok=True)
    stdout_path = logs_dir / f"{task.id}.stdout.log"
    stderr_path = logs_dir / f"{task.id}.stderr.log"
    with subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1) as proc:
        stdout_lines: list[str] = []
        stderr_lines: list[str] = []
        assert proc.stdout is not None
        for line in proc.stdout:
            stdout_lines.append(line)
            _emit_metric_if_json(line, task, event_writer)
        assert proc.stderr is not None
        stderr_lines.extend(proc.stderr.readlines())
        code = proc.wait()
    stdout_path.write_text("".join(stdout_lines), encoding="utf-8")
    stderr_path.write_text("".join(stderr_lines), encoding="utf-8")
    if code != 0:
        raise RuntimeError(f"task {task.id} failed with exit code {code}; see {stderr_path}")
    artifacts = [
        {"path": str(stdout_path), "role": "stdout_log", "mime": "text/plain"},
        {"path": str(stderr_path), "role": "stderr_log", "mime": "text/plain"},
    ]
    for path, role in artifact_candidates:
        if path.exists():
            artifacts.append({"path": str(path), "role": role})
    return artifacts


def _run_inference_task(task: TaskConfig, *, project_dir: Path, run_dir: Path) -> list[dict[str, str]]:
    from ocr_model_eval import evaluate_onnx_model, LabeledSample

    params = task.params
    image_path = _resolve(Path(str(params["image"])), project_dir)
    model_path = _resolve(Path(str(params["model"])), project_dir)
    output_dir = run_dir / "inference"
    output_dir.mkdir(parents=True, exist_ok=True)
    result = evaluate_onnx_model(
        str(params.get("model_id", model_path.stem)),
        model_path,
        [LabeledSample(image_path=image_path, label=str(params.get("label", "")))],
        limit=1,
        warmup=0,
        adapter=str(params.get("adapter", "seven_segment_ctc")),
    )
    output = output_dir / f"{task.id}_predictions.json"
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    return [{"path": str(output), "role": "predictions", "mime": "application/json"}]


def _run_export_android_assets(task: TaskConfig, *, project_dir: Path, run_dir: Path) -> list[dict[str, str]]:
    output_dir = run_dir / "export"
    output_dir.mkdir(parents=True, exist_ok=True)
    artifacts = []
    for src_value in task.params.get("models", []):
        src = _resolve(Path(str(src_value)), project_dir)
        dst = output_dir / src.name
        shutil.copy2(src, dst)
        artifacts.append({"path": str(dst), "role": "android_asset"})
    return artifacts


def _emit_metric_if_json(line: str, task: TaskConfig, event_writer: EventWriter) -> None:
    text = line.strip()
    if not (text.startswith("{") and text.endswith("}")):
        return
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return
    if isinstance(payload, dict) and ("epoch" in payload or "stage" in payload or "val" in payload):
        event_writer.metric(task.id, task.type, payload)


def _fastvit_args(params: dict[str, Any]) -> list[str]:
    args = [
        "--stage1-epochs",
        str(params.get("stage1_epochs", params.get("stage1-epochs", 1))),
        "--stage2-epochs",
        str(params.get("stage2_epochs", params.get("stage2-epochs", 1))),
        "--batch-size",
        str(params.get("batch_size", params.get("batch-size", 8))),
    ]
    if not bool(params.get("pretrained", True)):
        args.append("--no-pretrained")
    return args


def _resolve_dataset_root(config: PipelineConfig, task: TaskConfig, project_dir: Path, run_dir: Path) -> Path:
    if task.dataset:
        prepared = run_dir / "data" / "prepared" / task.dataset
        if prepared.exists():
            return prepared
        return _resolve(config.datasets[task.dataset].root, project_dir)
    dataset_param = task.params.get("dataset")
    if dataset_param:
        return _resolve(Path(str(dataset_param)), project_dir)
    raise ValueError(f"task {task.id} requires a dataset")


def _require_dataset(config: PipelineConfig, task: TaskConfig) -> DatasetConfig:
    if not task.dataset:
        raise ValueError(f"task {task.id} requires dataset")
    return config.datasets[task.dataset]


def _dataset_to_raw(dataset: DatasetConfig, project_dir: Path) -> dict[str, Any]:
    return {
        "kind": dataset.kind,
        "root": str(_resolve(dataset.root, project_dir)),
        "labels": dataset.labels,
        "image_column": dataset.image_column,
        "label_column": dataset.label_column,
        "split_column": dataset.split_column,
        "data_yaml": dataset.data_yaml,
        "source": dataset.source,
    }


def _resolve(path: Path, project_dir: Path) -> Path:
    return path if path.is_absolute() else project_dir / path


def _topological_tasks(tasks: list[TaskConfig]) -> list[TaskConfig]:
    by_id = {task.id: task for task in tasks}
    ordered: list[TaskConfig] = []
    seen: set[str] = set()

    def visit(task: TaskConfig) -> None:
        if task.id in seen:
            return
        for dep in task.depends_on:
            visit(by_id[dep])
        seen.add(task.id)
        ordered.append(task)

    for task in tasks:
        visit(task)
    return ordered
