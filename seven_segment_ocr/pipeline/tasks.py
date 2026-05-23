from __future__ import annotations

from .schema import SUPPORTED_TASK_TYPES


DATASET_TASKS = {
    "dataset.inspect",
    "dataset.prepare_recognition",
    "dataset.prepare_detection",
}

TRAINING_TASKS = {
    "train.fastvit_ctc",
    "train.light_svtr_domain",
    "train.light_crnn",
}

EVALUATION_TASKS = {"eval.ocr_candidates"}
INFERENCE_TASKS = {"infer.single_image"}
EXPORT_TASKS = {"export.android_assets"}


def supported_task_types() -> list[str]:
    return sorted(SUPPORTED_TASK_TYPES)


def task_group(task_type: str) -> str:
    if task_type in DATASET_TASKS:
        return "dataset"
    if task_type in TRAINING_TASKS:
        return "training"
    if task_type in EVALUATION_TASKS:
        return "evaluation"
    if task_type in INFERENCE_TASKS:
        return "inference"
    if task_type in EXPORT_TASKS:
        return "export"
    return "unknown"
