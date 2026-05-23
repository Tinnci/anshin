from __future__ import annotations

from PySide6.QtCore import QPointF, Qt, Signal
from PySide6.QtGui import QColor, QPen
from PySide6.QtWidgets import QGraphicsLineItem, QGraphicsRectItem, QGraphicsScene, QGraphicsTextItem, QGraphicsView, QMenu

from pipeline.graph import ordered_task_ids
from pipeline.schema import PipelineConfig


STATE_COLORS = {
    "pending": QColor("#e5e7eb"),
    "ready": QColor("#bfdbfe"),
    "running": QColor("#fde68a"),
    "completed": QColor("#bbf7d0"),
    "failed": QColor("#fecaca"),
    "skipped": QColor("#fed7aa"),
    "blocked": QColor("#cbd5e1"),
}


class DagView(QGraphicsView):
    node_selected = Signal(str)
    run_target_requested = Signal(str)
    resume_from_requested = Signal(str)
    inspect_requested = Signal(str)
    logs_requested = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.setScene(QGraphicsScene(self))
        self._config: PipelineConfig | None = None
        self._states: dict[str, str] = {}
        self._nodes: dict[str, QGraphicsRectItem] = {}
        self._labels: dict[str, QGraphicsTextItem] = {}
        self._edges: list[QGraphicsLineItem] = []
        self.setRenderHints(self.renderHints())

    def set_config(self, config: PipelineConfig) -> None:
        self._config = config
        self._states = {task.id: "pending" for task in config.tasks}
        self._render()

    def apply_events(self, events: list[dict]) -> None:
        for event in events:
            task_id = event.get("task_id")
            if not task_id:
                continue
            event_type = event.get("event")
            if event_type == "task_started":
                self._states[task_id] = "running"
            elif event_type == "task_finished":
                self._states[task_id] = "completed"
            elif event_type == "task_failed":
                self._states[task_id] = "failed"
            elif event_type == "task_skipped":
                self._states[task_id] = "skipped"
            elif event_type == "task_blocked":
                self._states[task_id] = "blocked"
        self._refresh_colors()

    def set_task_report(self, report: dict) -> None:
        for task_id, row in report.get("tasks", {}).items():
            status = row.get("status")
            if status:
                self._states[task_id] = str(status)
        self._refresh_colors()

    def node_count(self) -> int:
        return len(self._nodes)

    def edge_count(self) -> int:
        return len(self._edges)

    def node_state(self, task_id: str) -> str:
        return self._states.get(task_id, "pending")

    def mousePressEvent(self, event):  # noqa: N802
        task_id = self._task_at(event.pos())
        if task_id:
            self.node_selected.emit(task_id)
        super().mousePressEvent(event)

    def contextMenuEvent(self, event):  # noqa: N802
        task_id = self._task_at(event.pos())
        if not task_id:
            return super().contextMenuEvent(event)
        menu = QMenu(self)
        run_action = menu.addAction("Run target")
        resume_action = menu.addAction("Resume from")
        logs_action = menu.addAction("View logs")
        inspect_action = menu.addAction("Inspect parameters")
        chosen = menu.exec(event.globalPos())
        if chosen == run_action:
            self.run_target_requested.emit(task_id)
        elif chosen == resume_action:
            self.resume_from_requested.emit(task_id)
        elif chosen == logs_action:
            self.logs_requested.emit(task_id)
        elif chosen == inspect_action:
            self.inspect_requested.emit(task_id)

    def _render(self) -> None:
        scene = self.scene()
        scene.clear()
        self._nodes.clear()
        self._labels.clear()
        self._edges.clear()
        if not self._config:
            return
        positions = self._layout_positions()
        by_id = {task.id: task for task in self._config.tasks}
        for task_id in ordered_task_ids(self._config.tasks):
            x, y = positions[task_id]
            rect = scene.addRect(x, y, 190, 74, QPen(QColor("#475569"), 1))
            rect.setBrush(STATE_COLORS[self._states.get(task_id, "pending")])
            rect.setData(0, task_id)
            label = scene.addText(f"{task_id}\n{by_id[task_id].type}")
            label.setDefaultTextColor(QColor("#0f172a"))
            label.setPos(x + 10, y + 8)
            label.setData(0, task_id)
            self._nodes[task_id] = rect
            self._labels[task_id] = label
        for task in self._config.tasks:
            x2, y2 = positions[task.id]
            for dep in task.depends_on:
                if dep not in positions:
                    continue
                x1, y1 = positions[dep]
                line = scene.addLine(x1 + 190, y1 + 37, x2, y2 + 37, QPen(QColor("#64748b"), 2))
                self._edges.append(line)
        scene.setSceneRect(scene.itemsBoundingRect().adjusted(-24, -24, 24, 24))

    def _refresh_colors(self) -> None:
        for task_id, rect in self._nodes.items():
            rect.setBrush(STATE_COLORS.get(self._states.get(task_id, "pending"), STATE_COLORS["pending"]))

    def _layout_positions(self) -> dict[str, tuple[int, int]]:
        assert self._config is not None
        by_id = {task.id: task for task in self._config.tasks}
        depth_cache: dict[str, int] = {}

        def depth(task_id: str) -> int:
            if task_id in depth_cache:
                return depth_cache[task_id]
            deps = by_id[task_id].depends_on
            depth_cache[task_id] = 0 if not deps else 1 + max(depth(dep) for dep in deps)
            return depth_cache[task_id]

        layers: dict[int, list[str]] = {}
        for task_id in ordered_task_ids(self._config.tasks):
            layers.setdefault(depth(task_id), []).append(task_id)
        positions = {}
        for layer, task_ids in layers.items():
            for row, task_id in enumerate(task_ids):
                positions[task_id] = (layer * 260, row * 120)
        return positions

    def _task_at(self, pos) -> str | None:
        item = self.itemAt(pos)
        while item is not None:
            task_id = item.data(0)
            if task_id:
                return str(task_id)
            item = item.parentItem()
        return None
