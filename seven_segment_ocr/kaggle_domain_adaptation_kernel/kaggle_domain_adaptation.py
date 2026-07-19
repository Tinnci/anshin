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
import platform
import random
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image, ImageOps
from torch.utils.data import DataLoader, Dataset


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
DEFAULT_OUTPUT_DIR = Path("/kaggle/working/domain_adaptation")
MIN_CUDA_CAPABILITY = (7, 0)
MODEL_VARIANTS = {
    "tiny": {
        "d_model": 96,
        "nhead": 4,
        "num_layers": 2,
        "dim_feedforward": 384,
        "dropout": 0.1,
    },
    "base": {
        "d_model": 128,
        "nhead": 4,
        "num_layers": 3,
        "dim_feedforward": 512,
        "dropout": 0.1,
    },
    "large": {
        "d_model": 192,
        "nhead": 6,
        "num_layers": 4,
        "dim_feedforward": 768,
        "dropout": 0.1,
    },
}
_RUN_LOG_FILE: TextIO | None = None
_STARTUP_OUTPUT_DIR: Path | None = None
_STARTUP_RUNTIME_INFO: dict | None = None


class TeeStream:
    def __init__(self, primary: TextIO, log_file: TextIO):
        self.primary = primary
        self.log_file = log_file

    def write(self, text: str) -> int:
        written = self.primary.write(text)
        self.log_file.write(text)
        return written

    def flush(self) -> None:
        self.primary.flush()
        self.log_file.flush()

    def isatty(self) -> bool:
        return self.primary.isatty()

    def fileno(self) -> int:
        return self.primary.fileno()

    def __getattr__(self, name: str):
        return getattr(self.primary, name)


def make_tee_streams(
    stdout: TextIO,
    stderr: TextIO,
    log_file: TextIO,
) -> tuple[TeeStream, TeeStream]:
    return TeeStream(stdout, log_file), TeeStream(stderr, log_file)


def parse_early_output_dir(argv: list[str]) -> Path:
    for i, arg in enumerate(argv):
        if arg == "--output-dir" and i + 1 < len(argv):
            return Path(argv[i + 1])
        if arg.startswith("--output-dir="):
            return Path(arg.split("=", 1)[1])
    return DEFAULT_OUTPUT_DIR


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2))


