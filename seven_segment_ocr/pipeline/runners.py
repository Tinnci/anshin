from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .cache import artifact_paths, cache_entry_is_valid, compute_task_hash, load_task_cache, save_task_cache
from .datasets import inspect_dataset, prepare_detection_dataset, prepare_recognition_dataset
from .events import EventWriter
from .graph import blocked_by_failure, dependency_closure, descendant_closure, ordered_task_ids, ready_task_ids, tasks_by_id
from .schema import DatasetConfig, PipelineConfig, TaskConfig, pipeline_config_to_dict, write_pipeline_json


def run_pipeline(
    config: PipelineConfig,
    *,
    project_dir: Path,
    targets: set[str] | None = None,
    from_task: str | None = None,
    resume: bool = False,
    use_cache: bool = True,
    force: set[str] | None = None,
    max_workers: int | None = None,
) -> dict[str, Any]:
    run_dir = _resolve(config.run.output_dir, project_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "logs").mkdir(exist_ok=True)
    previous_report = _read_previous_report(run_dir)
    write_pipeline_json(config, run_dir / "pipeline_task.json")
    if config.source_path and config.source_path.exists():
        shutil.copy2(config.source_path, run_dir / "pipeline_task.yaml")
    else:
        (run_dir / "pipeline_task.yaml").write_text(json.dumps(pipeline_config_to_dict(config), indent=2), encoding="utf-8")
    event_writer = EventWriter(run_dir / "events.jsonl", run_id=config.run.name)
    selected_ids = _select_task_ids(config.tasks, targets=targets, from_task=from_task, resume=resume, previous_report=previous_report)
    selected_order = ordered_task_ids(config.tasks, selected_ids)
    max_worker_count = max(1, int(max_workers or min(len(selected_ids) or 1, os.cpu_count() or 1)))
    force_ids = force or set()
    cache = load_task_cache(run_dir)
    cache_tasks: dict[str, Any] = cache.setdefault("tasks", {})
    by_id = tasks_by_id(config.tasks)
    completed: set[str] = set()
    failed: set[str] = set()
    skipped: set[str] = set()
    blocked: set[str] = set()
    running: dict[Future[list[dict[str, str]]], str] = {}
    artifacts: list[dict[str, str]] = []
    artifacts_by_task: dict[str, list[dict[str, str]]] = {}
    task_report: dict[str, dict[str, Any]] = {
        task_id: {
            "task_id": task_id,
            "task_type": by_id[task_id].type,
            "status": "pending",
            "artifacts": [],
        }
        for task_id in selected_order
    }
    start = time.time()

    def dependency_artifacts(task: TaskConfig) -> dict[str, list[Path]]:
        rows: dict[str, list[Path]] = {}
        for dep_id in task.depends_on:
            dep_artifacts = artifacts_by_task.get(dep_id)
            if dep_artifacts is None:
                dep_artifacts = cache_tasks.get(dep_id, {}).get("artifacts", [])
            rows[dep_id] = artifact_paths(dep_artifacts)
        return rows

    def dependency_state(task: TaskConfig) -> dict[str, Any]:
        rows: dict[str, Any] = {}
        for dep_id in task.depends_on:
            entry = cache_tasks.get(dep_id, {})
            rows[dep_id] = {
                "status": entry.get("status"),
                "input_hash": entry.get("input_hash"),
                "completed_at": entry.get("completed_at"),
            }
        return rows

    def mark_artifacts(task: TaskConfig, task_artifacts: list[dict[str, str]]) -> list[dict[str, str]]:
        owned = []
        for artifact in task_artifacts:
            row = dict(artifact)
            row.setdefault("task_id", task.id)
            row.setdefault("task_type", task.type)
            owned.append(row)
        return owned

    def execute_task(task: TaskConfig) -> list[dict[str, str]]:
        event_writer.task_started(task.id, task.type, {"params": task.params, "dataset": task.dataset})
        return _run_task(task, config, project_dir=project_dir, run_dir=run_dir, event_writer=event_writer)

    with ThreadPoolExecutor(max_workers=max_worker_count) as executor:
        while len(completed | failed | blocked) < len(selected_ids):
            made_progress_without_future = False
            if failed:
                newly_blocked = blocked_by_failure(config.tasks, selected_ids, failed) - blocked - completed - failed
                for task_id in ordered_task_ids(config.tasks, newly_blocked):
                    blocked.add(task_id)
                    task = by_id[task_id]
                    task_report[task_id]["status"] = "blocked"
                    task_report[task_id]["blocked_by"] = [dep for dep in task.depends_on if dep in failed or dep in blocked]
            if not failed:
                for task_id in ready_task_ids(config.tasks, selected_ids, completed, blocked, set(running.values())):
                    if len(running) >= max_worker_count:
                        break
                    task = by_id[task_id]
                    input_hash = compute_task_hash(
                        task,
                        config,
                        project_dir=project_dir,
                        dependency_artifacts=dependency_artifacts(task),
                        dependency_state=dependency_state(task),
                    )
                    task_report[task_id]["input_hash"] = input_hash
                    entry = cache_tasks.get(task_id)
                    if use_cache and task_id not in force_ids and cache_entry_is_valid(entry, input_hash):
                        cached_artifacts = mark_artifacts(task, list(entry.get("artifacts", [])))
                        artifacts_by_task[task_id] = cached_artifacts
                        artifacts.extend(cached_artifacts)
                        completed.add(task_id)
                        skipped.add(task_id)
                        task_report[task_id].update({"status": "skipped", "artifacts": cached_artifacts})
                        event_writer.task_skipped(
                            task.id,
                            task.type,
                            {"reason": "cache_hit", "input_hash": input_hash, "artifacts": cached_artifacts},
                        )
                        made_progress_without_future = True
                        continue
                    task_report[task_id]["status"] = "running"
                    running[executor.submit(execute_task, task)] = task_id
            if not running:
                if made_progress_without_future:
                    continue
                if failed:
                    break
                remaining = selected_ids - completed - failed - blocked
                for task_id in ordered_task_ids(config.tasks, remaining):
                    blocked.add(task_id)
                    task_report[task_id]["status"] = "blocked"
                    task_report[task_id]["blocked_by"] = by_id[task_id].depends_on
                break
            done, _ = wait(running, return_when=FIRST_COMPLETED)
            for future in done:
                task_id = running.pop(future)
                task = by_id[task_id]
                try:
                    task_artifacts = mark_artifacts(task, future.result())
                    for artifact in task_artifacts:
                        artifacts.append(artifact)
                        event_writer.artifact(task.id, task.type, artifact["path"], role=artifact["role"], mime=artifact.get("mime"))
                    artifacts_by_task[task_id] = task_artifacts
                    completed.add(task_id)
                    task_report[task_id].update({"status": "completed", "artifacts": task_artifacts})
                    cache_tasks[task_id] = {
                        "status": "completed",
                        "input_hash": task_report[task_id].get("input_hash"),
                        "artifacts": task_artifacts,
                        "completed_at": datetime.now(timezone.utc).isoformat(),
                    }
                    save_task_cache(run_dir, cache)
                    event_writer.task_finished(task.id, task.type, {"status": "ok", "artifacts": task_artifacts})
                except Exception as exc:
                    failed.add(task_id)
                    task_report[task_id].update({"status": "failed", "error_type": type(exc).__name__, "message": str(exc)})
                    cache_tasks[task_id] = {
                        "status": "failed",
                        "input_hash": task_report[task_id].get("input_hash"),
                        "artifacts": [],
                        "completed_at": datetime.now(timezone.utc).isoformat(),
                    }
                    save_task_cache(run_dir, cache)
                    event_writer.task_failed(task.id, task.type, exc)

    for task_id in skipped:
        cache_tasks[task_id]["status"] = "skipped"
    save_task_cache(run_dir, cache)
    report = {
        "schema_version": 1,
        "run": {"name": config.run.name, "output_dir": str(run_dir)},
        "execution": {
            "selected": {
                "targets": sorted(targets or []),
                "from_task": from_task,
                "resume": resume,
            },
            "use_cache": use_cache,
            "force": sorted(force_ids),
            "max_workers": max_worker_count,
        },
        "summary": {
            "completed": len(completed - skipped),
            "skipped": len(skipped),
            "failed": len(failed),
            "blocked": len(blocked),
            "duration_seconds": round(time.time() - start, 3),
        },
        "selected_tasks": selected_order,
        "completed_tasks": ordered_task_ids(config.tasks, completed - skipped),
        "skipped_tasks": ordered_task_ids(config.tasks, skipped),
        "failed_tasks": ordered_task_ids(config.tasks, failed),
        "blocked_tasks": ordered_task_ids(config.tasks, blocked),
        "tasks": task_report,
        "artifacts": artifacts,
    }
    (run_dir / "run_report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    return report


def _select_task_ids(
    tasks: list[TaskConfig],
    *,
    targets: set[str] | None,
    from_task: str | None,
    resume: bool,
    previous_report: dict[str, Any] | None,
) -> set[str]:
    all_ids = set(tasks_by_id(tasks))
    if targets:
        return dependency_closure(tasks, targets)
    if from_task:
        return descendant_closure(tasks, {from_task})
    if resume and previous_report:
        retry_ids = set(previous_report.get("failed_tasks", [])) | set(previous_report.get("blocked_tasks", []))
        if retry_ids:
            return descendant_closure(tasks, retry_ids)
    return all_ids


def _read_previous_report(run_dir: Path) -> dict[str, Any] | None:
    path = run_dir / "run_report.json"
    if not path.exists():
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return raw if isinstance(raw, dict) else None


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
