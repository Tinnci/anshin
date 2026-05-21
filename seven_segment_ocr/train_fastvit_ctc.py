"""Two-stage fine-tuning for FastViT-T8 + CTC seven-segment OCR."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image, ImageOps
from torch.utils.data import DataLoader, Dataset

from fastvit_ctc import (
    IMAGENET_MEAN,
    IMAGENET_STD,
    build_fastvit_t8_ctc,
    export_fastvit_onnx,
    set_backbone_trainable,
)
from ocr_model_eval import CHARSET, ctc_decode, load_labeled_dataset, summarize_text_metrics


BLANK = 0
CHAR_TO_IDX = {char: index + 1 for index, char in enumerate(CHARSET)}
NUM_CLASSES = len(CHARSET) + 1


def preprocess_fastvit_image(image_path: str | Path, *, img_h: int = 128, img_w: int = 256) -> torch.Tensor:
    image = ImageOps.exif_transpose(Image.open(image_path)).convert("RGB")
    ratio = img_h / max(1, image.height)
    new_width = max(1, min(img_w, int(round(image.width * ratio))))
    image = image.resize((new_width, img_h), Image.Resampling.BILINEAR)
    padded = Image.new("RGB", (img_w, img_h), (0, 0, 0))
    padded.paste(image, (0, 0))
    arr = np.asarray(padded, dtype=np.float32) / 255.0
    chw = arr.transpose(2, 0, 1)
    mean = np.asarray(IMAGENET_MEAN, dtype=np.float32).reshape(3, 1, 1)
    std = np.asarray(IMAGENET_STD, dtype=np.float32).reshape(3, 1, 1)
    return torch.from_numpy(((chw - mean) / std).astype(np.float32))


class FastViTDataset(Dataset):
    def __init__(self, samples):
        self.samples = list(samples)

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int):
        sample = self.samples[index]
        image = preprocess_fastvit_image(sample.image_path)
        label = torch.tensor([CHAR_TO_IDX[ch] for ch in sample.label if ch in CHAR_TO_IDX], dtype=torch.long)
        return image, label, sample.label, sample.filename


def ctc_collate(batch):
    images, labels, texts, filenames = zip(*batch)
    label_lengths = torch.tensor([len(label) for label in labels], dtype=torch.long)
    labels_cat = torch.cat(labels) if labels else torch.empty(0, dtype=torch.long)
    return torch.stack(images), labels_cat, label_lengths, list(texts), list(filenames)


def split_samples(samples):
    train = [s for i, s in enumerate(samples) if i % 10 not in {0, 1}]
    val = [s for i, s in enumerate(samples) if i % 10 == 0]
    test = [s for i, s in enumerate(samples) if i % 10 == 1]
    return train or samples, val or samples, test or val or samples


def evaluate_model(model, loader, device) -> dict[str, object]:
    model.eval()
    criterion = torch.nn.CTCLoss(blank=BLANK, zero_infinity=True)
    total_loss = 0.0
    batches = 0
    pairs = []
    with torch.no_grad():
        for images, labels, label_lengths, texts, _ in loader:
            images = images.to(device)
            labels = labels.to(device)
            label_lengths = label_lengths.to(device)
            output = model(images)
            input_lengths = torch.full((output.size(1),), output.size(0), dtype=torch.long, device=device)
            loss = criterion(F.log_softmax(output, dim=2), labels, input_lengths, label_lengths)
            total_loss += float(loss.item())
            batches += 1
            for batch_index, truth in enumerate(texts):
                pairs.append((truth, ctc_decode(output[:, batch_index : batch_index + 1, :].cpu().numpy())))
    metrics = summarize_text_metrics(pairs)
    return {"loss": total_loss / max(1, batches), **metrics}


def run_epoch(model, loader, optimizer, device) -> float:
    model.train()
    criterion = torch.nn.CTCLoss(blank=BLANK, zero_infinity=True)
    total_loss = 0.0
    batches = 0
    for images, labels, label_lengths, _, _ in loader:
        images = images.to(device)
        labels = labels.to(device)
        label_lengths = label_lengths.to(device)
        output = model(images)
        input_lengths = torch.full((output.size(1),), output.size(0), dtype=torch.long, device=device)
        loss = criterion(F.log_softmax(output, dim=2), labels, input_lengths, label_lengths)
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        total_loss += float(loss.item())
        batches += 1
    return total_loss / max(1, batches)


def train_fastvit_ctc(
    *,
    dataset: str | Path,
    output_dir: str | Path,
    stage1_epochs: int = 3,
    stage2_epochs: int = 12,
    batch_size: int = 16,
    stage1_lr: float = 1e-3,
    stage2_lr: float = 1e-4,
    pretrained: bool = True,
) -> dict[str, object]:
    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    samples = load_labeled_dataset(dataset)
    train_samples, val_samples, test_samples = split_samples(samples)
    loaders = {
        "train": DataLoader(FastViTDataset(train_samples), batch_size=batch_size, shuffle=True, collate_fn=ctc_collate),
        "val": DataLoader(FastViTDataset(val_samples), batch_size=batch_size, shuffle=False, collate_fn=ctc_collate),
        "test": DataLoader(FastViTDataset(test_samples), batch_size=batch_size, shuffle=False, collate_fn=ctc_collate),
    }
    model = build_fastvit_t8_ctc(num_classes=NUM_CLASSES, pretrained=pretrained).to(device)
    history = []
    best_exact = -1.0
    best_path = output_root / "fastvit_t8_ctc_best.pth"

    for stage_name, epochs, lr, trainable in [
        ("stage1_frozen", stage1_epochs, stage1_lr, False),
        ("stage2_unfrozen", stage2_epochs, stage2_lr, True),
    ]:
        set_backbone_trainable(model, trainable)
        optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=lr, weight_decay=1e-4)
        for epoch in range(epochs):
            train_loss = run_epoch(model, loaders["train"], optimizer, device)
            val = evaluate_model(model, loaders["val"], device)
            row = {"stage": stage_name, "epoch": epoch + 1, "train_loss": train_loss, "val": val}
            history.append(row)
            print(json.dumps(row, ensure_ascii=False))
            if float(val["exact"]) > best_exact:
                best_exact = float(val["exact"])
                torch.save(model.state_dict(), best_path)

    model.load_state_dict(torch.load(best_path, map_location=device, weights_only=True))
    test = evaluate_model(model, loaders["test"], device)
    torch.save(model.state_dict(), output_root / "fastvit_t8_ctc_final.pth")
    model.cpu()
    onnx_path = output_root / "fastvit_t8_ctc.onnx"
    metadata_path = output_root / "fastvit_t8_ctc_metadata.json"
    export_fastvit_onnx(model, onnx_path=onnx_path, metadata_path=metadata_path)
    report = {
        "history": history,
        "test": test,
        "onnx_path": str(onnx_path),
        "metadata_path": str(metadata_path),
    }
    (output_root / "fastvit_t8_ctc_report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--output-dir", default="exported_candidates/fastvit_t8_ctc")
    parser.add_argument("--stage1-epochs", type=int, default=3)
    parser.add_argument("--stage2-epochs", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--stage1-lr", type=float, default=1e-3)
    parser.add_argument("--stage2-lr", type=float, default=1e-4)
    parser.add_argument("--no-pretrained", action="store_true")
    args = parser.parse_args(argv)
    report = train_fastvit_ctc(
        dataset=args.dataset,
        output_dir=args.output_dir,
        stage1_epochs=args.stage1_epochs,
        stage2_epochs=args.stage2_epochs,
        batch_size=args.batch_size,
        stage1_lr=args.stage1_lr,
        stage2_lr=args.stage2_lr,
        pretrained=not args.no_pretrained,
    )
    print(json.dumps({"output_dir": args.output_dir, "onnx_path": report["onnx_path"]}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
