from __future__ import annotations

from collections import defaultdict, deque

from .schema import TaskConfig


def tasks_by_id(tasks: list[TaskConfig]) -> dict[str, TaskConfig]:
    return {task.id: task for task in tasks}


def dependency_closure(tasks: list[TaskConfig], targets: set[str]) -> set[str]:
    by_id = tasks_by_id(tasks)
    selected: set[str] = set()

    def visit(task_id: str) -> None:
        if task_id in selected:
            return
        selected.add(task_id)
        for dep in by_id[task_id].depends_on:
            visit(dep)

    for target in targets:
        visit(target)
    return selected


def descendant_closure(tasks: list[TaskConfig], roots: set[str]) -> set[str]:
    children: dict[str, list[str]] = defaultdict(list)
    for task in tasks:
        for dep in task.depends_on:
            children[dep].append(task.id)
    selected = set(roots)
    queue = deque(roots)
    while queue:
        task_id = queue.popleft()
        for child in children.get(task_id, []):
            if child not in selected:
                selected.add(child)
                queue.append(child)
    return selected


def ordered_task_ids(tasks: list[TaskConfig], selected: set[str] | None = None) -> list[str]:
    by_id = tasks_by_id(tasks)
    selected_ids = selected or set(by_id)
    ordered: list[str] = []
    seen: set[str] = set()

    def visit(task_id: str) -> None:
        if task_id in seen or task_id not in selected_ids:
            return
        for dep in by_id[task_id].depends_on:
            visit(dep)
        seen.add(task_id)
        ordered.append(task_id)

    for task in tasks:
        visit(task.id)
    return ordered


def ready_task_ids(tasks: list[TaskConfig], selected: set[str], completed: set[str], blocked: set[str], running: set[str]) -> list[str]:
    ready: list[str] = []
    for task_id in ordered_task_ids(tasks, selected):
        if task_id in completed or task_id in blocked or task_id in running:
            continue
        task = tasks_by_id(tasks)[task_id]
        if all(dep not in selected or dep in completed for dep in task.depends_on):
            ready.append(task_id)
    return ready


def blocked_by_failure(tasks: list[TaskConfig], selected: set[str], failed: set[str]) -> set[str]:
    blocked: set[str] = set()
    changed = True
    by_id = tasks_by_id(tasks)
    while changed:
        changed = False
        for task_id in selected:
            if task_id in failed or task_id in blocked:
                continue
            task = by_id[task_id]
            if any(dep in failed or dep in blocked for dep in task.depends_on if dep in selected):
                blocked.add(task_id)
                changed = True
    return blocked
