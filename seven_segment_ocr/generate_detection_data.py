"""
YOLO 检测数据生成器
==================
生成合成训练数据：将七段管显示图渲染到复杂背景上，
输出 YOLO 格式 (images/ + labels/) 用于训练 YOLOv11-nano。

类别: 0 = lcd_display (数码管显示区域)

用法:
    python generate_detection_data.py                    # 默认生成 3000 张
    python generate_detection_data.py --num 5000         # 生成 5000 张
    python generate_detection_data.py --preview          # 预览模式
"""

import argparse
import random
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

from generate_data import (
    LCD_THEMES,
    render_number,
    augment_image,
    generate_textured_background,
    _perlin_noise_2d,
)

# ── 背景场景生成 ──────────────────────────────────────────

def generate_scene_background(w: int, h: int) -> Image.Image:
    """生成模拟真实拍摄场景的背景图。"""
    style = random.choice([
        "table", "desk", "fabric", "wall", "floor",
        "medical", "gradient", "noisy_solid",
    ])

    if style == "table":
        # 木质桌面
        base = random.choice([(180, 140, 100), (200, 170, 130), (160, 120, 80)])
        img = generate_textured_background(w, h, base)
        # 模拟木纹
        arr = np.array(img, dtype=np.float32)
        grain = _perlin_noise_2d((h, w), 16.0) * 20
        for c in range(3):
            arr[:, :, c] = np.clip(arr[:, :, c] + grain, 0, 255)
        img = Image.fromarray(arr.astype(np.uint8))

    elif style == "desk":
        # 办公桌/简洁面
        base = random.choice([(220, 220, 220), (240, 235, 225), (200, 200, 205)])
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32)
        noise = np.random.normal(0, 3, arr.shape)
        img = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))

    elif style == "fabric":
        base = random.choice([(150, 140, 160), (180, 180, 170), (140, 160, 150)])
        img = generate_textured_background(w, h, base)

    elif style == "wall":
        base = random.choice([(230, 228, 220), (245, 240, 230), (210, 215, 220)])
        img = Image.new("RGB", (w, h), base)
        # 添加纹理
        arr = np.array(img, dtype=np.float32)
        noise = _perlin_noise_2d((h, w), 32.0) * 8
        for c in range(3):
            arr[:, :, c] = np.clip(arr[:, :, c] + noise, 0, 255)
        img = Image.fromarray(arr.astype(np.uint8))

    elif style == "floor":
        base = random.choice([(190, 180, 170), (170, 165, 155), (210, 200, 185)])
        img = generate_textured_background(w, h, base)

    elif style == "medical":
        # 医疗环境（白色/浅蓝）
        base = random.choice([(240, 245, 250), (235, 240, 235), (250, 250, 250)])
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32)
        noise = np.random.normal(0, 2, arr.shape)
        img = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))

    elif style == "gradient":
        arr = np.zeros((h, w, 3), dtype=np.float32)
        c1 = np.array(random.choice([(200, 180, 160), (180, 190, 200), (220, 210, 200)]), dtype=np.float32)
        c2 = np.array(random.choice([(160, 140, 120), (140, 150, 160), (180, 170, 160)]), dtype=np.float32)
        angle = random.uniform(0, math.pi)
        for y in range(h):
            for x in range(w):
                t = (x * math.cos(angle) + y * math.sin(angle)) / max(w, h)
                t = max(0.0, min(1.0, t))
                arr[y, x] = c1 * (1 - t) + c2 * t
        img = Image.fromarray(arr.astype(np.uint8))

    else:  # noisy_solid
        base = tuple(random.randint(100, 240) for _ in range(3))
        img = Image.new("RGB", (w, h), base)
        arr = np.array(img, dtype=np.float32)
        noise = np.random.normal(0, 5, arr.shape)
        img = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))

    return img


