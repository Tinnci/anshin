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


if __name__ == "__main__":
    unittest.main()
