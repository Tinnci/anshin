import csv
import json
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml
from PIL import Image


class PipelineCoreTest(unittest.TestCase):
    def _make_image(self, path: Path, size=(32, 16)) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", size, (12, 34, 56)).save(path)

    def test_schema_loads_defaults_and_rejects_cycle(self):
        from pipeline.schema import PipelineConfigError, load_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config_path = root / "pipeline_task.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "smoke"},
                        "datasets": {
                            "rec": {
                                "kind": "image_text",
                                "root": "dataset",
                                "labels": "labels.csv",
                            }
                        },
                        "tasks": [
                            {"id": "inspect", "type": "dataset.inspect", "dataset": "rec"}
                        ],
                    }
                ),
                encoding="utf-8",
            )

            config = load_pipeline_config(config_path)
            self.assertEqual(config.run.name, "smoke")
            self.assertEqual(config.run.output_dir, Path("runs/smoke"))
            self.assertEqual(config.datasets["rec"].kind, "image_text")

            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "bad"},
                        "datasets": {"rec": {"kind": "image_text", "root": "dataset"}},
                        "tasks": [
                            {
                                "id": "a",
                                "type": "dataset.inspect",
                                "dataset": "rec",
                                "depends_on": ["b"],
                            },
                            {
                                "id": "b",
                                "type": "dataset.inspect",
                                "dataset": "rec",
                                "depends_on": ["a"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaises(PipelineConfigError):
                load_pipeline_config(config_path)

    def test_event_writer_outputs_jsonl_with_failure_payload(self):
        from pipeline.events import EventWriter

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "events.jsonl"
            writer = EventWriter(path, run_id="run1")
            writer.task_started("inspect", "dataset.inspect", {"dataset": "rec"})
            writer.task_failed("inspect", "dataset.inspect", RuntimeError("boom"))

            rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

        self.assertEqual([row["event"] for row in rows], ["task_started", "task_failed"])
        self.assertEqual(rows[1]["payload"]["error_type"], "RuntimeError")
        self.assertEqual(rows[1]["payload"]["message"], "boom")

    def test_dataset_inspect_and_prepare_recognition_support_image_text_voc_and_yolo(self):
        from pipeline.datasets import inspect_dataset, prepare_recognition_dataset

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)

            rec = root / "hf"
            self._make_image(rec / "images" / "a.png")
            self._make_image(rec / "images" / "b.png")
            (rec / "labels.csv").write_text(
                "image_path,text,split\nimages/a.png,12.3,train\nimages/b.png,-45,val\n",
                encoding="utf-8",
            )
            rec_profile = inspect_dataset(
                {
                    "kind": "image_text",
                    "root": str(rec),
                    "labels": "labels.csv",
                    "image_column": "image_path",
                    "label_column": "text",
                    "split_column": "split",
                },
                output_dir=root / "out" / "rec",
            )
            prepared = prepare_recognition_dataset(
                {
                    "kind": "image_text",
                    "root": str(rec),
                    "labels": "labels.csv",
                    "image_column": "image_path",
                    "label_column": "text",
                    "split_column": "split",
                },
                output_dir=root / "prepared",
            )
            with (prepared / "labels.csv").open(newline="", encoding="utf-8") as handle:
                normalized_rows = list(csv.DictReader(handle))
            prepared_image_exists = (prepared / "images" / "a.png").exists()

            voc = root / "voc"
            self._make_image(voc / "images" / "device.jpg")
            annotation = ET.Element("annotation")
            ET.SubElement(annotation, "filename").text = "device.jpg"
            size = ET.SubElement(annotation, "size")
            ET.SubElement(size, "width").text = "32"
            ET.SubElement(size, "height").text = "16"
            obj = ET.SubElement(annotation, "object")
            ET.SubElement(obj, "name").text = "bp_monitor"
            box = ET.SubElement(obj, "bndbox")
            for key, value in {"xmin": "1", "ymin": "2", "xmax": "20", "ymax": "14"}.items():
                ET.SubElement(box, key).text = value
            (voc / "ann").mkdir()
            ET.ElementTree(annotation).write(voc / "ann" / "device.xml")
            voc_profile = inspect_dataset(
                {"kind": "voc", "root": str(voc)},
                output_dir=root / "out" / "voc",
            )

            yolo = root / "yolo"
            self._make_image(yolo / "train" / "images" / "d.jpg")
            (yolo / "train" / "labels").mkdir(parents=True)
            (yolo / "train" / "labels" / "d.txt").write_text(
                "7 0.5 0.5 0.2 0.2\n",
                encoding="utf-8",
            )
            (yolo / "data.yaml").write_text(
                "train: train/images\nval: train/images\nnc: 8\nnames: ['0','1','2','3','4','5','6','66']\n",
                encoding="utf-8",
            )
            yolo_profile = inspect_dataset(
                {"kind": "yolo", "root": str(yolo), "data_yaml": "data.yaml"},
                output_dir=root / "out" / "yolo",
            )

            self.assertEqual(rec_profile["sample_count"], 2)
            self.assertEqual(rec_profile["label_characters"], "-.12345")
            self.assertEqual(normalized_rows[0]["filename"], "images/a.png")
            self.assertEqual(normalized_rows[0]["label"], "12.3")
            self.assertEqual(normalized_rows[0]["source"], "hf")
            self.assertTrue(prepared_image_exists)
            self.assertEqual(voc_profile["object_counts"], {"bp_monitor": 1})
            self.assertEqual(yolo_profile["class_counts"]["66"], 1)
            self.assertTrue(Path(yolo_profile["preview"]).exists())
            self.assertTrue(any(warning["code"] == "suspicious_yolo_class" for warning in yolo_profile["warnings"]))

    def test_runner_executes_inspect_and_prepare_with_events(self):
        from pipeline.runners import run_pipeline
        from pipeline.schema import load_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rec = root / "dataset"
            self._make_image(rec / "images" / "a.png")
            (rec / "labels.csv").write_text("image_path,text\nimages/a.png,123\n", encoding="utf-8")
            config_path = root / "pipeline_task.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "run", "output_dir": str(root / "runs" / "run")},
                        "datasets": {
                            "rec": {
                                "kind": "image_text",
                                "root": str(rec),
                                "labels": "labels.csv",
                                "image_column": "image_path",
                                "label_column": "text",
                            }
                        },
                        "tasks": [
                            {"id": "inspect", "type": "dataset.inspect", "dataset": "rec"},
                            {
                                "id": "prepare",
                                "type": "dataset.prepare_recognition",
                                "dataset": "rec",
                                "depends_on": ["inspect"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            report = run_pipeline(load_pipeline_config(config_path), project_dir=Path.cwd())
            events = [
                json.loads(line)
                for line in (root / "runs" / "run" / "events.jsonl").read_text(encoding="utf-8").splitlines()
            ]

            self.assertEqual(report["summary"]["completed"], 2)
            self.assertEqual(events[0]["event"], "task_started")
            self.assertIn("artifact", {event["event"] for event in events})
            self.assertIn("sample_preview", {event["event"] for event in events})
            self.assertTrue((root / "runs" / "run" / "data" / "prepared" / "rec" / "labels.csv").exists())

    def test_kaggle_packager_generates_script_kernel_without_pyside6(self):
        from pipeline.kaggle import package_kaggle_kernel
        from pipeline.schema import load_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config_path = root / "pipeline_task.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "kg", "output_dir": str(root / "runs" / "kg")},
                        "datasets": {"rec": {"kind": "image_text", "root": "dataset"}},
                        "tasks": [{"id": "inspect", "type": "dataset.inspect", "dataset": "rec"}],
                        "kaggle": {
                            "kernel_id": "tiiann/seven-segment-ocr-pipeline",
                            "dataset_sources": ["owner/data"],
                        },
                    }
                ),
                encoding="utf-8",
            )
            kernel_dir = package_kaggle_kernel(load_pipeline_config(config_path), project_dir=Path.cwd())
            metadata = json.loads((kernel_dir / "kernel-metadata.json").read_text(encoding="utf-8"))
            entry = (kernel_dir / "kaggle_pipeline_entry.py").read_text(encoding="utf-8")

            self.assertEqual(metadata["kernel_type"], "script")
            self.assertTrue(metadata["enable_gpu"])
            self.assertEqual(metadata["dataset_sources"], ["owner/data"])
            self.assertIn("pipeline_task.json", {p.name for p in kernel_dir.iterdir()})
            self.assertNotIn("PySide6", entry)

    def test_pipeline_core_does_not_import_pyside6(self):
        pipeline_root = Path.cwd() / "pipeline"
        if not pipeline_root.exists():
            self.fail("pipeline package is missing")
        offenders = []
        for path in pipeline_root.rglob("*.py"):
            text = path.read_text(encoding="utf-8")
            if "PySide6" in text:
                offenders.append(path.name)
        self.assertEqual(offenders, [])

    def test_example_task_loads_and_ui_uses_qprocess(self):
        from pipeline.schema import load_pipeline_config

        example = Path.cwd() / "pipeline_task.example.yaml"
        config = load_pipeline_config(example)
        ui_source = (Path.cwd() / "pipeline_ui" / "app.py").read_text(encoding="utf-8")

        self.assertEqual(config.run.name, "seven_segment_smoke")
        self.assertIn("dataset.prepare_recognition", {task.type for task in config.tasks})
        self.assertIn("QProcess", ui_source)
        self.assertIn("events.jsonl", ui_source)

    def test_task_cache_hash_changes_with_params_dataset_and_dependency_artifacts(self):
        from pipeline.cache import compute_task_hash, hash_file
        from pipeline.schema import parse_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dep_artifact = root / "dep.txt"
            dep_artifact.write_text("one", encoding="utf-8")
            raw = {
                "schema_version": 1,
                "run": {"name": "hash"},
                "datasets": {"rec": {"kind": "image_text", "root": "dataset", "labels": "labels.csv"}},
                "tasks": [
                    {"id": "prepare", "type": "dataset.prepare_recognition", "dataset": "rec", "params": {"batch_size": 8}}
                ],
            }
            config = parse_pipeline_config(raw)
            task = config.tasks[0]
            first = compute_task_hash(task, config, project_dir=root, dependency_artifacts={"dep": [dep_artifact]})

            raw["tasks"][0]["params"]["batch_size"] = 16
            second = compute_task_hash(parse_pipeline_config(raw).tasks[0], parse_pipeline_config(raw), project_dir=root, dependency_artifacts={"dep": [dep_artifact]})

            raw["tasks"][0]["params"]["batch_size"] = 8
            raw["datasets"]["rec"]["labels"] = "other.csv"
            third_config = parse_pipeline_config(raw)
            third = compute_task_hash(third_config.tasks[0], third_config, project_dir=root, dependency_artifacts={"dep": [dep_artifact]})

            dep_artifact.write_text("two", encoding="utf-8")
            fourth = compute_task_hash(task, config, project_dir=root, dependency_artifacts={"dep": [dep_artifact]})
            dep_digest_length = len(hash_file(dep_artifact))

        self.assertNotEqual(first, second)
        self.assertNotEqual(first, third)
        self.assertNotEqual(first, fourth)
        self.assertEqual(dep_digest_length, 64)

    def test_runner_uses_default_cache_and_no_cache_reruns(self):
        from pipeline.runners import run_pipeline
        from pipeline.schema import load_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rec = root / "dataset"
            self._make_image(rec / "images" / "a.png")
            (rec / "labels.csv").write_text("image_path,text\nimages/a.png,123\n", encoding="utf-8")
            config_path = root / "pipeline_task.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "cached", "output_dir": str(root / "runs" / "cached")},
                        "datasets": {
                            "rec": {
                                "kind": "image_text",
                                "root": str(rec),
                                "labels": "labels.csv",
                                "image_column": "image_path",
                                "label_column": "text",
                            }
                        },
                        "tasks": [
                            {"id": "inspect", "type": "dataset.inspect", "dataset": "rec"},
                            {
                                "id": "prepare",
                                "type": "dataset.prepare_recognition",
                                "dataset": "rec",
                                "depends_on": ["inspect"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            config = load_pipeline_config(config_path)
            first = run_pipeline(config, project_dir=Path.cwd())
            second = run_pipeline(config, project_dir=Path.cwd())
            forced = run_pipeline(config, project_dir=Path.cwd(), force={"inspect"})
            third = run_pipeline(config, project_dir=Path.cwd(), use_cache=False)
            events = [
                json.loads(line)
                for line in (root / "runs" / "cached" / "events.jsonl").read_text(encoding="utf-8").splitlines()
            ]

            self.assertEqual(first["summary"]["completed"], 2)
            self.assertEqual(second["summary"]["skipped"], 2)
            self.assertEqual(second["summary"]["blocked"], 0)
            self.assertEqual(forced["tasks"]["inspect"]["status"], "completed")
            self.assertEqual(forced["tasks"]["prepare"]["status"], "completed")
            self.assertEqual(third["summary"]["completed"], 2)
            self.assertTrue((root / "runs" / "cached" / ".task_cache.json").exists())
            self.assertIn("task_skipped", {event["event"] for event in events})
            self.assertIn("prepare", third["tasks"])
            self.assertEqual(third["tasks"]["prepare"]["status"], "completed")

    def test_runner_target_from_task_force_resume_and_blocked_dependents(self):
        from pipeline.runners import run_pipeline
        from pipeline.schema import load_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rec = root / "dataset"
            self._make_image(rec / "images" / "a.png")
            (rec / "labels.csv").write_text("image_path,text\nimages/a.png,123\n", encoding="utf-8")
            missing = root / "missing"
            config_path = root / "pipeline_task.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "schema_version": 1,
                        "run": {"name": "graph", "output_dir": str(root / "runs" / "graph")},
                        "datasets": {
                            "rec": {
                                "kind": "image_text",
                                "root": str(rec),
                                "labels": "labels.csv",
                                "image_column": "image_path",
                                "label_column": "text",
                            },
                            "bad": {"kind": "image_text", "root": str(missing), "labels": "labels.csv"},
                        },
                        "tasks": [
                            {"id": "inspect", "type": "dataset.inspect", "dataset": "rec"},
                            {
                                "id": "prepare",
                                "type": "dataset.prepare_recognition",
                                "dataset": "rec",
                                "depends_on": ["inspect"],
                            },
                            {"id": "bad", "type": "dataset.inspect", "dataset": "bad"},
                            {
                                "id": "after_bad",
                                "type": "dataset.prepare_recognition",
                                "dataset": "bad",
                                "depends_on": ["bad"],
                            },
                        ],
                    }
                ),
                encoding="utf-8",
            )
            config = load_pipeline_config(config_path)
            target_report = run_pipeline(config, project_dir=Path.cwd(), targets={"prepare"})
            from_report = run_pipeline(config, project_dir=Path.cwd(), from_task="prepare", force={"prepare"})
            failed_report = run_pipeline(config, project_dir=Path.cwd(), targets={"after_bad"}, force={"bad", "after_bad"})
            resume_report = run_pipeline(config, project_dir=Path.cwd(), resume=True)

        self.assertEqual(set(target_report["tasks"]), {"inspect", "prepare"})
        self.assertEqual(set(from_report["tasks"]), {"prepare"})
        self.assertEqual(failed_report["tasks"]["bad"]["status"], "failed")
        self.assertEqual(failed_report["tasks"]["after_bad"]["status"], "blocked")
        self.assertEqual(resume_report["selected_tasks"], ["bad", "after_bad"])
        self.assertEqual(resume_report["summary"]["blocked"], 1)

    def test_scheduler_runs_independent_tasks_concurrently(self):
        from pipeline.runners import run_pipeline
        from pipeline.schema import parse_pipeline_config

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rec1 = root / "a"
            rec2 = root / "b"
            self._make_image(rec1 / "images" / "a.png")
            self._make_image(rec2 / "images" / "b.png")
            (rec1 / "labels.csv").write_text("image_path,text\nimages/a.png,1\n", encoding="utf-8")
            (rec2 / "labels.csv").write_text("image_path,text\nimages/b.png,2\n", encoding="utf-8")
            config = parse_pipeline_config(
                {
                    "schema_version": 1,
                    "run": {"name": "parallel", "output_dir": str(root / "runs" / "parallel")},
                    "datasets": {
                        "a": {"kind": "image_text", "root": str(rec1), "labels": "labels.csv", "image_column": "image_path", "label_column": "text"},
                        "b": {"kind": "image_text", "root": str(rec2), "labels": "labels.csv", "image_column": "image_path", "label_column": "text"},
                    },
                    "tasks": [
                        {"id": "inspect_a", "type": "dataset.inspect", "dataset": "a"},
                        {"id": "inspect_b", "type": "dataset.inspect", "dataset": "b"},
                    ],
                }
            )
            report = run_pipeline(config, project_dir=Path.cwd(), max_workers=2)

        self.assertEqual(report["summary"]["completed"], 2)
        self.assertEqual(report["execution"]["max_workers"], 2)
        self.assertEqual(set(report["tasks"]), {"inspect_a", "inspect_b"})
        self.assertTrue(all(item["status"] in {"completed", "skipped"} for item in report["tasks"].values()))

    def test_cli_run_parser_accepts_partial_cache_and_parallel_flags(self):
        from pipeline.cli import build_parser

        args = build_parser().parse_args(
            [
                "run",
                "--config",
                "pipeline_task.yaml",
                "--target",
                "prepare",
                "eval",
                "--from-task",
                "prepare",
                "--resume",
                "--no-cache",
                "--force",
                "prepare",
                "--max-workers",
                "4",
            ]
        )

        self.assertEqual(args.target, ["prepare", "eval"])
        self.assertEqual(args.from_task, "prepare")
        self.assertTrue(args.resume)
        self.assertTrue(args.no_cache)
        self.assertEqual(args.force, ["prepare"])
        self.assertEqual(args.max_workers, 4)


if __name__ == "__main__":
    unittest.main()
