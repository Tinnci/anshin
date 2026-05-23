from __future__ import annotations

import json
import os
import sys
import sysconfig
from pathlib import Path
from typing import Any

_qt_plugins = Path(sysconfig.get_path("purelib")) / "PySide6" / "Qt" / "plugins"
if _qt_plugins.exists():
    os.environ.setdefault("QT_PLUGIN_PATH", str(_qt_plugins))
    os.environ.setdefault("QT_QPA_PLATFORM_PLUGIN_PATH", str(_qt_plugins / "platforms"))

from PySide6.QtCore import Qt, QProcess, QTimer
from PySide6.QtGui import QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QApplication,
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QPlainTextEdit,
    QSplitter,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from pipeline.artifacts import collect_run_artifacts, read_json
from pipeline.schema import load_pipeline_config


PROJECT_DIR = Path(__file__).resolve().parents[1]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


class MetricChart(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self.setMinimumHeight(220)
        self.metrics: list[dict[str, Any]] = []

    def set_metrics(self, metrics: list[dict[str, Any]]) -> None:
        self.metrics = metrics
        self.update()

    def paintEvent(self, event: Any) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.fillRect(self.rect(), Qt.GlobalColor.white)
        margin = 36
        area = self.rect().adjusted(margin, margin, -margin, -margin)
        painter.setPen(QPen(Qt.GlobalColor.lightGray, 1))
        painter.drawRect(area)
        series = self._series()
        if not series:
            painter.setPen(Qt.GlobalColor.darkGray)
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, "No metric events yet")
            return
        colors = [
            Qt.GlobalColor.red,
            Qt.GlobalColor.blue,
            Qt.GlobalColor.darkGreen,
            Qt.GlobalColor.magenta,
            Qt.GlobalColor.darkCyan,
        ]
        for index, (name, values) in enumerate(series.items()):
            if len(values) < 2:
                continue
            numeric = [value for _, value in values]
            low = min(numeric)
            high = max(numeric)
            span = high - low or 1.0
            painter.setPen(QPen(colors[index % len(colors)], 2))
            last = None
            for step, value in values:
                x = area.left() + (step / max(1, len(self.metrics) - 1)) * area.width()
                y = area.bottom() - ((value - low) / span) * area.height()
                point = (int(x), int(y))
                if last is not None:
                    painter.drawLine(last[0], last[1], point[0], point[1])
                last = point
            painter.drawText(area.left() + 8, area.top() + 18 + index * 18, name)

    def _series(self) -> dict[str, list[tuple[int, float]]]:
        preferred = ["loss", "val_loss", "exact", "cer", "digit_accuracy", "lr"]
        out: dict[str, list[tuple[int, float]]] = {}
        for step, payload in enumerate(self.metrics):
            for key in preferred:
                value = payload.get(key)
                if isinstance(value, (int, float)):
                    out.setdefault(key, []).append((step, float(value)))
        return out


class PipelineMainWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Seven Segment OCR Pipeline")
        self.resize(1280, 820)
        self.run_dir: Path | None = None
        self.config_path: Path | None = None
        self.process: QProcess | None = None
        self._last_event_mtime: float | None = None
        self.artifacts: list[dict[str, Any]] = []

        self.config_line = QLineEdit(str(PROJECT_DIR / "pipeline_task.example.yaml"))
        self.run_line = QLineEdit("")
        self.kernel_line = QLineEdit("tiiann/seven-segment-ocr-pipeline")
        self.kernel_dir_line = QLineEdit("")

        self.events_table = QTableWidget(0, 5)
        self.events_table.setHorizontalHeaderLabels(["Time", "Event", "Task", "Type", "Payload"])
        self.artifact_table = QTableWidget(0, 3)
        self.artifact_table.setHorizontalHeaderLabels(["Role", "Path", "MIME"])
        self.artifact_table.itemSelectionChanged.connect(self._show_selected_artifact)

        self.dataset_summary = QPlainTextEdit()
        self.dataset_summary.setReadOnly(True)
        self.dataset_preview = QLabel("No dataset preview")
        self.dataset_preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.dataset_preview.setMinimumHeight(300)

        self.metric_chart = MetricChart()
        self.metric_table = QTableWidget(0, 2)
        self.metric_table.setHorizontalHeaderLabels(["Key", "Value"])
        self.training_console = QPlainTextEdit()
        self.training_console.setReadOnly(True)

        self.inference_view = QLabel("No inference overlay")
        self.inference_view.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.inference_text = QPlainTextEdit()
        self.inference_text.setReadOnly(True)

        self.eval_text = QPlainTextEdit()
        self.eval_text.setReadOnly(True)
        self.kaggle_console = QPlainTextEdit()
        self.kaggle_console.setReadOnly(True)

        self.tabs = QTabWidget()
        self.tabs.addTab(self._dataset_tab(), "Dataset")
        self.tabs.addTab(self._training_tab(), "Training")
        self.tabs.addTab(self._inference_tab(), "Inference")
        self.tabs.addTab(self._evaluation_tab(), "Evaluation")
        self.tabs.addTab(self._kaggle_tab(), "Kaggle")
        self.tabs.addTab(self._events_tab(), "Events")

        root = QWidget()
        layout = QVBoxLayout(root)
        layout.addLayout(self._toolbar())
        layout.addWidget(self.tabs)
        self.setCentralWidget(root)

        self.timer = QTimer(self)
        self.timer.setInterval(1000)
        self.timer.timeout.connect(self._refresh_if_changed)
        self.timer.start()

    def _toolbar(self) -> QHBoxLayout:
        layout = QHBoxLayout()
        choose_config = QPushButton("Task JSON/YAML")
        choose_config.clicked.connect(self._choose_config)
        run_button = QPushButton("Run")
        run_button.clicked.connect(self._run_config)
        open_run = QPushButton("Open Run")
        open_run.clicked.connect(self._choose_run)
        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.refresh_run)
        layout.addWidget(choose_config)
        layout.addWidget(self.config_line, 2)
        layout.addWidget(run_button)
        layout.addWidget(open_run)
        layout.addWidget(QLabel("Run"))
        layout.addWidget(self.run_line, 1)
        layout.addWidget(refresh)
        return layout

    def _dataset_tab(self) -> QWidget:
        splitter = QSplitter(Qt.Orientation.Horizontal)
        left = QWidget()
        left_layout = QVBoxLayout(left)
        left_layout.addWidget(QLabel("Dataset profile"))
        left_layout.addWidget(self.dataset_summary)
        right = QWidget()
        right_layout = QVBoxLayout(right)
        right_layout.addWidget(QLabel("Preview / overlay"))
        right_layout.addWidget(self.dataset_preview)
        right_layout.addWidget(QLabel("Artifacts"))
        right_layout.addWidget(self.artifact_table)
        splitter.addWidget(left)
        splitter.addWidget(right)
        box = QWidget()
        layout = QVBoxLayout(box)
        layout.addWidget(splitter)
        return box

    def _training_tab(self) -> QWidget:
        splitter = QSplitter(Qt.Orientation.Vertical)
        top = QWidget()
        top_layout = QHBoxLayout(top)
        top_layout.addWidget(self.metric_chart, 3)
        top_layout.addWidget(self.metric_table, 2)
        splitter.addWidget(top)
        splitter.addWidget(self.training_console)
        box = QWidget()
        layout = QVBoxLayout(box)
        layout.addWidget(splitter)
        return box

    def _inference_tab(self) -> QWidget:
        splitter = QSplitter(Qt.Orientation.Horizontal)
        splitter.addWidget(self.inference_view)
        splitter.addWidget(self.inference_text)
        box = QWidget()
        layout = QVBoxLayout(box)
        layout.addWidget(splitter)
        return box

    def _evaluation_tab(self) -> QWidget:
        box = QWidget()
        layout = QVBoxLayout(box)
        layout.addWidget(QLabel("Evaluation results"))
        layout.addWidget(self.eval_text)
        return box

    def _kaggle_tab(self) -> QWidget:
        box = QWidget()
        layout = QVBoxLayout(box)
        package_button = QPushButton("Package Kernel")
        package_button.clicked.connect(self._package_kaggle)
        push_button = QPushButton("Push")
        push_button.clicked.connect(self._push_kaggle)
        status_button = QPushButton("Status")
        status_button.clicked.connect(self._status_kaggle)
        fetch_button = QPushButton("Fetch Output")
        fetch_button.clicked.connect(self._fetch_kaggle)
        row1 = QHBoxLayout()
        row1.addWidget(package_button)
        row1.addWidget(QLabel("Kernel dir"))
        row1.addWidget(self.kernel_dir_line)
        row1.addWidget(push_button)
        row2 = QHBoxLayout()
        row2.addWidget(QLabel("Kernel id"))
        row2.addWidget(self.kernel_line)
        row2.addWidget(status_button)
        row2.addWidget(fetch_button)
        layout.addLayout(row1)
        layout.addLayout(row2)
        layout.addWidget(self.kaggle_console)
        return box

    def _events_tab(self) -> QWidget:
        box = QWidget()
        layout = QVBoxLayout(box)
        layout.addWidget(self.events_table)
        return box

    def _choose_config(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self,
            "Open pipeline task",
            str(PROJECT_DIR),
            "Pipeline tasks (*.yaml *.yml *.json)",
        )
        if path:
            self.config_line.setText(path)
            self._set_run_from_config(Path(path))

    def _choose_run(self) -> None:
        path = QFileDialog.getExistingDirectory(self, "Open run directory", str(PROJECT_DIR / "runs"))
        if path:
            self._set_run_dir(Path(path))

    def _set_run_from_config(self, config_path: Path) -> None:
        try:
            config = load_pipeline_config(config_path)
        except Exception as exc:
            self._show_error("Invalid config", str(exc))
            return
        run_dir = config.run.output_dir if config.run.output_dir.is_absolute() else PROJECT_DIR / config.run.output_dir
        self._set_run_dir(run_dir)
        self.config_path = config_path
        self.kernel_line.setText(config.kaggle.kernel_id)

    def _set_run_dir(self, run_dir: Path) -> None:
        self.run_dir = run_dir
        self.run_line.setText(str(run_dir))
        self.kernel_dir_line.setText(str(run_dir / "kaggle" / "kernel"))
        self._last_event_mtime = None
        self.refresh_run()

    def _run_config(self) -> None:
        config_path = Path(self.config_line.text()).expanduser()
        if not config_path.exists():
            self._show_error("Missing config", f"Config file does not exist: {config_path}")
            return
        self._set_run_from_config(config_path)
        self._start_process(
            [sys.executable, "-m", "pipeline.cli", "run", "--config", str(config_path), "--project-dir", str(PROJECT_DIR)],
            self.training_console,
        )

    def _package_kaggle(self) -> None:
        config_path = Path(self.config_line.text()).expanduser()
        if not config_path.exists():
            self._show_error("Missing config", f"Config file does not exist: {config_path}")
            return
        self._start_process(
            [sys.executable, "-m", "pipeline.cli", "package-kaggle", "--config", str(config_path), "--project-dir", str(PROJECT_DIR)],
            self.kaggle_console,
        )

    def _push_kaggle(self) -> None:
        kernel_dir = self.kernel_dir_line.text().strip()
        if not kernel_dir:
            self._show_error("Missing kernel dir", "Package a kernel or enter a kernel directory first.")
            return
        self._start_process(
            [sys.executable, "-m", "pipeline.cli", "push-kaggle", "--kernel-dir", kernel_dir],
            self.kaggle_console,
        )

    def _status_kaggle(self) -> None:
        self._start_process(
            [sys.executable, "-m", "pipeline.cli", "status-kaggle", "--kernel-id", self.kernel_line.text().strip()],
            self.kaggle_console,
        )

    def _fetch_kaggle(self) -> None:
        output_dir = self.run_dir / "kaggle" / "output" if self.run_dir else PROJECT_DIR / "runs" / "kaggle_output"
        self._start_process(
            [
                sys.executable,
                "-m",
                "pipeline.cli",
                "fetch-kaggle",
                "--kernel-id",
                self.kernel_line.text().strip(),
                "--output-dir",
                str(output_dir),
            ],
            self.kaggle_console,
        )

    def _start_process(self, command: list[str], console: QPlainTextEdit) -> None:
        if self.process and self.process.state() != QProcess.ProcessState.NotRunning:
            self._show_error("Process running", "Wait for the current pipeline command to finish.")
            return
        console.clear()
        console.appendPlainText("$ " + " ".join(command))
        process = QProcess(self)
        process.setWorkingDirectory(str(PROJECT_DIR))
        process.setProgram(command[0])
        process.setArguments(command[1:])
        process.readyReadStandardOutput.connect(lambda: console.appendPlainText(bytes(process.readAllStandardOutput()).decode(errors="replace")))
        process.readyReadStandardError.connect(lambda: console.appendPlainText(bytes(process.readAllStandardError()).decode(errors="replace")))
        process.finished.connect(lambda *_: self.refresh_run())
        process.start()
        self.process = process

    def _refresh_if_changed(self) -> None:
        if not self.run_dir:
            return
        events = self.run_dir / "events.jsonl"
        if not events.exists():
            return
        mtime = events.stat().st_mtime
        if mtime != self._last_event_mtime:
            self._last_event_mtime = mtime
            self.refresh_run()

    def refresh_run(self) -> None:
        if not self.run_dir:
            return
        events = self._read_events()
        self.artifacts = collect_run_artifacts(self.run_dir)
        self._render_events(events)
        self._render_metrics(events)
        self._render_artifacts()
        self._render_dataset_profile()
        self._render_inference()
        self._render_eval()

    def _read_events(self) -> list[dict[str, Any]]:
        if not self.run_dir:
            return []
        path = self.run_dir / "events.jsonl"
        if not path.exists():
            return []
        rows = []
        for line in path.read_text(encoding="utf-8").splitlines():
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
        return rows

    def _render_events(self, events: list[dict[str, Any]]) -> None:
        self.events_table.setRowCount(len(events))
        for row, event in enumerate(events):
            values = [
                event.get("time", ""),
                event.get("event", ""),
                event.get("task_id", ""),
                event.get("task_type", ""),
                json.dumps(event.get("payload", {}), ensure_ascii=False),
            ]
            for col, value in enumerate(values):
                self.events_table.setItem(row, col, QTableWidgetItem(str(value)))
        self.events_table.resizeColumnsToContents()

    def _render_metrics(self, events: list[dict[str, Any]]) -> None:
        metrics = [event.get("payload", {}) for event in events if event.get("event") == "metric"]
        self.metric_chart.set_metrics(metrics)
        latest = metrics[-1] if metrics else {}
        self.metric_table.setRowCount(len(latest))
        for row, (key, value) in enumerate(latest.items()):
            self.metric_table.setItem(row, 0, QTableWidgetItem(str(key)))
            self.metric_table.setItem(row, 1, QTableWidgetItem(str(value)))
        self.metric_table.resizeColumnsToContents()

    def _render_artifacts(self) -> None:
        self.artifact_table.setRowCount(len(self.artifacts))
        for row, artifact in enumerate(self.artifacts):
            for col, key in enumerate(["role", "path", "mime"]):
                self.artifact_table.setItem(row, col, QTableWidgetItem(str(artifact.get(key, ""))))
        self.artifact_table.resizeColumnsToContents()

    def _render_dataset_profile(self) -> None:
        profile_artifact = next((a for a in self.artifacts if a.get("role") == "dataset_profile"), None)
        if not profile_artifact:
            self.dataset_summary.setPlainText("No dataset_profile.json artifact yet.")
            self.dataset_preview.setText("No dataset preview")
            return
        profile_path = Path(str(profile_artifact["path"]))
        profile = read_json(profile_path, default={}) or {}
        self.dataset_summary.setPlainText(json.dumps(profile, indent=2, ensure_ascii=False))
        preview = profile.get("preview")
        if preview:
            self._set_label_image(self.dataset_preview, Path(preview))

    def _render_inference(self) -> None:
        prediction = next((a for a in self.artifacts if a.get("role") == "predictions"), None)
        if prediction:
            path = Path(str(prediction["path"]))
            self.inference_text.setPlainText(path.read_text(encoding="utf-8") if path.exists() else "")
        image = next((a for a in self.artifacts if "overlay" in str(a.get("path", "")).lower()), None)
        if image:
            self._set_label_image(self.inference_view, Path(str(image["path"])))

    def _render_eval(self) -> None:
        candidates = [
            a for a in self.artifacts
            if a.get("role") in {"evaluation_results", "evaluation_report", "evaluation_table"}
        ]
        if not candidates:
            self.eval_text.setPlainText("No evaluation artifacts yet.")
            return
        parts = []
        for artifact in candidates:
            path = Path(str(artifact["path"]))
            if path.exists() and path.is_file():
                parts.append(f"## {artifact.get('role')} {path}\n{path.read_text(encoding='utf-8')[:20000]}")
        self.eval_text.setPlainText("\n\n".join(parts))

    def _show_selected_artifact(self) -> None:
        selected = self.artifact_table.selectedItems()
        if not selected:
            return
        row = selected[0].row()
        if row >= len(self.artifacts):
            return
        path = Path(str(self.artifacts[row].get("path", "")))
        if path.suffix.lower() in IMAGE_EXTENSIONS:
            self._set_label_image(self.dataset_preview, path)
        elif path.exists() and path.is_file():
            self.dataset_summary.setPlainText(path.read_text(encoding="utf-8", errors="replace")[:20000])

    def _set_label_image(self, label: QLabel, path: Path) -> None:
        if not path.exists():
            label.setText(f"Missing image: {path}")
            return
        pixmap = QPixmap(str(path))
        if pixmap.isNull():
            label.setText(f"Could not load image: {path}")
            return
        label.setPixmap(pixmap.scaled(label.size(), Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))

    def _show_error(self, title: str, message: str) -> None:
        QMessageBox.warning(self, title, message)


def main() -> int:
    app = QApplication(sys.argv)
    window = PipelineMainWindow()
    window.show()
    return app.exec()
