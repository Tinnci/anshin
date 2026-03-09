"""
轻量 CRNN + CTC 模型训练脚本
============================
训练一个小型 CNN + BiLSTM + CTC 模型用于七段管数字序列识别。

模型架构:
- CNN 特征提取 (MobileNet-style depthwise separable convolutions)
- BiLSTM 序列建模
- CTC 解码

目标模型大小: < 2MB (INT8 量化后 < 500KB)
"""

import argparse
import csv
import os
import random
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from PIL import Image


# ── 字符集定义 ────────────────────────────────────────────
# CTC blank = 0, 然后是 0-9, /, ., 空格, -
CHARS = "0123456789/. -"
BLANK = 0  # CTC blank token
CHAR_TO_IDX = {ch: i + 1 for i, ch in enumerate(CHARS)}
IDX_TO_CHAR = {i + 1: ch for i, ch in enumerate(CHARS)}
NUM_CLASSES = len(CHARS) + 1  # +1 for CTC blank


def encode_label(text: str) -> list[int]:
    """将字符串编码为索引序列。"""
    return [CHAR_TO_IDX[ch] for ch in text if ch in CHAR_TO_IDX]


def decode_prediction(indices: list[int]) -> str:
    """CTC 贪婪解码: 合并重复 + 移除 blank。"""
    result = []
    prev = BLANK
    for idx in indices:
        if idx != BLANK and idx != prev:
            if idx in IDX_TO_CHAR:
                result.append(IDX_TO_CHAR[idx])
        prev = idx
    return "".join(result)


# ── 数据集 ──────────────────────────────────────────────
class SevenSegDataset(Dataset):
    """七段管序列 OCR 数据集。"""

    def __init__(self, root_dir: str, target_h: int = 64, max_w: int = 256):
        self.root_dir = Path(root_dir)
        self.img_dir = self.root_dir / "images"
        self.target_h = target_h
        self.max_w = max_w
        self.samples = []

        csv_path = self.root_dir / "sequences.csv"
        with open(csv_path) as f:
            reader = csv.DictReader(f)
            for row in reader:
                self.samples.append((row["filename"], row["label"]))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        fname, label = self.samples[idx]
        img = Image.open(self.img_dir / fname).convert("L")  # 灰度

        # 缩放到固定高度，保持宽高比，填充到 max_w
        ratio = self.target_h / img.height
        new_w = min(int(img.width * ratio), self.max_w)
        img = img.resize((new_w, self.target_h), Image.LANCZOS)

        # 填充到固定宽度
        padded = Image.new("L", (self.max_w, self.target_h), 0)
        padded.paste(img, (0, 0))

        # 转 tensor, 归一化到 [0, 1]
        tensor = torch.from_numpy(np.array(padded, dtype=np.float32) / 255.0)
        tensor = tensor.unsqueeze(0)  # [1, H, W]

        # 编码标签
        encoded = encode_label(label)
        return tensor, torch.tensor(encoded, dtype=torch.long), len(encoded), new_w


class SingleDigitDataset(Dataset):
    """七段管单数字分类数据集。"""

    def __init__(self, root_dir: str, target_size: tuple = (32, 64)):
        self.root_dir = Path(root_dir)
        self.img_dir = self.root_dir / "images"
        self.target_size = target_size
        self.samples = []

        csv_path = self.root_dir / "labels.csv"
        with open(csv_path) as f:
            reader = csv.DictReader(f)
            for row in reader:
                self.samples.append((row["filename"], int(row["label"])))

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        fname, label = self.samples[idx]
        img = Image.open(self.img_dir / fname).convert("L")
        img = img.resize(self.target_size, Image.LANCZOS)
        tensor = torch.from_numpy(np.array(img, dtype=np.float32) / 255.0)
        tensor = tensor.unsqueeze(0)  # [1, H, W]
        return tensor, label


