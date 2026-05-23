from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import yaml
from PySide6.QtWidgets import QLabel, QPlainTextEdit, QTableWidget, QTableWidgetItem, QTabWidget, QVBoxLayout, QWidget

from pipeline.schema import SUPPORTED_TASK_TYPES, PipelineConfig, parse_pipeline_config, pipeline_config_to_dict


class ConfigBuilderWidget(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self._raw: dict[str, Any] = {}
        self._yaml = QPlainTextEdit()
        self._dataset_table = QTableWidget(0, 4)
        self._dataset_table.setHorizontalHeaderLabels(["标识 (ID)", "类别 (Kind)", "根路径 (Root)", "标注文件 (Labels)"])
        self._task_table = QTableWidget(0, 5)
        self._task_table.setHorizontalHeaderLabels(["任务 ID", "类型 (Type)", "数据集 (Dataset)", "依赖节点 (Depends On)", "运行参数 (Params)"])
        tabs = QTabWidget()
        tabs.addTab(self._yaml_tab(), "YAML 配置")
        tabs.addTab(self._table_tab(self._dataset_table, "数据集列表"), "数据集列表")
        tabs.addTab(self._table_tab(self._task_table, "任务列表"), "任务列表")
        layout = QVBoxLayout(self)
        layout.addWidget(tabs)

    def supported_task_types(self) -> list[str]:
        return sorted(SUPPORTED_TASK_TYPES)

    def load_config(self, config: PipelineConfig) -> None:
        self._raw = pipeline_config_to_dict(config)
        self._sync_widgets()

    def set_task_param(self, task_id: str, key: str, value: str) -> None:
        parsed = _parse_value(value)
        for task in self._raw.get("tasks", []):
            if task.get("id") == task_id:
                task.setdefault("params", {})[key] = parsed
                break
        self._sync_widgets()

    def to_dict(self) -> dict[str, Any]:
        self._sync_from_yaml()
        return dict(self._raw)

    def validate(self) -> PipelineConfig:
        return parse_pipeline_config(self.to_dict())

    def save_yaml(self, path: str | Path) -> None:
        config = self.validate()
        output = Path(path)
        output.write_text(yaml.safe_dump(pipeline_config_to_dict(config), sort_keys=False, allow_unicode=True), encoding="utf-8")

    def _sync_widgets(self) -> None:
        self._yaml.setPlainText(yaml.safe_dump(self._raw, sort_keys=False, allow_unicode=True))
        datasets = self._raw.get("datasets", {})
        self._dataset_table.setRowCount(len(datasets))
        for row, (dataset_id, dataset) in enumerate(datasets.items()):
            values = [dataset_id, dataset.get("kind", ""), dataset.get("root", ""), dataset.get("labels", "")]
            for col, value in enumerate(values):
                self._dataset_table.setItem(row, col, QTableWidgetItem(str(value)))
        tasks = self._raw.get("tasks", [])
        self._task_table.setRowCount(len(tasks))
        for row, task in enumerate(tasks):
            values = [
                task.get("id", ""),
                task.get("type", ""),
                task.get("dataset", ""),
                ",".join(task.get("depends_on", []) or []),
                json.dumps(task.get("params", {}), ensure_ascii=False),
            ]
            for col, value in enumerate(values):
                self._task_table.setItem(row, col, QTableWidgetItem(str(value)))
        self._dataset_table.resizeColumnsToContents()
        self._task_table.resizeColumnsToContents()

    def _sync_from_yaml(self) -> None:
        parsed = yaml.safe_load(self._yaml.toPlainText()) or {}
        if isinstance(parsed, dict):
            self._raw = parsed

    def _yaml_tab(self) -> QWidget:
        tab = QWidget()
        layout = QVBoxLayout(tab)
        layout.addWidget(QLabel("编辑完整的管线 YAML，然后验证并保存。"))
        layout.addWidget(self._yaml)
        return tab

    def _table_tab(self, table: QTableWidget, label: str = "") -> QWidget:
        tab = QWidget()
        layout = QVBoxLayout(tab)
        if label:
            layout.addWidget(QLabel(label))
        layout.addWidget(table)
        return tab


def _parse_value(value: str) -> Any:
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return value
