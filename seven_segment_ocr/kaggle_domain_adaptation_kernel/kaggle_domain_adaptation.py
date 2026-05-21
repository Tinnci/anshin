"""
Kaggle domain-adaptation workflow for seven-segment OCR.

This script trains the Android-deployable LightSVTR student on a mixed dataset:

1. Real labeled LCD crops/photos from a Kaggle dataset.
2. Hardened synthetic samples generated from this repository.
3. Optional pseudo labels from an offline OCR teacher.

Expected real labeled dataset layout:

    /kaggle/input/<dataset>/
      labels.csv
      images/
        img_001.jpg

`labels.csv` columns:

    filename,label[,split]

`split` is optional. If missing, a stable hash split is used. Supported values:
`train`, `val`, `test`.

Why Kaggle GPU instead of TPU:

- PaddleOCR and PyTorch/ONNX tooling are GPU-native.
- Synthetic generation is CPU-bound, while the student training loop benefits
  from T4/P100/L4 GPUs.
- TPU/XLA adds compile/debug overhead and is not helpful for PaddleOCR.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import random
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image, ImageOps
from torch.utils.data import DataLoader, Dataset


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

try:
    from generate_data import generate_sequence_dataset  # type: ignore  # noqa: E402
    from light_svtr import LightSVTR, export_onnx  # type: ignore  # noqa: E402
except ModuleNotFoundError:
    # `kaggle kernels push -p kaggle_domain_adaptation_kernel` uploads this
    # folder only. Fetch the shared training modules from the repository unless
    # the user attached the full repo as a Kaggle dataset.
    import urllib.request

    raw_base = os.getenv(
        "MEDLOG_RAW_BASE",
        "https://raw.githubusercontent.com/Tinnci/anshin/master/seven_segment_ocr",
    )
    for module_name in ["generate_data.py", "light_svtr.py"]:
        target = SCRIPT_DIR / module_name
        if not target.exists():
            url = f"{raw_base}/{module_name}"
            print(f"downloading {url}")
            urllib.request.urlretrieve(url, target)
    if str(SCRIPT_DIR) not in sys.path:
        sys.path.insert(0, str(SCRIPT_DIR))
    from generate_data import generate_sequence_dataset  # type: ignore  # noqa: E402
    from light_svtr import LightSVTR, export_onnx  # type: ignore  # noqa: E402


CHARS = "0123456789/. -\n"
BLANK = 0
CHAR_TO_IDX = {ch: i + 1 for i, ch in enumerate(CHARS)}
IDX_TO_CHAR = {i + 1: ch for i, ch in enumerate(CHARS)}
NUM_CLASSES = len(CHARS) + 1


@dataclass(frozen=True)
class Sample:
    image_path: Path
    label: str
    split: str
    source: str


def clean_label(label: str) -> str:
    """Keep only characters supported by the Android CTC decoder."""
    label = label.replace("\\n", "\n")
    label = label.replace("|", "\n")
    label = re.sub(r"\s+", " ", label)
    cleaned = "".join(ch for ch in label if ch in CHAR_TO_IDX)
    return cleaned.strip(" /. -\n")


def encode_label(text: str) -> list[int]:
    return [CHAR_TO_IDX[ch] for ch in text if ch in CHAR_TO_IDX]


def decode_prediction(indices: list[int]) -> str:
    result: list[str] = []
    prev = BLANK
    for idx in indices:
        if idx != BLANK and idx != prev:
            ch = IDX_TO_CHAR.get(idx)
            if ch is not None:
                result.append(ch)
        prev = idx
    text = "".join(result)
    lines = []
    for line in text.split("\n"):
        line = re.sub(r"\s{2,}", " ", line.strip())
        line = line.strip("/.- ")
        line = re.sub(r"([/.\-])\1+", r"\1", line)
        if line:
            lines.append(line)
    return "\n".join(lines)


def stable_split(name: str) -> str:
    bucket = int(hashlib.sha1(name.encode("utf-8")).hexdigest()[:8], 16) % 100
    if bucket < 80:
        return "train"
    if bucket < 90:
        return "val"
    return "test"


def read_labeled_dataset(root: Path, source: str) -> list[Sample]:
    labels_csv = root / "labels.csv"
    if not labels_csv.exists():
        return []

    samples: list[Sample] = []
    with labels_csv.open(newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            filename = row.get("filename") or row.get("image") or row.get("path")
            label = clean_label(row.get("label", ""))
            if not filename or not label:
                continue
            image_path = root / filename
            if not image_path.exists():
                image_path = root / "images" / filename
            if not image_path.exists():
                print(f"skip missing image: {filename}")
                continue
            split = (row.get("split") or stable_split(filename)).strip().lower()
            if split not in {"train", "val", "test"}:
                split = stable_split(filename)
            samples.append(Sample(image_path=image_path, label=label, split=split, source=source))
    return samples


def find_real_dataset() -> Path | None:
    configured = os.getenv("REAL_DATA_DIR")
    if configured:
        path = Path(configured)
        return path if (path / "labels.csv").exists() else None

    kaggle_input = Path("/kaggle/input")
    if not kaggle_input.exists():
        return None
    for labels_csv in kaggle_input.glob("**/labels.csv"):
        return labels_csv.parent
    return None


def generate_synthetic_dataset(
    output_dir: Path,
    num_samples: int,
    real_world_ratio: float,
    seed: int,
) -> list[Sample]:
    if num_samples <= 0:
        return []
    dataset_dir = output_dir / "synthetic" / "sequence"
    if not (dataset_dir / "sequences.csv").exists():
        random.seed(seed)
        np.random.seed(seed)
        generate_sequence_dataset(
            dataset_dir,
            num_samples=num_samples,
            real_world_ratio=real_world_ratio,
        )

    samples: list[Sample] = []
    with (dataset_dir / "sequences.csv").open(newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            filename = row["filename"]
            label = clean_label(row["label"])
            split = stable_split("synthetic/" + filename)
            samples.append(
                Sample(
                    image_path=dataset_dir / "images" / filename,
                    label=label,
                    split=split,
                    source="synthetic",
                )
            )
    return samples


def write_ppocr_rec_files(samples: list[Sample], output_dir: Path) -> None:
    """Write PaddleOCR SimpleDataSet label files for PP-OCRv5 recognition fine-tune."""
    ppocr_dir = output_dir / "ppocr_rec"
    ppocr_dir.mkdir(parents=True, exist_ok=True)

    for split in ["train", "val", "test"]:
        rows = [s for s in samples if s.split == split]
        with (ppocr_dir / f"rec_{split}.txt").open("w") as f:
            for s in rows:
                f.write(f"{s.image_path}\t{s.label.replace(chr(10), ' ')}\n")

    with (ppocr_dir / "seven_segment_dict.txt").open("w") as f:
        for ch in CHARS.replace("\n", ""):
            f.write(ch + "\n")

    commands = [
        "# Optional PP-OCRv5 teacher fine-tuning commands for a Kaggle notebook:",
        "git clone --depth 1 https://github.com/PaddlePaddle/PaddleOCR.git /kaggle/working/PaddleOCR",
        "cd /kaggle/working/PaddleOCR",
        "python -m pip install -r requirements.txt",
        "# Download PP-OCRv5_mobile_rec pretrained weights, then edit a rec config to use:",
        f"#   Train.dataset.label_file_list=[{ppocr_dir / 'rec_train.txt'}]",
        f"#   Eval.dataset.label_file_list=[{ppocr_dir / 'rec_val.txt'}]",
        f"#   Global.character_dict_path={ppocr_dir / 'seven_segment_dict.txt'}",
        "# Then run PaddleOCR tools/train.py with Global.pretrained_model=<PP-OCRv5_mobile_rec pretrained path>.",
    ]
    (ppocr_dir / "PPOCR_FINE_TUNE_COMMANDS.txt").write_text("\n".join(commands) + "\n")
    print(f"PP-OCR recognition files written to {ppocr_dir}")


class SevenSegmentManifestDataset(Dataset):
    def __init__(self, samples: list[Sample], target_h: int = 128, max_w: int = 256):
        self.samples = samples
        self.target_h = target_h
        self.max_w = max_w

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int):
        sample = self.samples[idx]
        img = Image.open(sample.image_path)
        img = ImageOps.exif_transpose(img).convert("L")
        ratio = self.target_h / img.height
        new_w = max(8, min(int(img.width * ratio), self.max_w))
        img = img.resize((new_w, self.target_h), Image.LANCZOS)
        padded = Image.new("L", (self.max_w, self.target_h), 0)
        padded.paste(img, (0, 0))
        arr = np.array(padded, dtype=np.float32) / 255.0
        tensor = torch.from_numpy(arr).unsqueeze(0)
        encoded = encode_label(sample.label)
        return tensor, torch.tensor(encoded, dtype=torch.long), len(encoded), sample.label


def ctc_collate(batch):
    images, labels, label_lengths, texts = zip(*batch)
    return (
        torch.stack(images, 0),
        torch.cat(labels, 0),
        torch.tensor(label_lengths, dtype=torch.long),
        list(texts),
    )


def evaluate(model: nn.Module, loader: DataLoader, device: torch.device) -> dict:
    model.eval()
    criterion = nn.CTCLoss(blank=BLANK, zero_infinity=True)
    total_loss = 0.0
    batches = 0
    exact = 0
    total = 0
    examples = []

    with torch.no_grad():
        for images, labels, label_lengths, texts in loader:
            images = images.to(device)
            labels = labels.to(device)
            label_lengths = label_lengths.to(device)
            output = model(images)
            time_steps, batch_size = output.size(0), output.size(1)
            input_lengths = torch.full(
                (batch_size,), time_steps, dtype=torch.long, device=device
            )
            log_probs = F.log_softmax(output, dim=2)
            loss = criterion(log_probs, labels, input_lengths, label_lengths)
            total_loss += float(loss.item())
            batches += 1

            preds = output.argmax(dim=2)
            for b, truth in enumerate(texts):
                pred = decode_prediction(preds[:, b].cpu().tolist())
                if pred == truth:
                    exact += 1
                elif len(examples) < 8:
                    examples.append({"truth": truth, "pred": pred})
                total += 1

    return {
        "loss": total_loss / max(1, batches),
        "exact": exact / max(1, total),
        "errors": examples,
    }


def train_student(
    samples: list[Sample],
    output_dir: Path,
    epochs: int,
    batch_size: int,
    lr: float,
    real_weight: int,
) -> Path:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    train_samples = [s for s in samples if s.split == "train"]
    val_samples = [s for s in samples if s.split == "val"]
    test_samples = [s for s in samples if s.split == "test"]

    weighted_train: list[Sample] = []
    for sample in train_samples:
        repeat = real_weight if sample.source == "real" else 1
        weighted_train.extend([sample] * repeat)

    if not weighted_train:
        raise RuntimeError("No training samples found")
    if not val_samples:
        raise RuntimeError("No validation samples found")

    train_loader = DataLoader(
        SevenSegmentManifestDataset(weighted_train),
        batch_size=batch_size,
        shuffle=True,
        collate_fn=ctc_collate,
        num_workers=2,
        pin_memory=torch.cuda.is_available(),
    )
    val_loader = DataLoader(
        SevenSegmentManifestDataset(val_samples),
        batch_size=batch_size,
        shuffle=False,
        collate_fn=ctc_collate,
        num_workers=1,
        pin_memory=torch.cuda.is_available(),
    )
    test_loader = DataLoader(
        SevenSegmentManifestDataset(test_samples or val_samples),
        batch_size=batch_size,
        shuffle=False,
        collate_fn=ctc_collate,
        num_workers=1,
        pin_memory=torch.cuda.is_available(),
    )

    model = LightSVTR(num_classes=NUM_CLASSES).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=max(1, epochs), eta_min=lr * 0.05
    )
    criterion = nn.CTCLoss(blank=BLANK, zero_infinity=True)

    output_dir.mkdir(parents=True, exist_ok=True)
    best_path = output_dir / "svtr_domain_best.pth"
    best_exact = -1.0
    history = []

    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        batches = 0
        for images, labels, label_lengths, _ in train_loader:
            images = images.to(device)
            labels = labels.to(device)
            label_lengths = label_lengths.to(device)
            output = model(images)
            time_steps, batch_size_actual = output.size(0), output.size(1)
            input_lengths = torch.full(
                (batch_size_actual,), time_steps, dtype=torch.long, device=device
            )
            loss = criterion(F.log_softmax(output, dim=2), labels, input_lengths, label_lengths)

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            optimizer.step()
            total_loss += float(loss.item())
            batches += 1

        scheduler.step()
        val = evaluate(model, val_loader, device)
        row = {
            "epoch": epoch + 1,
            "train_loss": total_loss / max(1, batches),
            "val_loss": val["loss"],
            "val_exact": val["exact"],
            "lr": scheduler.get_last_lr()[0],
        }
        history.append(row)
        print(json.dumps(row, ensure_ascii=False))

        if val["exact"] > best_exact:
            best_exact = val["exact"]
            torch.save(model.state_dict(), best_path)
            print(f"saved best: {best_path}")

    model.load_state_dict(torch.load(best_path, map_location=device, weights_only=True))
    test = evaluate(model, test_loader, device)
    print("test:", json.dumps(test, ensure_ascii=False, indent=2))
    (output_dir / "training_history.json").write_text(
        json.dumps({"history": history, "test": test}, ensure_ascii=False, indent=2)
    )

    model.cpu()
    export_onnx(model, str(output_dir / "svtr_seven_seg_domain.onnx"), img_h=128, img_w=256)
    return output_dir / "svtr_seven_seg_domain.onnx"


def main() -> None:
    parser = argparse.ArgumentParser(description="Seven-segment OCR Kaggle domain adaptation")
    parser.add_argument("--output-dir", default="/kaggle/working/domain_adaptation")
    parser.add_argument("--synthetic-samples", type=int, default=30000)
    parser.add_argument("--real-world-ratio", type=float, default=0.45)
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=5e-4)
    parser.add_argument("--real-weight", type=int, default=4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--prepare-ppocr-only", action="store_true")
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    samples: list[Sample] = []
    real_root = find_real_dataset()
    if real_root:
        real_samples = read_labeled_dataset(real_root, source="real")
        samples.extend(real_samples)
        print(f"real dataset: {real_root} ({len(real_samples)} samples)")
    else:
        print("no real labeled dataset found; training will use synthetic only")

    synthetic = generate_synthetic_dataset(
        output_dir=output_dir,
        num_samples=args.synthetic_samples,
        real_world_ratio=args.real_world_ratio,
        seed=args.seed,
    )
    samples.extend(synthetic)
    print(f"synthetic samples: {len(synthetic)}")

    counts = {}
    for sample in samples:
        key = f"{sample.source}/{sample.split}"
        counts[key] = counts.get(key, 0) + 1
    print("sample counts:", json.dumps(counts, ensure_ascii=False, indent=2))

    write_ppocr_rec_files(samples, output_dir)
    if args.prepare_ppocr_only:
        return

    onnx_path = train_student(
        samples=samples,
        output_dir=output_dir,
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        real_weight=args.real_weight,
    )
    print(f"done: {onnx_path}")


if __name__ == "__main__":
    main()