# ── 模型定义 ──────────────────────────────────────────────
class DepthwiseSeparableConv(nn.Module):
    """深度可分离卷积 (MobileNet 风格)。"""

    def __init__(self, in_ch, out_ch, kernel=3, stride=1, padding=1):
        super().__init__()
        self.depthwise = nn.Conv2d(
            in_ch, in_ch, kernel, stride, padding, groups=in_ch, bias=False
        )
        self.pointwise = nn.Conv2d(in_ch, out_ch, 1, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)

    def forward(self, x):
        x = self.depthwise(x)
        x = self.pointwise(x)
        x = self.bn(x)
        return F.relu(x)


class LightCRNN(nn.Module):
    """轻量 CRNN 模型。

    输入: [B, 1, 64, W]  (灰度图)
    输出: [T, B, num_classes]  (CTC logits)

    模型大小目标: ~800KB (FP32), ~250KB (INT8)
    """

    def __init__(self, num_classes: int = NUM_CLASSES, rnn_hidden: int = 96):
        super().__init__()

        # CNN 特征提取: 64x256 -> 1x16 (高度降为1, 宽度/16)
        self.cnn = nn.Sequential(
            # Block 1: 64xW -> 32x(W/2)
            nn.Conv2d(1, 24, 3, 1, 1, bias=False),
            nn.BatchNorm2d(24),
            nn.ReLU(),
            nn.MaxPool2d(2, 2),
            # Block 2: 32x(W/2) -> 16x(W/4)
            DepthwiseSeparableConv(24, 48),
            nn.MaxPool2d(2, 2),
            # Block 3: 16x(W/4) -> 8x(W/8)
            DepthwiseSeparableConv(48, 64),
            nn.MaxPool2d(2, 2),
            # Block 4: 8x(W/8) -> 4x(W/16)
            DepthwiseSeparableConv(64, 96),
            nn.MaxPool2d(2, 2),
            # Block 5: 4x(W/16) -> 1x(W/16)
            DepthwiseSeparableConv(96, 96),
            nn.AvgPool2d((4, 1)),  # 高度从4压到1，宽度保持
        )

        # RNN 序列建模
        self.rnn = nn.LSTM(
            input_size=96,
            hidden_size=rnn_hidden,
            num_layers=2,
            bidirectional=True,
            batch_first=False,
            dropout=0.15,
        )

        # 分类头
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        # CNN: [B, 1, H, W] -> [B, 64, 1, W']
        conv = self.cnn(x)
        # 压缩高度维: [B, 64, 1, W'] -> [B, 64, W']
        conv = conv.squeeze(2)
        # 转置为序列: [B, 64, W'] -> [W', B, 64]
        conv = conv.permute(2, 0, 1)
        # RNN: [W', B, 64] -> [W', B, 128]
        rnn_out, _ = self.rnn(conv)
        # 分类: [W', B, 128] -> [W', B, num_classes]
        output = self.fc(rnn_out)
        return output


class DigitClassifier(nn.Module):
    """简单的单数字分类 CNN。

    输入: [B, 1, 64, 32]
    输出: [B, 10]
    """

    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(1, 16, 3, 1, 1),
            nn.ReLU(),
            nn.MaxPool2d(2, 2),  # 32x16
            DepthwiseSeparableConv(16, 32),
            nn.MaxPool2d(2, 2),  # 16x8
            DepthwiseSeparableConv(32, 48),
            nn.MaxPool2d(2, 2),  # 8x4
            DepthwiseSeparableConv(48, 64),
            nn.AdaptiveAvgPool2d((1, 1)),
        )
        self.classifier = nn.Linear(64, 10)

    def forward(self, x):
        x = self.features(x)
        x = x.view(x.size(0), -1)
        return self.classifier(x)


# ── CTC collate ─────────────────────────────────────────
def ctc_collate(batch):
    """CTC 训练用的 collate 函数。"""
    images, labels, label_lengths, img_widths = zip(*batch)
    images = torch.stack(images, 0)
    # 拼接所有标签
    all_labels = torch.cat(labels, 0)
    label_lengths = torch.tensor(label_lengths, dtype=torch.long)
    return images, all_labels, label_lengths


