from __future__ import annotations

from pathlib import Path
from typing import Any

from PySide6.QtWidgets import QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget

from pipeline.leaderboard import scan_leaderboard


class LeaderboardWidget(QWidget):
    COLUMNS = ["run_name", "source", "model_id", "exact", "cer", "digit_accuracy", "latency_ms", "model_size_bytes", "status"]

    def __init__(self, runs_root: str | Path) -> None:
        super().__init__()
        self.runs_root = Path(runs_root)
        self.table = QTableWidget(0, len(self.COLUMNS))
        self.table.setHorizontalHeaderLabels(self.COLUMNS)
        self.table.setSortingEnabled(True)
        layout = QVBoxLayout(self)
        layout.addWidget(self.table)

    def refresh(self) -> list[dict[str, Any]]:
        rows = scan_leaderboard(self.runs_root)
        self.table.setSortingEnabled(False)
        self.table.setRowCount(len(rows))
        for row_index, row in enumerate(rows):
            for col, key in enumerate(self.COLUMNS):
                value = row.get(key)
                self.table.setItem(row_index, col, QTableWidgetItem("" if value is None else str(value)))
        self.table.setSortingEnabled(True)
        self.table.resizeColumnsToContents()
        return rows
