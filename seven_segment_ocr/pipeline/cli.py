from __future__ import annotations

import argparse
import json
from pathlib import Path

from .datasets import inspect_dataset, prepare_recognition_dataset
from .kaggle import fetch_kaggle_output, kaggle_status, package_kaggle_kernel, push_kaggle_kernel
from .runners import run_pipeline
from .schema import load_pipeline_config


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Seven-segment OCR pipeline")
    sub = parser.add_subparsers(dest="command", required=True)

    inspect_cmd = sub.add_parser("inspect")
    inspect_cmd.add_argument("--config", required=True)
    inspect_cmd.add_argument("--dataset", default=None)
    inspect_cmd.add_argument("--output-dir", default=None)

    run_cmd = sub.add_parser("run")
    run_cmd.add_argument("--config", required=True)
    run_cmd.add_argument("--project-dir", default=".")
    run_cmd.add_argument("--target", nargs="*", default=None)
    run_cmd.add_argument("--from-task", default=None)
    run_cmd.add_argument("--resume", action="store_true")
    run_cmd.add_argument("--no-cache", action="store_true")
    run_cmd.add_argument("--force", nargs="*", default=None)
    run_cmd.add_argument("--max-workers", type=int, default=None)

    prep_cmd = sub.add_parser("prepare-recognition")
    prep_cmd.add_argument("--config", required=True)
    prep_cmd.add_argument("--dataset", required=True)
    prep_cmd.add_argument("--output-dir", required=True)

    package_cmd = sub.add_parser("package-kaggle")
    package_cmd.add_argument("--config", required=True)
    package_cmd.add_argument("--project-dir", default=".")

    push_cmd = sub.add_parser("push-kaggle")
    push_cmd.add_argument("--kernel-dir", required=True)
    push_cmd.add_argument("--accelerator", default="NvidiaTeslaT4")

    status_cmd = sub.add_parser("status-kaggle")
    status_cmd.add_argument("--kernel-id", required=True)

    fetch_cmd = sub.add_parser("fetch-kaggle")
    fetch_cmd.add_argument("--kernel-id", required=True)
    fetch_cmd.add_argument("--output-dir", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "inspect":
        config = load_pipeline_config(args.config)
        dataset_id = args.dataset or next(iter(config.datasets))
        dataset = config.datasets[dataset_id]
        output_dir = Path(args.output_dir) if args.output_dir else Path(config.run.output_dir) / "data" / "previews" / dataset_id
        profile = inspect_dataset(dataset, output_dir=output_dir)
        print(json.dumps({"dataset": dataset_id, "profile": str(output_dir / "dataset_profile.json"), "sample_count": profile.get("sample_count") or profile.get("image_count")}, ensure_ascii=False))
        return 0
    if args.command == "prepare-recognition":
        config = load_pipeline_config(args.config)
        output = prepare_recognition_dataset(config.datasets[args.dataset], output_dir=args.output_dir)
        print(json.dumps({"output_dir": str(output), "labels": str(output / "labels.csv")}, ensure_ascii=False))
        return 0
    if args.command == "run":
        report = run_pipeline(
            load_pipeline_config(args.config),
            project_dir=Path(args.project_dir),
            targets=set(args.target or []) or None,
            from_task=args.from_task,
            resume=args.resume,
            use_cache=not args.no_cache,
            force=set(args.force or []),
            max_workers=args.max_workers,
        )
        print(json.dumps({"run": report["run"], "summary": report["summary"]}, ensure_ascii=False))
        return 0
    if args.command == "package-kaggle":
        kernel_dir = package_kaggle_kernel(load_pipeline_config(args.config), project_dir=Path(args.project_dir))
        print(json.dumps({"kernel_dir": str(kernel_dir)}, ensure_ascii=False))
        return 0
    if args.command == "push-kaggle":
        result = push_kaggle_kernel(args.kernel_dir, accelerator=args.accelerator)
        print(result.stdout or result.stderr)
        return result.returncode
    if args.command == "status-kaggle":
        result = kaggle_status(args.kernel_id)
        print(result.stdout or result.stderr)
        return result.returncode
    if args.command == "fetch-kaggle":
        result = fetch_kaggle_output(args.kernel_id, args.output_dir)
        print(result.stdout or result.stderr)
        return result.returncode
    raise RuntimeError(f"unknown command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
