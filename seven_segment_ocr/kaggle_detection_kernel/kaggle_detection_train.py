"""
YOLOv11-nano 数码管检测器 - Kaggle GPU 训练脚本
==============================================
自包含: 数据生成 + 训练 + ONNX 导出

在 Kaggle GPU 环境运行:
1. 生成合成检测数据 (YOLO 格式)
2. 训练 YOLOv11-nano 检测 lcd_display
3. 导出 ONNX 模型
"""

import os
import sys
import random
import math
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

# ── 安装 ultralytics ──
os.system("pip install -q ultralytics")

# ── 配置 ──
OUTPUT_DIR = Path("/kaggle/working")
DATA_DIR = OUTPUT_DIR / "detection_data"
NUM_TRAIN = 4000
NUM_VAL = 800
SCENE_SIZE = 640
EPOCHS = 100
BATCH_SIZE = 16
IMG_SIZE = 640

print("=" * 60)
print("YOLOv11-nano 数码管检测器训练")
print("=" * 60)

import torch
device_name = "N/A"
if torch.cuda.is_available():
    device_name = torch.cuda.get_device_name(0)
    mem_gb = torch.cuda.get_device_properties(0).total_memory / 1024**3
    print(f"🚀 GPU: {device_name} ({mem_gb:.1f} GB)")
else:
    print("⚠️ 无 GPU, 使用 CPU")


# ══════════════════════════════════════════════════════════
# 七段管渲染 (精简版, 来自 generate_data.py)
# ══════════════════════════════════════════════════════════

DIGIT_SEGMENTS = {
    0: [True, True, True, True, True, True, False],
    1: [False, True, True, False, False, False, False],
    2: [True, True, False, True, True, False, True],
    3: [True, True, True, True, False, False, True],
    4: [False, True, True, False, False, True, True],
    5: [True, False, True, True, False, True, True],
    6: [True, False, True, True, True, True, True],
    7: [True, True, True, False, False, False, False],
    8: [True, True, True, True, True, True, True],
    9: [True, True, True, True, False, True, True],
}

LCD_THEMES = [
    {"fg": (0, 255, 70), "dim": (0, 40, 10), "bg": (5, 20, 5)},
    {"fg": (255, 30, 30), "dim": (40, 5, 5), "bg": (10, 2, 2)},
    {"fg": (60, 160, 255), "dim": (8, 20, 40), "bg": (3, 8, 18)},
    {"fg": (255, 160, 30), "dim": (40, 25, 5), "bg": (12, 8, 2)},
    {"fg": (240, 240, 240), "dim": (30, 30, 30), "bg": (8, 8, 8)},
    {"fg": (180, 220, 40), "dim": (25, 30, 8), "bg": (60, 70, 50)},
    {"fg": (20, 20, 20), "dim": (200, 210, 200), "bg": (210, 220, 210)},
    {"fg": (30, 30, 80), "dim": (180, 185, 200), "bg": (190, 195, 210)},
]


def _perlin_noise_2d(shape, scale=32.0):
    h, w = shape
    noise = np.zeros((h, w), dtype=np.float32)
    for octave in range(4):
        freq = 2 ** octave
        s = scale / freq
        gh = max(2, int(h / s) + 2)
        gw = max(2, int(w / s) + 2)
        grid = np.random.randn(gh, gw).astype(np.float32)
        grid_img = Image.fromarray(grid, mode="F")
        grid_up = np.array(grid_img.resize((w, h), Image.BILINEAR))
        noise += grid_up * (0.5 ** octave)
    noise = (noise - noise.min()) / (noise.max() - noise.min() + 1e-8)
    return noise


