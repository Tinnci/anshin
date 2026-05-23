from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class EventWriter:
    def __init__(self, path: str | Path, *, run_id: str):
        self.path = Path(path)
        self.run_id = run_id
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def emit(
        self,
        event: str,
        *,
        task_id: str,
        task_type: str,
        phase: str = "run",
        payload: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        row = {
            "schema_version": 1,
            "run_id": self.run_id,
            "task_id": task_id,
            "task_type": task_type,
            "phase": phase,
            "event": event,
            "time": datetime.now(timezone.utc).isoformat(),
            "payload": payload or {},
        }
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
        return row

    def task_started(self, task_id: str, task_type: str, payload: dict[str, Any] | None = None) -> None:
        self.emit("task_started", task_id=task_id, task_type=task_type, payload=payload)

    def task_progress(self, task_id: str, task_type: str, payload: dict[str, Any]) -> None:
        self.emit("task_progress", task_id=task_id, task_type=task_type, payload=payload)

    def metric(self, task_id: str, task_type: str, payload: dict[str, Any]) -> None:
        self.emit("metric", task_id=task_id, task_type=task_type, phase="train", payload=payload)

    def artifact(self, task_id: str, task_type: str, path: str | Path, *, role: str, mime: str | None = None) -> None:
        payload: dict[str, Any] = {"path": str(path), "role": role}
        if mime:
            payload["mime"] = mime
        self.emit("artifact", task_id=task_id, task_type=task_type, payload=payload)

    def sample_preview(self, task_id: str, task_type: str, path: str | Path, payload: dict[str, Any] | None = None) -> None:
        preview_payload: dict[str, Any] = {"path": str(path)}
        if payload:
            preview_payload.update(payload)
        self.emit("sample_preview", task_id=task_id, task_type=task_type, phase="data", payload=preview_payload)

    def warning(self, task_id: str, task_type: str, *, code: str, message: str) -> None:
        self.emit("warning", task_id=task_id, task_type=task_type, payload={"code": code, "message": message})

    def task_finished(self, task_id: str, task_type: str, payload: dict[str, Any] | None = None) -> None:
        self.emit("task_finished", task_id=task_id, task_type=task_type, payload=payload)

    def task_failed(self, task_id: str, task_type: str, error: BaseException) -> None:
        self.emit(
            "task_failed",
            task_id=task_id,
            task_type=task_type,
            payload={"error_type": type(error).__name__, "message": str(error)},
        )