# ── 训练循环 ──────────────────────────────────────────────
def train_crnn(
    data_dir: str,
    output_dir: str,
    epochs: int = 50,
    batch_size: int = 32,
    lr: float = 0.001,
):
    """训练 CRNN + CTC 模型。"""
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    print(f"使用设备: {device}")

    dataset = SevenSegDataset(data_dir)
    # 90/10 划分训练/验证
    n_val = max(1, len(dataset) // 10)
    n_train = len(dataset) - n_val
    train_set, val_set = torch.utils.data.random_split(dataset, [n_train, n_val])

    train_loader = DataLoader(
        train_set,
        batch_size=batch_size,
        shuffle=True,
        collate_fn=ctc_collate,
        num_workers=0,
    )
    val_loader = DataLoader(
        val_set,
        batch_size=batch_size,
        shuffle=False,
        collate_fn=ctc_collate,
        num_workers=0,
    )

    model = LightCRNN().to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode="min", factor=0.5, patience=5
    )
    ctc_loss = nn.CTCLoss(blank=BLANK, zero_infinity=True)

    # 统计模型大小
    param_count = sum(p.numel() for p in model.parameters())
    param_size_kb = param_count * 4 / 1024  # FP32
    print(f"模型参数量: {param_count:,} ({param_size_kb:.0f} KB FP32)")

    best_val_loss = float("inf")
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        num_batches = 0

        for images, labels, label_lengths in train_loader:
            images = images.to(device)
            labels = labels.to(device)

            output = model(images)  # [T, B, C]
            T = output.size(0)
            B = images.size(0)
            input_lengths = torch.full((B,), T, dtype=torch.long)

            log_probs = F.log_softmax(output, dim=2)
            loss = ctc_loss(log_probs, labels, input_lengths, label_lengths)

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            optimizer.step()

            total_loss += loss.item()
            num_batches += 1

        avg_train_loss = total_loss / max(num_batches, 1)

        # 验证
        model.eval()
        val_loss = 0.0
        val_batches = 0
        correct = 0
        total = 0

        with torch.no_grad():
            for images, labels, label_lengths in val_loader:
                images = images.to(device)
                labels = labels.to(device)

                output = model(images)
                T = output.size(0)
                B = images.size(0)
                input_lengths = torch.full((B,), T, dtype=torch.long)

                log_probs = F.log_softmax(output, dim=2)
                loss = ctc_loss(log_probs, labels, input_lengths, label_lengths)
                val_loss += loss.item()
                val_batches += 1

                # 计算准确率 (完全匹配)
                preds = output.argmax(dim=2)  # [T, B]
                offset = 0
                for b in range(B):
                    pred_indices = preds[:, b].cpu().tolist()
                    pred_text = decode_prediction(pred_indices)
                    llen = label_lengths[b].item()
                    true_indices = labels[offset : offset + llen].cpu().tolist()
                    true_text = "".join(
                        IDX_TO_CHAR.get(i, "?") for i in true_indices
                    )
                    if pred_text == true_text:
                        correct += 1
                    total += 1
                    offset += llen

        avg_val_loss = val_loss / max(val_batches, 1)
        accuracy = correct / max(total, 1)
        scheduler.step(avg_val_loss)

        if (epoch + 1) % 5 == 0 or epoch == 0:
            print(
                f"  Epoch {epoch + 1:3d}/{epochs} | "
                f"Train Loss: {avg_train_loss:.4f} | "
                f"Val Loss: {avg_val_loss:.4f} | "
                f"Accuracy: {accuracy:.1%}"
            )

        if avg_val_loss < best_val_loss:
            best_val_loss = avg_val_loss
            torch.save(model.state_dict(), out_path / "crnn_best.pth")

    # 保存最终模型
    torch.save(model.state_dict(), out_path / "crnn_final.pth")
    print(f"\n✅ 训练完成! 最佳验证 Loss: {best_val_loss:.4f}")
    print(f"  模型保存到: {out_path}/crnn_best.pth")

    return model


