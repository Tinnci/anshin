from __future__ import annotations

import csv
import json
import shutil
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw
import yaml


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def inspect_dataset(dataset: dict[str, Any] | Any, *, output_dir: str | Path) -> dict[str, Any]:
    raw = _dataset_raw(dataset)
    kind = raw["kind"]
    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    if kind == "image_text":
        profile = _inspect_image_text(raw, output_root)
    elif kind == "voc":
        profile = _inspect_voc(raw, output_root)
    elif kind == "yolo":
        profile = _inspect_yolo(raw, output_root)
    else:
        raise ValueError(f"unsupported dataset kind: {kind}")
    profile_path = output_root / "dataset_profile.json"
    profile_path.write_text(json.dumps(profile, indent=2, ensure_ascii=False), encoding="utf-8")
    return profile


def prepare_recognition_dataset(dataset: dict[str, Any] | Any, *, output_dir: str | Path) -> Path:
    raw = _dataset_raw(dataset)
    if raw["kind"] != "image_text":
        raise ValueError("dataset.prepare_recognition requires kind=image_text")
    root = Path(raw["root"])
    labels_path = root / raw.get("labels", "labels.csv")
    image_column = raw.get("image_column", "filename")
    label_column = raw.get("label_column", "label")
    split_column = raw.get("split_column", "split")
    source = raw.get("source") or root.name
    output_root = Path(output_dir)
    image_output = output_root / "images"
    image_output.mkdir(parents=True, exist_ok=True)
    rows = []
    with labels_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for index, row in enumerate(reader):
            image_value = row[image_column]
            label = row[label_column]
            split = row.get(split_column, "train") if split_column else "train"
            image_path = _resolve_image(root, image_value)
            destination = image_output / image_path.name
            if destination.exists():
                destination = image_output / f"{index:06d}{image_path.suffix.lower()}"
            shutil.copy2(image_path, destination)
            rows.append(
                {
                    "filename": f"images/{destination.name}",
                    "label": label,
                    "split": split or "train",
                    "source": source,
                }
            )
    with (output_root / "labels.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["filename", "label", "split", "source"])
        writer.writeheader()
        writer.writerows(rows)
    return output_root


def prepare_detection_dataset(dataset: dict[str, Any] | Any, *, output_dir: str | Path) -> Path:
    raw = _dataset_raw(dataset)
    if raw["kind"] == "yolo":
        output_root = Path(output_dir)
        if output_root.exists():
            shutil.rmtree(output_root)
        shutil.copytree(Path(raw["root"]), output_root)
        return output_root
    if raw["kind"] != "voc":
        raise ValueError("dataset.prepare_detection requires kind=voc or kind=yolo")
    root = Path(raw["root"])
    output_root = Path(output_dir)
    images_dir = output_root / "train" / "images"
    labels_dir = output_root / "train" / "labels"
    images_dir.mkdir(parents=True, exist_ok=True)
    labels_dir.mkdir(parents=True, exist_ok=True)
    classes: dict[str, int] = {}
    images = {path.name: path for path in _find_images(root)}
    for xml_path in root.rglob("*.xml"):
        annotation = ET.parse(xml_path).getroot()
        filename = annotation.findtext("filename") or ""
        image_path = images.get(filename)
        if image_path is None:
            continue
        size = annotation.find("size")
        width = int(float(size.findtext("width", "0"))) if size is not None else 0
        height = int(float(size.findtext("height", "0"))) if size is not None else 0
        label_lines = []
        for obj in annotation.findall("object"):
            name = obj.findtext("name") or "object"
            classes.setdefault(name, len(classes))
            box = obj.find("bndbox")
            if box is None or width <= 0 or height <= 0:
                continue
            xmin = float(box.findtext("xmin", "0"))
            ymin = float(box.findtext("ymin", "0"))
            xmax = float(box.findtext("xmax", "0"))
            ymax = float(box.findtext("ymax", "0"))
            cx = ((xmin + xmax) / 2) / width
            cy = ((ymin + ymax) / 2) / height
            bw = (xmax - xmin) / width
            bh = (ymax - ymin) / height
            label_lines.append(f"{classes[name]} {cx:.8f} {cy:.8f} {bw:.8f} {bh:.8f}")
        shutil.copy2(image_path, images_dir / image_path.name)
        (labels_dir / f"{image_path.stem}.txt").write_text("\n".join(label_lines) + "\n", encoding="utf-8")
    names = {index: name for name, index in classes.items()}
    (output_root / "data.yaml").write_text(
        yaml.safe_dump({"train": "train/images", "val": "train/images", "names": names}, sort_keys=False),
        encoding="utf-8",
    )
    return output_root


def _inspect_image_text(raw: dict[str, Any], output_root: Path) -> dict[str, Any]:
    root = Path(raw["root"])
    labels_path = root / raw.get("labels", "labels.csv")
    image_column = raw.get("image_column", "filename")
    label_column = raw.get("label_column", "label")
    split_column = raw.get("split_column", "split")
    rows = []
    chars = Counter()
    splits = Counter()
    missing = []
    previews = []
    with labels_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            image_value = row[image_column]
            label = row[label_column]
            split = row.get(split_column, "train") if split_column else "train"
            chars.update(label)
            splits[split or "train"] += 1
            image_path = _resolve_image(root, image_value, must_exist=False)
            if not image_path.exists():
                missing.append(image_value)
            elif len(previews) < 16:
                previews.append((image_path, label))
            rows.append(row)
    preview_path = _write_contact_sheet(previews, output_root / "preview_contact_sheet.jpg")
    return {
        "kind": "image_text",
        "root": str(root),
        "labels": str(labels_path),
        "sample_count": len(rows),
        "missing_files": missing,
        "splits": dict(splits),
        "label_characters": "".join(sorted(chars)),
        "character_counts": dict(sorted(chars.items())),
        "preview": str(preview_path) if preview_path else None,
        "warnings": [],
    }


def _inspect_voc(raw: dict[str, Any], output_root: Path) -> dict[str, Any]:
    root = Path(raw["root"])
    object_counts = Counter()
    missing = []
    previews = []
    images = {path.name: path for path in _find_images(root)}
    xml_count = 0
    for xml_path in root.rglob("*.xml"):
        xml_count += 1
        annotation = ET.parse(xml_path).getroot()
        filename = annotation.findtext("filename") or ""
        image_path = images.get(filename)
        if image_path is None:
            missing.append(filename)
        boxes = []
        for obj in annotation.findall("object"):
            name = obj.findtext("name") or "object"
            object_counts[name] += 1
            box = obj.find("bndbox")
            if box is not None:
                boxes.append(
                    (
                        name,
                        tuple(float(box.findtext(key, "0")) for key in ("xmin", "ymin", "xmax", "ymax")),
                    )
                )
        if image_path and len(previews) < 12:
            previews.append((image_path, boxes))
    preview_path = _write_bbox_contact_sheet(previews, output_root / "preview_contact_sheet.jpg")
    return {
        "kind": "voc",
        "root": str(root),
        "image_count": len(images),
        "xml_count": xml_count,
        "object_counts": dict(object_counts),
        "missing_files": missing,
        "preview": str(preview_path) if preview_path else None,
        "warnings": [],
    }


def _inspect_yolo(raw: dict[str, Any], output_root: Path) -> dict[str, Any]:
    root = Path(raw["root"])
    data_yaml = root / raw.get("data_yaml", "data.yaml")
    data = yaml.safe_load(data_yaml.read_text(encoding="utf-8"))
    names_raw = data.get("names", [])
    if isinstance(names_raw, dict):
        names = [str(names_raw[index]) for index in sorted(names_raw)]
    else:
        names = [str(name) for name in names_raw]
    class_counts = Counter()
    split_counts = Counter()
    label_files = 0
    bad_lines = []
    previews = []
    for split in ("train", "valid", "val", "test"):
        label_dir = root / split / "labels"
        image_dir = root / split / "images"
        if not label_dir.exists() and not image_dir.exists():
            continue
        for image_path in _find_images(image_dir):
            split_counts[split] += 1
            if len(previews) < 12:
                boxes = _read_yolo_boxes(image_path, label_dir / f"{image_path.stem}.txt", names)
                previews.append((image_path, boxes))
        for label_path in label_dir.glob("*.txt") if label_dir.exists() else []:
            label_files += 1
            for line in label_path.read_text(encoding="utf-8").splitlines():
                if not line.strip():
                    continue
                parts = line.split()
                if len(parts) != 5:
                    bad_lines.append(f"{label_path.name}:{line}")
                    continue
                class_index = int(float(parts[0]))
                class_name = names[class_index] if 0 <= class_index < len(names) else str(class_index)
                class_counts[class_name] += 1
    warnings = []
    if "66" in class_counts:
        warnings.append(
            {
                "code": "suspicious_yolo_class",
                "message": "YOLO class '66' is rare/suspicious and should be audited before training.",
                "count": class_counts["66"],
            }
        )
    if bad_lines:
        warnings.append({"code": "bad_yolo_label_lines", "message": "Some YOLO label lines are malformed.", "count": len(bad_lines)})
    preview_path = _write_bbox_contact_sheet(previews, output_root / "preview_contact_sheet.jpg")
    return {
        "kind": "yolo",
        "root": str(root),
        "data_yaml": str(data_yaml),
        "classes": names,
        "image_count": sum(split_counts.values()),
        "label_files": label_files,
        "splits": dict(split_counts),
        "class_counts": dict(class_counts),
        "preview": str(preview_path) if preview_path else None,
        "warnings": warnings,
    }


def _read_yolo_boxes(image_path: Path, label_path: Path, names: list[str]) -> list[tuple[str, tuple[float, float, float, float]]]:
    if not label_path.exists():
        return []
    with Image.open(image_path) as image:
        width, height = image.size
    boxes = []
    for line in label_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) != 5:
            continue
        class_index = int(float(parts[0]))
        cx, cy, bw, bh = (float(value) for value in parts[1:])
        xmin = (cx - bw / 2) * width
        ymin = (cy - bh / 2) * height
        xmax = (cx + bw / 2) * width
        ymax = (cy + bh / 2) * height
        label = names[class_index] if 0 <= class_index < len(names) else str(class_index)
        boxes.append((label, (xmin, ymin, xmax, ymax)))
    return boxes