def generate_device_body(lcd_img: Image.Image) -> tuple[Image.Image, tuple[int, int, int, int]]:
    """
    在数码管显示图周围生成设备外壳，返回 (设备图, LCD在设备图中的bbox)。
    bbox 格式: (x1, y1, x2, y2)
    """
    lcd_w, lcd_h = lcd_img.size
    
    style = random.choice(["simple", "rounded", "bezel", "full_device"])
    
    if style == "simple":
        # 简单边框
        pad_t = random.randint(8, 25)
        pad_b = random.randint(8, 25)
        pad_l = random.randint(8, 20)
        pad_r = random.randint(8, 20)
        body_color = random.choice([
            (230, 230, 230), (200, 200, 200), (245, 240, 235),
            (180, 185, 190), (250, 250, 250),
        ])
        dev_w = lcd_w + pad_l + pad_r
        dev_h = lcd_h + pad_t + pad_b
        device = Image.new("RGB", (dev_w, dev_h), body_color)
        device.paste(lcd_img, (pad_l, pad_t))
        bbox = (pad_l, pad_t, pad_l + lcd_w, pad_t + lcd_h)

    elif style == "rounded":
        pad = random.randint(12, 30)
        body_color = random.choice([
            (220, 225, 230), (240, 240, 240), (200, 210, 220),
        ])
        dev_w = lcd_w + pad * 2
        dev_h = lcd_h + pad * 2
        device = Image.new("RGB", (dev_w, dev_h), body_color)
        draw = ImageDraw.Draw(device)
        # 画圆角矩形背景
        r = random.randint(5, 12)
        draw.rounded_rectangle(
            [pad - 3, pad - 3, pad + lcd_w + 3, pad + lcd_h + 3],
            radius=r, fill=(0, 0, 0),
        )
        device.paste(lcd_img, (pad, pad))
        bbox = (pad, pad, pad + lcd_w, pad + lcd_h)

    elif style == "bezel":
        pad_t = random.randint(15, 40)
        pad_b = random.randint(30, 60)  # 按钮区域
        pad_l = random.randint(10, 25)
        pad_r = random.randint(10, 25)
        body_color = random.choice([
            (235, 235, 235), (210, 215, 220), (245, 240, 230),
        ])
        dev_w = lcd_w + pad_l + pad_r
        dev_h = lcd_h + pad_t + pad_b
        device = Image.new("RGB", (dev_w, dev_h), body_color)
        draw = ImageDraw.Draw(device)
        # LCD 凹陷边框
        draw.rectangle(
            [pad_l - 2, pad_t - 2, pad_l + lcd_w + 2, pad_t + lcd_h + 2],
            fill=(40, 40, 40),
        )
        device.paste(lcd_img, (pad_l, pad_t))
        # 模拟按钮
        btn_y = pad_t + lcd_h + 10
        for bx in range(3):
            bx_pos = pad_l + bx * (lcd_w // 3) + 5
            btn_color = tuple(max(0, c - 30) for c in body_color)
            draw.ellipse(
                [bx_pos, btn_y, bx_pos + 15, btn_y + 15],
                fill=btn_color, outline=(150, 150, 150),
            )
        bbox = (pad_l, pad_t, pad_l + lcd_w, pad_t + lcd_h)

    else:  # full_device
        # 完整设备外形（较大）
        pad_t = random.randint(20, 50)
        pad_b = random.randint(40, 80)
        pad_l = random.randint(15, 35)
        pad_r = random.randint(15, 35)
        body_color = random.choice([
            (240, 240, 240), (220, 225, 230), (250, 248, 240),
            (200, 200, 210),
        ])
        dev_w = lcd_w + pad_l + pad_r
        dev_h = lcd_h + pad_t + pad_b
        device = Image.new("RGB", (dev_w, dev_h), body_color)
        draw = ImageDraw.Draw(device)
        # 添加品牌区域（顶部色条）
        brand_h = min(12, pad_t - 5)
        if brand_h > 3:
            brand_color = tuple(max(0, c - 20) for c in body_color)
            draw.rectangle([pad_l, 5, pad_l + lcd_w, 5 + brand_h], fill=brand_color)
        # LCD
        draw.rectangle(
            [pad_l - 1, pad_t - 1, pad_l + lcd_w + 1, pad_t + lcd_h + 1],
            fill=(10, 10, 10),
        )
        device.paste(lcd_img, (pad_l, pad_t))
        # 底部按钮/文字区域
        btn_y = pad_t + lcd_h + 15
        for i in range(random.randint(2, 4)):
            bx = pad_l + i * (lcd_w // 4) + random.randint(0, 10)
            btn_w = random.randint(12, 20)
            btn_h = random.randint(8, 14)
            btn_col = tuple(max(0, c - random.randint(15, 40)) for c in body_color)
            draw.rounded_rectangle(
                [bx, btn_y, bx + btn_w, btn_y + btn_h],
                radius=3, fill=btn_col,
            )
        bbox = (pad_l, pad_t, pad_l + lcd_w, pad_t + lcd_h)

    return device, bbox


def apply_scene_augmentation(scene: Image.Image) -> Image.Image:
    """对整个场景图应用拍照模拟增强。"""
    # 随机亮度
    if random.random() < 0.5:
        factor = random.uniform(0.7, 1.3)
        scene = ImageEnhance.Brightness(scene).enhance(factor)

    # 随机对比度
    if random.random() < 0.4:
        factor = random.uniform(0.8, 1.2)
        scene = ImageEnhance.Contrast(scene).enhance(factor)

    # 轻微模糊（模拟对焦不完美）
    if random.random() < 0.3:
        r = random.uniform(0.3, 1.0)
        scene = scene.filter(ImageFilter.GaussianBlur(radius=r))

    # 噪声
    if random.random() < 0.4:
        arr = np.array(scene, dtype=np.float32)
        noise = np.random.normal(0, random.uniform(2, 8), arr.shape)
        scene = Image.fromarray(np.clip(arr + noise, 0, 255).astype(np.uint8))

    return scene


def generate_detection_sample(
    scene_size: tuple[int, int] = (640, 640),
) -> tuple[Image.Image, list[tuple[int, float, float, float, float]]]:
    """
    生成一张场景图 + YOLO 标注。

    返回: (scene_image, labels)
        labels: [(class_id, cx, cy, w, h), ...]  归一化到 [0, 1]
    """
    sw, sh = scene_size

    # 1. 生成背景
    scene = generate_scene_background(sw, sh)

    # 2. 随机生成 1~2 个数码管设备
    num_devices = random.choices([1, 2], weights=[0.75, 0.25], k=1)[0]
    labels = []
    occupied = []  # 已放置区域，避免重叠

    for _ in range(num_devices):
        # 生成数码管文本
        text = _random_medical_text()

        # 随机主题和参数
        theme = random.choice(LCD_THEMES)
        dw = random.randint(25, 45)
        dh = random.randint(45, 75)
        thickness = random.randint(3, max(4, dw // 6))

        lcd_img = render_number(
            text,
            digit_width=dw,
            digit_height=dh,
            thickness=thickness,
            theme=theme,
            gap=random.randint(0, 2),
            spacing=random.randint(3, 10),
            padding=random.randint(5, 12),
            skew=random.uniform(-0.08, 0.08),
            show_dim=random.random() < 0.6,
            use_textured_bg=random.random() < 0.5,
        )

        # 对 LCD 做轻微增强
        difficulty = random.choices(
            ["easy", "normal", "hard"], weights=[0.4, 0.4, 0.2], k=1
        )[0]
        lcd_img = augment_image(lcd_img, difficulty)

        # 3. 生成设备外壳
        device_img, lcd_bbox = generate_device_body(lcd_img)

        # 4. 随机缩放设备以适配场景
        max_dev_w = int(sw * random.uniform(0.3, 0.7))
        dev_w, dev_h = device_img.size
        if dev_w > max_dev_w:
            scale = max_dev_w / dev_w
            new_w = int(dev_w * scale)
            new_h = int(dev_h * scale)
            device_img = device_img.resize((new_w, new_h), Image.LANCZOS)
            # 缩放 bbox
            lcd_bbox = (
                int(lcd_bbox[0] * scale),
                int(lcd_bbox[1] * scale),
                int(lcd_bbox[2] * scale),
                int(lcd_bbox[3] * scale),
            )
            dev_w, dev_h = new_w, new_h

        # 5. 随机旋转设备
        if random.random() < 0.4:
            angle = random.uniform(-15, 15)
            device_img, lcd_bbox = _rotate_with_bbox(device_img, lcd_bbox, angle)
            dev_w, dev_h = device_img.size

        # 6. 放置到场景中（避免出界、避免重叠）
        max_x = max(0, sw - dev_w)
        max_y = max(0, sh - dev_h)

        placed = False
        for _attempt in range(20):
            px = random.randint(0, max(0, max_x))
            py = random.randint(0, max(0, max_y))
            new_rect = (px, py, px + dev_w, py + dev_h)
            if not any(_rects_overlap(new_rect, occ) for occ in occupied):
                placed = True
                break

        if not placed:
            continue

        occupied.append((px, py, px + dev_w, py + dev_h))
        scene.paste(device_img, (px, py))

        # 7. 计算 LCD 在场景中的归一化坐标
        lcd_x1 = (px + lcd_bbox[0]) / sw
        lcd_y1 = (py + lcd_bbox[1]) / sh
        lcd_x2 = (px + lcd_bbox[2]) / sw
        lcd_y2 = (py + lcd_bbox[3]) / sh

        # 裁剪到 [0, 1]
        lcd_x1 = max(0.0, min(1.0, lcd_x1))
        lcd_y1 = max(0.0, min(1.0, lcd_y1))
        lcd_x2 = max(0.0, min(1.0, lcd_x2))
        lcd_y2 = max(0.0, min(1.0, lcd_y2))

        cx = (lcd_x1 + lcd_x2) / 2
        cy = (lcd_y1 + lcd_y2) / 2
        bw = lcd_x2 - lcd_x1
        bh = lcd_y2 - lcd_y1

        if bw > 0.01 and bh > 0.01:  # 过滤太小的
            labels.append((0, cx, cy, bw, bh))

    # 8. 场景增强
    scene = apply_scene_augmentation(scene)

    return scene, labels


def _random_medical_text() -> str:
    """随机生成医疗设备上的数字文本。"""
    gen = random.choice([
        lambda: f"{random.randint(80, 200)}/{random.randint(40, 130)}",  # BP
        lambda: str(random.randint(40, 200)),  # HR
        lambda: str(round(random.uniform(35.0, 42.0), 1)),  # Temp
        lambda: str(random.randint(85, 100)),  # SpO2
        lambda: str(round(random.uniform(2.0, 30.0), 1)),  # Glucose
        lambda: "".join([str(random.randint(0, 9)) for _ in range(random.randint(2, 5))]),
    ])
    return gen()


def _rotate_with_bbox(
    img: Image.Image,
    bbox: tuple[int, int, int, int],
    angle: float,
) -> tuple[Image.Image, tuple[int, int, int, int]]:
    """旋转图像并更新 bbox。"""
    w, h = img.size
    cx, cy = w / 2, h / 2

    rotated = img.rotate(-angle, expand=True, resample=Image.BICUBIC, fillcolor=(0, 0, 0))
    nw, nh = rotated.size

    # 旋转四个角点
    cos_a = math.cos(math.radians(angle))
    sin_a = math.sin(math.radians(angle))
    corners = [
        (bbox[0], bbox[1]),
        (bbox[2], bbox[1]),
        (bbox[2], bbox[3]),
        (bbox[0], bbox[3]),
    ]

    offset_x = (nw - w) / 2
    offset_y = (nh - h) / 2

    rotated_corners = []
    for px, py in corners:
        rx = cos_a * (px - cx) - sin_a * (py - cy) + cx + offset_x
        ry = sin_a * (px - cx) + cos_a * (py - cy) + cy + offset_y
        rotated_corners.append((rx, ry))

    xs = [c[0] for c in rotated_corners]
    ys = [c[1] for c in rotated_corners]
    new_bbox = (
        max(0, int(min(xs))),
        max(0, int(min(ys))),
        min(nw, int(max(xs))),
        min(nh, int(max(ys))),
    )

    return rotated, new_bbox


def _rects_overlap(r1, r2) -> bool:
    """检查两个矩形是否重叠。"""
    return not (r1[2] <= r2[0] or r1[0] >= r2[2] or r1[3] <= r2[1] or r1[1] >= r2[3])


# ── 数据集生成 ──────────────────────────────────────────

def generate_dataset(
    output_dir: str = "detection_dataset",
    num_train: int = 2500,
    num_val: int = 500,
    scene_size: tuple[int, int] = (640, 640),
    seed: int = 42,
):
    """生成完整 YOLO 格式数据集。"""
    random.seed(seed)
    np.random.seed(seed)

    root = Path(output_dir)

    for split, count in [("train", num_train), ("val", num_val)]:
        img_dir = root / split / "images"
        lbl_dir = root / split / "labels"
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n生成 {split} 集: {count} 张...")
        for i in range(count):
            scene, labels = generate_detection_sample(scene_size)

            # 保存图片
            img_path = img_dir / f"{i:05d}.jpg"
            scene.save(str(img_path), "JPEG", quality=random.randint(80, 95))

            # 保存标注
            lbl_path = lbl_dir / f"{i:05d}.txt"
            with open(lbl_path, "w") as f:
                for cls, cx, cy, bw, bh in labels:
                    f.write(f"{cls} {cx:.6f} {cy:.6f} {bw:.6f} {bh:.6f}\n")

            if (i + 1) % 500 == 0:
                print(f"  {i + 1}/{count}")

    # 生成 data.yaml
    yaml_path = root / "data.yaml"
    yaml_path.write_text(
        f"path: {root.resolve()}\n"
        f"train: train/images\n"
        f"val: val/images\n"
        f"\n"
        f"names:\n"
        f"  0: lcd_display\n"
    )
    print(f"\n✅ 数据集生成完成: {root}")
    print(f"   data.yaml: {yaml_path}")


def preview(num: int = 8):
    """预览生成的检测样本。"""
    for i in range(num):
        scene, labels = generate_detection_sample((640, 640))
        draw = ImageDraw.Draw(scene)
        for cls, cx, cy, bw, bh in labels:
            x1 = int((cx - bw / 2) * 640)
            y1 = int((cy - bh / 2) * 640)
            x2 = int((cx + bw / 2) * 640)
            y2 = int((cy + bh / 2) * 640)
            draw.rectangle([x1, y1, x2, y2], outline="lime", width=2)
            draw.text((x1, y1 - 12), "lcd", fill="lime")
        scene.save(f"preview_det_{i:02d}.jpg")
    print(f"已保存 {num} 张预览图 (preview_det_*.jpg)")


# ── CLI ──────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="YOLO 检测数据生成")
    parser.add_argument("--num-train", type=int, default=2500, help="训练集大小")
    parser.add_argument("--num-val", type=int, default=500, help="验证集大小")
    parser.add_argument("--output", type=str, default="detection_dataset", help="输出目录")
    parser.add_argument("--size", type=int, default=640, help="场景图尺寸")
    parser.add_argument("--seed", type=int, default=42, help="随机种子")
    parser.add_argument("--preview", action="store_true", help="预览模式")
    args = parser.parse_args()

    if args.preview:
        preview()
    else:
        generate_dataset(
            output_dir=args.output,
            num_train=args.num_train,
            num_val=args.num_val,
            scene_size=(args.size, args.size),
            seed=args.seed,
        )


if __name__ == "__main__":
    main()