def _draw_segment(draw, seg_id, x, y, w, h, t, fg, gap):
    g = gap
    if seg_id == "a":
        draw.polygon([(x+g+t, y), (x+w-g-t, y), (x+w-g, y+t), (x+g, y+t)], fill=fg)
    elif seg_id == "b":
        draw.polygon([(x+w, y+g+t), (x+w, y+h//2-g), (x+w-t, y+h//2-g-t//2), (x+w-t, y+g+t+t//2)], fill=fg)
    elif seg_id == "c":
        draw.polygon([(x+w, y+h//2+g), (x+w, y+h-g-t), (x+w-t, y+h-g-t-t//2), (x+w-t, y+h//2+g+t//2)], fill=fg)
    elif seg_id == "d":
        draw.polygon([(x+g, y+h-t), (x+w-g, y+h-t), (x+w-g-t, y+h), (x+g+t, y+h)], fill=fg)
    elif seg_id == "e":
        draw.polygon([(x, y+h//2+g), (x+t, y+h//2+g+t//2), (x+t, y+h-g-t-t//2), (x, y+h-g-t)], fill=fg)
    elif seg_id == "f":
        draw.polygon([(x, y+g+t), (x+t, y+g+t+t//2), (x+t, y+h//2-g-t//2), (x, y+h//2-g)], fill=fg)
    elif seg_id == "g":
        my = y + h // 2
        draw.polygon([(x+g, my), (x+g+t, my-t//2), (x+w-g-t, my-t//2),
                       (x+w-g, my), (x+w-g-t, my+t//2), (x+g+t, my+t//2)], fill=fg)


def render_number(text, digit_width=40, digit_height=70, thickness=6,
                  theme=None, gap=1, spacing=8, padding=10, skew=0.0,
                  show_dim=True, use_textured_bg=False):
    if theme is None:
        theme = random.choice(LCD_THEMES)
    fg = theme["fg"]
    dim = theme["dim"]
    bg_color = theme["bg"]

    chars = list(text)
    total_w = padding * 2
    for ch in chars:
        if ch == ".":
            total_w += thickness * 2 + spacing
        elif ch == "/":
            total_w += digit_width // 2 + spacing
        elif ch == " ":
            total_w += digit_width // 2 + spacing
        elif ch == "-":
            total_w += digit_width + spacing
        else:
            total_w += digit_width + spacing
    total_h = digit_height + padding * 2

    if use_textured_bg:
        img = _generate_textured_bg(total_w, total_h, bg_color)
    else:
        img = Image.new("RGB", (total_w, total_h), bg_color)

    draw = ImageDraw.Draw(img)
    cx = padding

    for ch in chars:
        if ch == ".":
            r = thickness
            draw.ellipse([cx, padding + digit_height - r*2, cx + r*2, padding + digit_height], fill=fg)
            cx += r * 2 + spacing
        elif ch == "/":
            x1 = cx + digit_width // 2
            y1 = padding
            x2 = cx
            y2 = padding + digit_height
            draw.line([(x1, y1), (x2, y2)], fill=fg, width=max(2, thickness // 2))
            cx += digit_width // 2 + spacing
        elif ch == " ":
            cx += digit_width // 2 + spacing
        elif ch == "-":
            my = padding + digit_height // 2
            draw.polygon([(cx + gap, my), (cx + gap + thickness, my - thickness // 2),
                          (cx + digit_width - gap - thickness, my - thickness // 2),
                          (cx + digit_width - gap, my),
                          (cx + digit_width - gap - thickness, my + thickness // 2),
                          (cx + gap + thickness, my + thickness // 2)], fill=fg)
            cx += digit_width + spacing
        elif ch.isdigit():
            d = int(ch)
            segs = DIGIT_SEGMENTS[d]
            seg_names = ["a", "b", "c", "d", "e", "f", "g"]
            for seg_name, on in zip(seg_names, segs):
                color = fg if on else (dim if show_dim else bg_color)
                _draw_segment(draw, seg_name, cx, padding, digit_width, digit_height, thickness, color, gap)
            cx += digit_width + spacing

    if abs(skew) > 0.001:
        img = img.transform(img.size, Image.AFFINE, (1, skew, -skew * total_h / 2, 0, 1, 0), resample=Image.BICUBIC)

    return img


def _generate_textured_bg(w, h, base_color):
    style = random.choice(["plastic", "metal", "medical", "plain"])
    arr = np.zeros((h, w, 3), dtype=np.float32)
    for c in range(3):
        arr[:, :, c] = base_color[c]

    if style == "plastic":
        noise = _perlin_noise_2d((h, w), 24.0)
        for c in range(3):
            arr[:, :, c] += (noise - 0.5) * 15
    elif style == "metal":
        for y in range(h):
            stripe = random.gauss(0, 3)
            arr[y, :, :] += stripe
    elif style == "medical":
        grad = np.linspace(0, 1, h).reshape(-1, 1) * 8
        for c in range(3):
            arr[:, :, c] += grad
    # plain: no texture

    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


# ══════════════════════════════════════════════════════════
# 增强函数 (精简版)
# ══════════════════════════════════════════════════════════

def augment_lcd(img, difficulty="normal"):
    """对 LCD 图进行增强。"""
    if difficulty == "easy":
        if random.random() < 0.3:
            arr = np.array(img, dtype=np.float32)
            arr += np.random.normal(0, 3, arr.shape)
            img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
        return img

    # normal/hard
    # 亮度/对比度
    b = random.uniform(0.7, 1.3) if difficulty == "hard" else random.uniform(0.85, 1.15)
    img = ImageEnhance.Brightness(img).enhance(b)
    c = random.uniform(0.7, 1.3) if difficulty == "hard" else random.uniform(0.85, 1.15)
    img = ImageEnhance.Contrast(img).enhance(c)

    # 噪声
    if random.random() < 0.5:
        arr = np.array(img, dtype=np.float32)
        intensity = random.uniform(3, 12) if difficulty == "hard" else random.uniform(2, 6)
        arr += np.random.normal(0, intensity, arr.shape)
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))

    # 模糊
    if random.random() < (0.4 if difficulty == "hard" else 0.2):
        r = random.uniform(0.3, 1.5) if difficulty == "hard" else random.uniform(0.2, 0.8)
        img = img.filter(ImageFilter.GaussianBlur(radius=r))

    # 反光 (hard)
    if difficulty == "hard" and random.random() < 0.3:
        arr = np.array(img, dtype=np.float32)
        w, h = img.size
        cy = random.randint(0, h - 1)
        spread = random.randint(h // 4, h // 2)
        for y in range(h):
            factor = max(0, 1 - abs(y - cy) / spread)
            arr[y] = np.clip(arr[y] + factor * random.uniform(20, 60), 0, 255)
        img = Image.fromarray(arr.astype(np.uint8))

    return img


# ══════════════════════════════════════════════════════════
# 场景生成 + 检测数据
# ══════════════════════════════════════════════════════════

def generate_scene_bg(w, h):
    style = random.choice(["table", "desk", "medical", "gradient", "solid"])
    if style == "table":
        base = random.choice([(180, 140, 100), (200, 170, 130), (160, 120, 80)])
        img = _generate_textured_bg(w, h, base)
    elif style == "desk":
        base = random.choice([(220, 220, 220), (240, 235, 225), (200, 200, 205)])
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32) + np.random.normal(0, 3, (h, w, 3))
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    elif style == "medical":
        base = random.choice([(240, 245, 250), (235, 240, 235), (250, 250, 250)])
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32) + np.random.normal(0, 2, (h, w, 3))
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    elif style == "gradient":
        c1 = np.array(random.choice([(200, 180, 160), (180, 190, 200)]), dtype=np.float32)
        c2 = np.array(random.choice([(160, 140, 120), (140, 150, 160)]), dtype=np.float32)
        t = np.linspace(0, 1, h).reshape(-1, 1)  # (h, 1) for proper broadcast to (h, w, 3)
        arr = np.empty((h, w, 3), dtype=np.float32)
        for c in range(3):
            arr[:, :, c] = c1[c] * (1 - t) + c2[c] * t
        noise = np.random.normal(0, 3, arr.shape)
        img = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))
    else:
        base = tuple(random.randint(100, 240) for _ in range(3))
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32) + np.random.normal(0, 5, (h, w, 3))
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    return img


def generate_device_body(lcd_img):
    lcd_w, lcd_h = lcd_img.size
    style = random.choice(["simple", "rounded", "bezel", "full"])

    if style == "simple":
        pt, pb, pl, pr = [random.randint(8, 25) for _ in range(4)]
        bc = random.choice([(230, 230, 230), (200, 200, 200), (245, 240, 235), (180, 185, 190)])
    elif style == "rounded":
        pt = pb = pl = pr = random.randint(12, 30)
        bc = random.choice([(220, 225, 230), (240, 240, 240)])
    elif style == "bezel":
        pt = random.randint(15, 40)
        pb = random.randint(30, 60)
        pl = pr = random.randint(10, 25)
        bc = random.choice([(235, 235, 235), (210, 215, 220)])
    else:
        pt = random.randint(20, 50)
        pb = random.randint(40, 80)
        pl = pr = random.randint(15, 35)
        bc = random.choice([(240, 240, 240), (220, 225, 230)])

    dw = lcd_w + pl + pr
    dh = lcd_h + pt + pb
    device = Image.new("RGB", (dw, dh), bc)
    draw = ImageDraw.Draw(device)

    if style == "rounded":
        r = random.randint(5, 12)
        draw.rounded_rectangle([pl - 3, pt - 3, pl + lcd_w + 3, pt + lcd_h + 3], radius=r, fill=(0, 0, 0))
    elif style in ("bezel", "full"):
        draw.rectangle([pl - 2, pt - 2, pl + lcd_w + 2, pt + lcd_h + 2], fill=(10, 10, 10))
        # 按钮
        btn_y = pt + lcd_h + 10
        for i in range(random.randint(2, 4)):
            bx = pl + i * (lcd_w // 4) + random.randint(0, 10)
            btn_col = tuple(max(0, c - 30) for c in bc)
            draw.ellipse([bx, btn_y, bx + 12, btn_y + 12], fill=btn_col, outline=(150, 150, 150))

    device.paste(lcd_img, (pl, pt))
    bbox = (pl, pt, pl + lcd_w, pt + lcd_h)
    return device, bbox


def random_medical_text():
    gen = random.choice([
        lambda: f"{random.randint(80, 200)}/{random.randint(40, 130)}",
        lambda: str(random.randint(40, 200)),
        lambda: str(round(random.uniform(35.0, 42.0), 1)),
        lambda: str(random.randint(85, 100)),
        lambda: "".join([str(random.randint(0, 9)) for _ in range(random.randint(2, 5))]),
    ])
    return gen()


def rotate_with_bbox(img, bbox, angle):
    w, h = img.size
    cx, cy = w / 2, h / 2
    rotated = img.rotate(-angle, expand=True, resample=Image.BICUBIC, fillcolor=(0, 0, 0))
    nw, nh = rotated.size
    cos_a = math.cos(math.radians(angle))
    sin_a = math.sin(math.radians(angle))
    corners = [(bbox[0], bbox[1]), (bbox[2], bbox[1]), (bbox[2], bbox[3]), (bbox[0], bbox[3])]
    ox, oy = (nw - w) / 2, (nh - h) / 2
    rc = [(cos_a * (px - cx) - sin_a * (py - cy) + cx + ox,
           sin_a * (px - cx) + cos_a * (py - cy) + cy + oy) for px, py in corners]
    xs, ys = [c[0] for c in rc], [c[1] for c in rc]
    return rotated, (max(0, int(min(xs))), max(0, int(min(ys))), min(nw, int(max(xs))), min(nh, int(max(ys))))


def rects_overlap(r1, r2):
    return not (r1[2] <= r2[0] or r1[0] >= r2[2] or r1[3] <= r2[1] or r1[1] >= r2[3])


def generate_sample(scene_size=640):
    sw = sh = scene_size
    scene = generate_scene_bg(sw, sh)
    num_devices = random.choices([1, 2], weights=[0.75, 0.25], k=1)[0]
    labels = []
    occupied = []

    for _ in range(num_devices):
        text = random_medical_text()
        theme = random.choice(LCD_THEMES)
        dw = random.randint(25, 45)
        dh = random.randint(45, 75)
        t = random.randint(3, max(4, dw // 6))

        lcd = render_number(text, digit_width=dw, digit_height=dh, thickness=t,
                           theme=theme, gap=random.randint(0, 2), spacing=random.randint(3, 10),
                           padding=random.randint(5, 12), skew=random.uniform(-0.08, 0.08),
                           show_dim=random.random() < 0.6, use_textured_bg=random.random() < 0.5)

        diff = random.choices(["easy", "normal", "hard"], weights=[0.4, 0.4, 0.2], k=1)[0]
        lcd = augment_lcd(lcd, diff)
        device, lcd_bbox = generate_device_body(lcd)

        max_dev_w = int(sw * random.uniform(0.3, 0.7))
        dev_w, dev_h = device.size
        if dev_w > max_dev_w:
            scale = max_dev_w / dev_w
            nw, nh = int(dev_w * scale), int(dev_h * scale)
            device = device.resize((nw, nh), Image.LANCZOS)
            lcd_bbox = tuple(int(v * scale) for v in lcd_bbox)
            dev_w, dev_h = nw, nh

        if random.random() < 0.4:
            angle = random.uniform(-15, 15)
            device, lcd_bbox = rotate_with_bbox(device, lcd_bbox, angle)
            dev_w, dev_h = device.size

        mx, my = max(0, sw - dev_w), max(0, sh - dev_h)
        placed = False
        for _ in range(20):
            px, py = random.randint(0, max(0, mx)), random.randint(0, max(0, my))
            nr = (px, py, px + dev_w, py + dev_h)
            if not any(rects_overlap(nr, o) for o in occupied):
                placed = True
                break
        if not placed:
            continue

        occupied.append((px, py, px + dev_w, py + dev_h))
        scene.paste(device, (px, py))

        x1 = max(0.0, min(1.0, (px + lcd_bbox[0]) / sw))
        y1 = max(0.0, min(1.0, (py + lcd_bbox[1]) / sh))
        x2 = max(0.0, min(1.0, (px + lcd_bbox[2]) / sw))
        y2 = max(0.0, min(1.0, (py + lcd_bbox[3]) / sh))
        cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
        bw, bh = x2 - x1, y2 - y1
        if bw > 0.01 and bh > 0.01:
            labels.append((0, cx, cy, bw, bh))

    # 场景增强
    if random.random() < 0.5:
        scene = ImageEnhance.Brightness(scene).enhance(random.uniform(0.7, 1.3))
    if random.random() < 0.4:
        scene = ImageEnhance.Contrast(scene).enhance(random.uniform(0.8, 1.2))
    if random.random() < 0.3:
        scene = scene.filter(ImageFilter.GaussianBlur(radius=random.uniform(0.3, 1.0)))
    if random.random() < 0.4:
        arr = np.array(scene, dtype=np.float32)
        arr += np.random.normal(0, random.uniform(2, 8), arr.shape)
        scene = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))

    return scene, labels


# ══════════════════════════════════════════════════════════
# 数据集生成
# ══════════════════════════════════════════════════════════

def generate_dataset():
    random.seed(42)
    np.random.seed(42)

    for split, count in [("train", NUM_TRAIN), ("val", NUM_VAL)]:
        img_dir = DATA_DIR / split / "images"
        lbl_dir = DATA_DIR / split / "labels"
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n📦 生成 {split} 集: {count} 张...")
        for i in range(count):
            scene, labels = generate_sample(SCENE_SIZE)
            scene.save(str(img_dir / f"{i:05d}.jpg"), "JPEG", quality=90)
            with open(lbl_dir / f"{i:05d}.txt", "w") as f:
                for cls, cx, cy, bw, bh in labels:
                    f.write(f"{cls} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")
            if (i + 1) % 500 == 0:
                print(f"    {i + 1}/{count}")

    # data.yaml
    (DATA_DIR / "data.yaml").write_text(
        f"path: {DATA_DIR}\n"
        f"train: train/images\n"
        f"val: val/images\n"
        f"\n"
        f"names:\n"
        f"  0: lcd_display\n"
    )
    print("✅ 数据集生成完成")


# ══════════════════════════════════════════════════════════
# 训练 + 导出
# ══════════════════════════════════════════════════════════

def train_and_export():
    from ultralytics import YOLO

    # 加载 YOLOv11-nano 预训练权重
    model = YOLO("yolo11n.pt")

    # 训练
    print(f"\n🏋️ 开始训练 (epochs={EPOCHS}, batch={BATCH_SIZE})...")
    results = model.train(
        data=str(DATA_DIR / "data.yaml"),
        epochs=EPOCHS,
        batch=BATCH_SIZE,
        imgsz=IMG_SIZE,
        device=0 if torch.cuda.is_available() else "cpu",
        workers=2,
        patience=20,
        save=True,
        project=str(OUTPUT_DIR / "runs"),
        name="lcd_detect",
        exist_ok=True,
        # 数据增强
        hsv_h=0.015,
        hsv_s=0.5,
        hsv_v=0.3,
        degrees=15.0,
        translate=0.1,
        scale=0.3,
        shear=5.0,
        perspective=0.0005,
        flipud=0.0,  # 不上下翻转
        fliplr=0.5,
        mosaic=1.0,
        mixup=0.1,
    )

    # 最佳模型路径
    best_pt = OUTPUT_DIR / "runs" / "lcd_detect" / "weights" / "best.pt"
    print(f"\n✅ 训练完成! 最佳模型: {best_pt}")

    # 导出 ONNX
    print("\n📦 导出 ONNX...")
    best_model = YOLO(str(best_pt))
    best_model.export(
        format="onnx",
        imgsz=IMG_SIZE,
        simplify=True,
        opset=17,
    )

    # 复制到输出目录
    onnx_src = best_pt.with_suffix(".onnx")
    onnx_dst = OUTPUT_DIR / "lcd_detector.onnx"
    shutil.copy2(str(onnx_src), str(onnx_dst))
    print(f"✅ ONNX 导出: {onnx_dst} ({onnx_dst.stat().st_size / 1024 / 1024:.1f} MB)")

    # 也复制 best.pt
    shutil.copy2(str(best_pt), str(OUTPUT_DIR / "lcd_detector_best.pt"))

    # 打印验证结果
    print("\n📊 验证结果:")
    val_results = best_model.val(data=str(DATA_DIR / "data.yaml"))
    print(f"  mAP50:    {val_results.box.map50:.4f}")
    print(f"  mAP50-95: {val_results.box.map:.4f}")


# ══════════════════════════════════════════════════════════
# 主流程
# ══════════════════════════════════════════════════════════

if __name__ == "__main__":
    generate_dataset()
    train_and_export()

    # 清理训练数据，只保留模型文件（减小输出大小）
    print("\n🧹 清理训练数据...")
    import shutil as _sh
    for cleanup_dir in [DATA_DIR, OUTPUT_DIR / "runs"]:
        if cleanup_dir.exists():
            _sh.rmtree(str(cleanup_dir))
            print(f"  已删除: {cleanup_dir}")

    print("\n🎉 完成! 请下载 /kaggle/working/lcd_detector.onnx")