def collect_runtime_info() -> dict:
    """Collect Kaggle hardware/runtime details for reproducible Eval reports."""
    info: dict = {
        "python": sys.version.replace("\n", " "),
        "platform": platform.platform(),
        "torch": torch.__version__,
        "cuda_available": torch.cuda.is_available(),
        "cuda_device_count": torch.cuda.device_count(),
        "cuda_visible_devices": os.getenv("CUDA_VISIBLE_DEVICES"),
        "kaggle_kernel_run_type": os.getenv("KAGGLE_KERNEL_RUN_TYPE"),
        "kaggle_url_base": os.getenv("KAGGLE_URL_BASE"),
        "tpu_env": {
            key: os.getenv(key)
            for key in ["TPU_NAME", "TPU_WORKER_ID", "XRT_TPU_CONFIG", "PJRT_DEVICE"]
            if os.getenv(key)
        },
    }
    if torch.cuda.is_available():
        info["cuda"] = torch.version.cuda
        info["cudnn"] = torch.backends.cudnn.version()
        info["gpu_devices"] = [
            {
                "index": i,
                "name": torch.cuda.get_device_name(i),
                "capability": torch.cuda.get_device_capability(i),
                "memory_gb": round(
                    torch.cuda.get_device_properties(i).total_memory / (1024**3),
                    2,
                ),
            }
            for i in range(torch.cuda.device_count())
        ]
    try:
        result = subprocess.run(
            [
                "nvidia-smi",
                "--query-gpu=name,memory.total,driver_version",
                "--format=csv,noheader",
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        info["nvidia_smi"] = result.stdout.strip() or result.stderr.strip()
    except Exception as exc:
        info["nvidia_smi"] = f"unavailable: {type(exc).__name__}: {exc}"
    return info


def write_startup_artifacts(
    output_dir: Path,
    runtime_info: dict | None = None,
    install_tee: bool = True,
) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    run_log = output_dir / "run.log"
    if install_tee:
        install_run_log_tee(output_dir)
    else:
        run_log.touch(exist_ok=True)

    runtime = runtime_info if runtime_info is not None else collect_runtime_info()
    write_json(output_dir / "runtime_report.json", runtime)
    if install_tee:
        print("runtime:", json.dumps(runtime, ensure_ascii=False, indent=2), flush=True)
    return run_log


def install_run_log_tee(output_dir: Path) -> Path:
    global _RUN_LOG_FILE
    output_dir.mkdir(parents=True, exist_ok=True)
    run_log = output_dir / "run.log"
    if getattr(sys.stdout, "_medlog_run_log_path", None) == run_log:
        return run_log

    _RUN_LOG_FILE = run_log.open("a", encoding="utf-8", buffering=1)
    tee_stdout, tee_stderr = make_tee_streams(sys.stdout, sys.stderr, _RUN_LOG_FILE)
    tee_stdout._medlog_run_log_path = run_log
    tee_stderr._medlog_run_log_path = run_log
    sys.stdout = tee_stdout
    sys.stderr = tee_stderr
    print(f"run log: {run_log}", flush=True)
    return run_log


def find_unsupported_cuda_devices(runtime_info: dict) -> list[dict]:
    if not runtime_info.get("cuda_available"):
        return []
    unsupported = []
    for device in runtime_info.get("gpu_devices", []):
        capability = tuple(device.get("capability") or ())
        if capability and capability < MIN_CUDA_CAPABILITY:
            unsupported.append(device)
    return unsupported


def assert_runtime_supported(runtime_info: dict) -> None:
    unsupported = find_unsupported_cuda_devices(runtime_info)
    if not unsupported:
        return
    devices = ", ".join(
        f"{device.get('name', 'unknown')} sm_{device.get('capability', ['?', '?'])[0]}"
        f"{device.get('capability', ['?', '?'])[1]}"
        for device in unsupported
    )
    raise RuntimeError(
        "Current Kaggle PyTorch/CUDA runtime does not support these GPU architectures: "
        f"{devices}. Use a T4/L4/A100/H100 runtime, for example "
        "`kaggle kernels push -p kaggle_domain_adaptation_kernel --accelerator NvidiaTeslaT4`."
    )


def get_model_variant_config(model_variant: str) -> dict:
    try:
        return dict(MODEL_VARIANTS[model_variant])
    except KeyError as exc:
        valid = ", ".join(sorted(MODEL_VARIANTS))
        raise ValueError(f"Unknown model variant: {model_variant}. Valid variants: {valid}") from exc


def cleanup_synthetic_images(output_dir: Path, keep_synthetic_images: bool) -> int:
    if keep_synthetic_images:
        return 0
    image_dir = output_dir / "synthetic" / "sequence" / "images"
    if not image_dir.exists():
        return 0
    removed = sum(1 for path in image_dir.rglob("*") if path.is_file())
    shutil.rmtree(image_dir)
    print(f"removed synthetic image output bloat: {removed} files from {image_dir}", flush=True)
    return removed


if __name__ == "__main__":
    try:
        _STARTUP_OUTPUT_DIR = parse_early_output_dir(sys.argv[1:])
        _STARTUP_RUNTIME_INFO = collect_runtime_info()
        write_startup_artifacts(
            _STARTUP_OUTPUT_DIR,
            runtime_info=_STARTUP_RUNTIME_INFO,
            install_tee=True,
        )
    except Exception as exc:
        print(f"failed to initialize startup artifacts: {type(exc).__name__}: {exc}", file=sys.stderr)


if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

try:
    from generate_data import generate_sequence_dataset  # type: ignore  # noqa: E402
    from light_svtr import LightSVTR, export_onnx  # type: ignore  # noqa: E402
except ModuleNotFoundError:
    # `kaggle kernels push -p kaggle_domain_adaptation_kernel` uploads this
    # folder only. Fetch the shared training modules from the repository unless
    # the user attached the full repo as a Kaggle dataset. Kaggle mounts the
    # uploaded source directory read-only, so downloaded modules must go under
    # `/kaggle/working`.
    import urllib.request

    module_dir = Path(os.getenv("MEDLOG_MODULE_DIR", "/kaggle/working/medlog_ocr_modules"))
    module_dir.mkdir(parents=True, exist_ok=True)
    raw_base = os.getenv(
        "MEDLOG_RAW_BASE",
        "https://raw.githubusercontent.com/Tinnci/anshin/main/seven_segment_ocr",
    )
    for module_name in ["generate_data.py", "light_svtr.py"]:
        target = module_dir / module_name
        if not target.exists():
            url = f"{raw_base}/{module_name}"
            print(f"downloading {url}")
            urllib.request.urlretrieve(url, target)
    if str(module_dir) not in sys.path:
        sys.path.insert(0, str(module_dir))
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
        return (
            tensor,
            torch.tensor(encoded, dtype=torch.long),
            len(encoded),
            sample.label,
            sample.source,
        )


def ctc_collate(batch):
    images, labels, label_lengths, texts, sources = zip(*batch)
    return (
        torch.stack(images, 0),
        torch.cat(labels, 0),
        torch.tensor(label_lengths, dtype=torch.long),
        list(texts),
        list(sources),
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
        by_source: dict[str, dict[str, int]] = {}
        for images, labels, label_lengths, texts, sources in loader:
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
                source = sources[b]
                by_source.setdefault(source, {"correct": 0, "total": 0})
                by_source[source]["total"] += 1
                if pred == truth:
                    exact += 1
                    by_source[source]["correct"] += 1
                elif len(examples) < 8:
                    examples.append({"source": source, "truth": truth, "pred": pred})
                total += 1

    return {
        "loss": total_loss / max(1, batches),
        "exact": exact / max(1, total),
        "by_source": {
            source: {
                "exact": values["correct"] / max(1, values["total"]),
                "correct": values["correct"],
                "total": values["total"],
            }
            for source, values in sorted(by_source.items())
        },
        "errors": examples,
    }


def train_student(
    samples: list[Sample],
    output_dir: Path,
    epochs: int,
    batch_size: int,
    lr: float,
    real_weight: int,
    model_variant: str,
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

    model_config = get_model_variant_config(model_variant)
    print("model variant:", json.dumps({"name": model_variant, **model_config}, ensure_ascii=False))
    model = LightSVTR(num_classes=NUM_CLASSES, **model_config).to(device)
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
        for images, labels, label_lengths, _, _ in train_loader:
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
    val = evaluate(model, val_loader, device)
    test = evaluate(model, test_loader, device)
    print("val:", json.dumps(val, ensure_ascii=False, indent=2))
    print("test:", json.dumps(test, ensure_ascii=False, indent=2))
    write_json(
        output_dir / "training_history.json",
        {"history": history, "val": val, "test": test},
    )
    write_json(
        output_dir / "evaluation_report.json",
        {
            "runtime": collect_runtime_info(),
            "model_variant": {"name": model_variant, **model_config},
            "sample_counts": count_samples(samples),
            "validation": val,
            "test": test,
        },
    )

    model.cpu()
    export_onnx(model, str(output_dir / "svtr_seven_seg_domain.onnx"), img_h=128, img_w=256)
    return output_dir / "svtr_seven_seg_domain.onnx"


def count_samples(samples: list[Sample]) -> dict:
    counts = {}
    for sample in samples:
        key = f"{sample.source}/{sample.split}"
        counts[key] = counts.get(key, 0) + 1
    return dict(sorted(counts.items()))


def evaluate_checkpoint(
    samples: list[Sample],
    output_dir: Path,
    model_path: Path,
    batch_size: int,
    model_variant: str,
) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model_config = get_model_variant_config(model_variant)
    model = LightSVTR(num_classes=NUM_CLASSES, **model_config).to(device)
    model.load_state_dict(torch.load(model_path, map_location=device, weights_only=True))

    report: dict = {
        "runtime": collect_runtime_info(),
        "model_variant": {"name": model_variant, **model_config},
        "model_path": str(model_path),
        "sample_counts": count_samples(samples),
    }
    for split in ["val", "test"]:
        split_samples = [s for s in samples if s.split == split]
        if not split_samples:
            continue
        loader = DataLoader(
            SevenSegmentManifestDataset(split_samples),
            batch_size=batch_size,
            shuffle=False,
            collate_fn=ctc_collate,
            num_workers=1,
            pin_memory=torch.cuda.is_available(),
        )
        report[split] = evaluate(model, loader, device)
    write_json(output_dir / "evaluation_report.json", report)
    print("evaluation:", json.dumps(report, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description="Seven-segment OCR Kaggle domain adaptation")
    parser.add_argument("--output-dir", default="/kaggle/working/domain_adaptation")
    parser.add_argument("--synthetic-samples", type=int, default=30000)
    parser.add_argument("--real-world-ratio", type=float, default=0.45)
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=5e-4)
    parser.add_argument("--real-weight", type=int, default=4)
    parser.add_argument("--model-variant", choices=sorted(MODEL_VARIANTS), default="base")
    parser.add_argument("--keep-synthetic-images", action="store_true")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--eval-only", action="store_true")
    parser.add_argument("--model-path", type=str, default="")
    parser.add_argument("--prepare-ppocr-only", action="store_true")
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    if _STARTUP_OUTPUT_DIR == output_dir and _STARTUP_RUNTIME_INFO is not None:
        runtime_info = _STARTUP_RUNTIME_INFO
    else:
        runtime_info = collect_runtime_info()
        write_json(output_dir / "runtime_report.json", runtime_info)
        print("runtime:", json.dumps(runtime_info, ensure_ascii=False, indent=2))
    assert_runtime_supported(runtime_info)

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

    counts = count_samples(samples)
    print("sample counts:", json.dumps(counts, ensure_ascii=False, indent=2))

    write_ppocr_rec_files(samples, output_dir)
    if args.prepare_ppocr_only:
        return
    if args.eval_only:
        if not args.model_path:
            raise RuntimeError("--eval-only requires --model-path")
        evaluate_checkpoint(
            samples=samples,
            output_dir=output_dir,
            model_path=Path(args.model_path),
            batch_size=args.batch_size,
            model_variant=args.model_variant,
        )
        cleanup_synthetic_images(output_dir, keep_synthetic_images=args.keep_synthetic_images)
        return

    onnx_path = train_student(
        samples=samples,
        output_dir=output_dir,
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        real_weight=args.real_weight,
        model_variant=args.model_variant,
    )
    cleanup_synthetic_images(output_dir, keep_synthetic_images=args.keep_synthetic_images)
    print(f"done: {onnx_path}")


if __name__ == "__main__":
    main()
