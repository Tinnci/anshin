from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

from .schema import PipelineConfig, pipeline_config_to_dict


def package_kaggle_kernel(config: PipelineConfig, *, project_dir: Path) -> Path:
    run_dir = _resolve(config.run.output_dir, project_dir)
    kernel_dir = run_dir / "kaggle" / "kernel"
    if kernel_dir.exists():
        shutil.rmtree(kernel_dir)
    kernel_dir.mkdir(parents=True, exist_ok=True)
    _copy_core_modules(project_dir, kernel_dir / "medlog_ocr_modules")
    task_json = pipeline_config_to_dict(config)
    task_json["run"]["output_dir"] = f"/kaggle/working/{config.run.name}"
    (kernel_dir / "pipeline_task.json").write_text(json.dumps(task_json, indent=2, ensure_ascii=False), encoding="utf-8")
    metadata = {
        "id": config.kaggle.kernel_id,
        "title": config.kaggle.title,
        "code_file": "kaggle_pipeline_entry.py",
        "language": "python",
        "kernel_type": "script",
        "is_private": True,
        "enable_gpu": config.kaggle.enable_gpu,
        "enable_tpu": config.kaggle.enable_tpu,
        "enable_internet": config.kaggle.enable_internet,
        "dataset_sources": list(config.kaggle.dataset_sources),
        "competition_sources": [],
        "keywords": ["seven-segment", "ocr", "pipeline"],
    }
    (kernel_dir / "kernel-metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    (kernel_dir / "kaggle_pipeline_entry.py").write_text(_entry_script(), encoding="utf-8")
    return kernel_dir


def push_kaggle_kernel(kernel_dir: str | Path, *, accelerator: str = "NvidiaTeslaT4") -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["kaggle", "kernels", "push", "-p", str(kernel_dir), "--accelerator", accelerator],
        check=False,
        capture_output=True,
        text=True,
    )


def fetch_kaggle_output(kernel_id: str, output_dir: str | Path) -> subprocess.CompletedProcess[str]:
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    return subprocess.run(
        ["kaggle", "kernels", "output", kernel_id, "-p", str(output_dir)],
        check=False,
        capture_output=True,
        text=True,
    )


def kaggle_status(kernel_id: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["kaggle", "kernels", "status", kernel_id], check=False, capture_output=True, text=True)


def _copy_core_modules(project_dir: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in [
        "pipeline",
        "train_fastvit_ctc.py",
        "ocr_model_eval.py",
        "run_candidate_evaluation.py",
        "evaluate_parseq_onnx.py",
        "dequantize_onnx.py",
        "fastvit_ctc.py",
        "light_svtr.py",
    ]:
        src = project_dir / name
        dst = output_dir / name
        if src.is_dir():
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        elif src.exists():
            shutil.copy2(src, dst)


def _entry_script() -> str:
    return '''from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "medlog_ocr_modules"))

from pipeline.schema import parse_pipeline_config
from pipeline.runners import run_pipeline


def main() -> int:
    task_path = ROOT / "pipeline_task.json"
    raw = json.loads(task_path.read_text(encoding="utf-8"))
    input_mounts = raw.get("kaggle", {}).get("input_mounts", {})
    for dataset_id, mount in input_mounts.items():
        if dataset_id in raw.get("datasets", {}):
            raw["datasets"][dataset_id]["root"] = mount
    config = parse_pipeline_config(raw, source_path=task_path)
    run_pipeline(config, project_dir=ROOT / "medlog_ocr_modules")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
'''


def _resolve(path: Path, project_dir: Path) -> Path:
    return path if path.is_absolute() else project_dir / path