def _dataset_raw(dataset: dict[str, Any] | Any) -> dict[str, Any]:
    if isinstance(dataset, dict):
        return dict(dataset)
    return {
        "kind": dataset.kind,
        "root": str(dataset.root),
        "labels": dataset.labels,
        "image_column": dataset.image_column,
        "label_column": dataset.label_column,
        "split_column": dataset.split_column,
        "data_yaml": dataset.data_yaml,
        "source": dataset.source,
    }


def _find_images(root: Path) -> list[Path]:
    if not root.exists():
        return []
    return sorted(path for path in root.rglob("*") if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS)


def _resolve_image(root: Path, image_value: str, *, must_exist: bool = True) -> Path:
    path = Path(image_value)
    candidates = [path if path.is_absolute() else root / path]
    if not path.is_absolute():
        candidates.append(root / "images" / path.name)
    for candidate in candidates:
        if candidate.exists():
            return candidate
    if must_exist:
        raise FileNotFoundError(f"image not found: {image_value}")
    return candidates[0]


def _write_contact_sheet(samples: list[tuple[Path, str]], path: Path) -> Path | None:
    if not samples:
        return None
    path.parent.mkdir(parents=True, exist_ok=True)
    cells = []
    for image_path, label in samples:
        image = Image.open(image_path).convert("RGB")
        image.thumbnail((180, 120))
        cell = Image.new("RGB", (200, 160), "white")
        cell.paste(image, ((200 - image.width) // 2, 12))
        draw = ImageDraw.Draw(cell)
        draw.text((8, 136), str(label)[:28], fill=(20, 20, 20))
        cells.append(cell)
    _save_grid(cells, path)
    return path


def _write_bbox_contact_sheet(samples: list[tuple[Path, list[tuple[str, tuple[float, float, float, float]]]]], path: Path) -> Path | None:
    if not samples:
        return None
    path.parent.mkdir(parents=True, exist_ok=True)
    cells = []
    for image_path, boxes in samples:
        image = Image.open(image_path).convert("RGB")
        original_w, original_h = image.size
        image.thumbnail((200, 140))
        scale_x = image.width / max(1, original_w)
        scale_y = image.height / max(1, original_h)
        cell = Image.new("RGB", (220, 180), "white")
        x0 = (220 - image.width) // 2
        y0 = 8
        cell.paste(image, (x0, y0))
        draw = ImageDraw.Draw(cell)
        for label, (xmin, ymin, xmax, ymax) in boxes:
            rect = [x0 + xmin * scale_x, y0 + ymin * scale_y, x0 + xmax * scale_x, y0 + ymax * scale_y]
            draw.rectangle(rect, outline="lime", width=2)
            draw.text((rect[0], max(0, rect[1] - 10)), label, fill="lime")
        draw.text((8, 158), image_path.name[:30], fill=(20, 20, 20))
        cells.append(cell)
    _save_grid(cells, path)
    return path


def _save_grid(cells: list[Image.Image], path: Path) -> None:
    columns = min(4, len(cells))
    rows = (len(cells) + columns - 1) // columns
    width = max(cell.width for cell in cells)
    height = max(cell.height for cell in cells)
    canvas = Image.new("RGB", (columns * width, rows * height), (240, 240, 240))
    for index, cell in enumerate(cells):
        canvas.paste(cell, ((index % columns) * width, (index // columns) * height))
    canvas.save(path, quality=90)