def train_classifier(
    data_dir: str,
    output_dir: str,
    epochs: int = 30,
    batch_size: int = 64,
    lr: float = 0.001,
):
    """训练单数字分类器。"""
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    print(f"使用设备: {device}")

    dataset = SingleDigitDataset(data_dir)
    n_val = max(1, len(dataset) // 10)
    n_train = len(dataset) - n_val
    train_set, val_set = torch.utils.data.random_split(dataset, [n_train, n_val])

    train_loader = DataLoader(
        train_set, batch_size=batch_size, shuffle=True, num_workers=0
    )
    val_loader = DataLoader(
        val_set, batch_size=batch_size, shuffle=False, num_workers=0
    )

    model = DigitClassifier().to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    criterion = nn.CrossEntropyLoss()

    param_count = sum(p.numel() for p in model.parameters())
    print(f"模型参数量: {param_count:,} ({param_count * 4 / 1024:.0f} KB FP32)")

    best_acc = 0.0
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for images, labels in train_loader:
            images = images.to(device)
            labels = torch.tensor(labels, dtype=torch.long).to(device)
            output = model(images)
            loss = criterion(output, labels)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        # 验证
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for images, labels in val_loader:
                images = images.to(device)
                labels = torch.tensor(labels, dtype=torch.long).to(device)
                output = model(images)
                _, predicted = output.max(1)
                correct += (predicted == labels).sum().item()
                total += labels.size(0)

        accuracy = correct / max(total, 1)
        if (epoch + 1) % 5 == 0 or epoch == 0:
            print(
                f"  Epoch {epoch + 1:3d}/{epochs} | "
                f"Loss: {total_loss / len(train_loader):.4f} | "
                f"Val Acc: {accuracy:.1%}"
            )

        if accuracy > best_acc:
            best_acc = accuracy
            torch.save(model.state_dict(), out_path / "digit_classifier_best.pth")

    torch.save(model.state_dict(), out_path / "digit_classifier_final.pth")
    print(f"\n✅ 训练完成! 最佳验证准确率: {best_acc:.1%}")
    print(f"  模型保存到: {out_path}/digit_classifier_best.pth")
    return model


def main():
    parser = argparse.ArgumentParser(description="七段管 OCR 模型训练")
    parser.add_argument(
        "--mode",
        choices=["crnn", "classifier", "both"],
        default="both",
        help="训练模式: crnn (序列), classifier (单数字), both (两者都训练)",
    )
    parser.add_argument("--data-dir", type=str, default="dataset", help="数据目录")
    parser.add_argument("--output-dir", type=str, default="models", help="模型输出目录")
    parser.add_argument("--epochs", type=int, default=50, help="训练轮数")
    parser.add_argument("--batch-size", type=int, default=32, help="批大小")
    parser.add_argument("--lr", type=float, default=0.001, help="学习率")

    args = parser.parse_args()

    if args.mode in ("classifier", "both"):
        print("=" * 60)
        print("训练单数字分类器")
        print("=" * 60)
        train_classifier(
            data_dir=f"{args.data_dir}/single_digit",
            output_dir=args.output_dir,
            epochs=min(args.epochs, 30),
            batch_size=args.batch_size * 2,
            lr=args.lr,
        )

    if args.mode in ("crnn", "both"):
        print("\n" + "=" * 60)
        print("训练 CRNN + CTC 序列模型")
        print("=" * 60)
        train_crnn(
            data_dir=f"{args.data_dir}/sequence",
            output_dir=args.output_dir,
            epochs=args.epochs,
            batch_size=args.batch_size,
            lr=args.lr,
        )


if __name__ == "__main__":
    main()
