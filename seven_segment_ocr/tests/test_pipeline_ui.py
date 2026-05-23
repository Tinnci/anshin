import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pipeline_ui
from PySide6.QtWidgets import QApplication


def _app() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication(sys.argv)
    return app


class PipelineUiTest(unittest.TestCase):
    def _config_path(self, root: Path) -> Path:
        path = root / "pipeline_task.yaml"
        path.write_text(
            yaml.safe_dump(
                {
                    "schema_version": 1,
                    "run": {"name": "ui", "output_dir": str(root / "runs" / "ui")},
                    "datasets": {"rec": {"kind": "image_text", "root": "dataset", "labels": "labels.csv"}},
                    "tasks": [
                        {"id": "inspect", "type": "dataset.inspect", "dataset": "rec"},
                        {
                            "id": "prepare",
                            "type": "dataset.prepare_recognition",
                            "dataset": "rec",
                            "depends_on": ["inspect"],
                            "params": {"batch_size": 8},
                        },
                    ],
                }
            ),
            encoding="utf-8",
        )
        return path

    def test_dag_view_renders_nodes_edges_and_event_states(self):
        _app()
        from pipeline.schema import load_pipeline_config
        from pipeline_ui.widgets.dag_view import DagView

        with tempfile.TemporaryDirectory() as tmp:
            config = load_pipeline_config(self._config_path(Path(tmp)))
            view = DagView()
            selected = []
            view.node_selected.connect(selected.append)
            view.set_config(config)
            view.apply_events(
                [
                    {"event": "task_started", "task_id": "inspect"},
                    {"event": "task_finished", "task_id": "inspect"},
                    {"event": "task_skipped", "task_id": "prepare"},
                ]
            )

        self.assertEqual(view.node_count(), 2)
        self.assertEqual(view.edge_count(), 1)
        self.assertEqual(view.node_state("inspect"), "completed")
        self.assertEqual(view.node_state("prepare"), "skipped")

    def test_builder_round_trips_config_and_validates_supported_tasks(self):
        _app()
        from pipeline.schema import load_pipeline_config
        from pipeline_ui.widgets.builder import ConfigBuilderWidget

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            config = load_pipeline_config(self._config_path(root))
            output = root / "saved.yaml"
            builder = ConfigBuilderWidget()
            builder.load_config(config)
            builder.set_task_param("prepare", "batch_size", "16")
            builder.save_yaml(output)
            saved = load_pipeline_config(output)

        self.assertEqual(saved.tasks[1].params["batch_size"], 16)
        self.assertIn("dataset.inspect", builder.supported_task_types())

    def test_leaderboard_scanner_reads_runs_and_sorts_metrics(self):
        from pipeline.leaderboard import scan_leaderboard

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            run_a = root / "run_a"
            run_b = root / "run_b"
            (run_a / "eval").mkdir(parents=True)
            (run_b / "eval").mkdir(parents=True)
            (run_a / "run_report.json").write_text(json.dumps({"run": {"name": "run_a"}, "summary": {"failed": 0}}), encoding="utf-8")
            (run_b / "run_report.json").write_text(json.dumps({"run": {"name": "run_b"}, "summary": {"failed": 0}}), encoding="utf-8")
            (run_a / "eval" / "results.json").write_text(
                json.dumps({"results": [{"model_id": "a", "exact": 0.75, "cer": 0.1, "latency_ms": 12.0, "model_size_bytes": 100}]}),
                encoding="utf-8",
            )
            (run_b / "eval" / "results.json").write_text(
                json.dumps({"results": [{"model_id": "b", "exact": 0.9, "cer": 0.05, "latency_ms": 20.0, "model_size_bytes": 200}]}),
                encoding="utf-8",
            )

            rows = scan_leaderboard(root)

        self.assertEqual([row["model_id"] for row in rows], ["b", "a"])
        self.assertEqual(rows[0]["run_name"], "run_b")
        self.assertEqual(rows[0]["exact"], 0.9)

    def test_main_window_instantiates_with_dag_builder_and_leaderboard_tabs(self):
        _app()
        from pipeline_ui.app import PipelineMainWindow

        window = PipelineMainWindow()
        labels = [window.tabs.tabText(index) for index in range(window.tabs.count())]

        self.assertTrue(hasattr(window, "dag_view"))
        self.assertIn("拓扑图 (DAG)", labels)
        self.assertIn("配置编辑器", labels)
        self.assertIn("性能排行榜", labels)


if __name__ == "__main__":
    unittest.main()
