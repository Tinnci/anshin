"""Real-world visual effects for synthetic seven-segment OCR data."""

import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from lcd_rendering import (
    _perlin_noise_2d,
    add_background_clutter,
    add_barrel_distortion,
    add_cast_shadow,
    add_chromatic_aberration,
    add_color_cast,
    add_edge_frame,
    add_ghosting,
    add_jpeg_artifacts,
    add_motion_blur,
    add_noise,
    add_reflection,
    add_salt_pepper_noise,
    add_scratches,
    add_smudge,
    adjust_brightness_contrast,
    invert_polarity,
    perspective_transform,
    random_rotate,
)

# ── 医疗标签干扰文字 ──────────────────────────────────

MEDICAL_LABELS = {
    "bp": [
        "mmHg", "SYS", "DIA", "BP", "SYS/DIA", "Systolic", "Diastolic",
        "血压", "高压", "低压", "收缩压", "舒张压", "毫米汞柱",
        "血圧", "最高", "最低",
        "혈압", "수축기", "이완기",
    ],
    "hr": [
        "bpm", "HR", "PULSE", "Pulse", "beats/min",
        "心率", "脉搏", "次/分",
        "心拍", "脈拍",
        "심박수", "맥박",
    ],
    "temp": [
        "°C", "℃", "°F", "TEMP", "Temp",
        "体温", "温度",
        "体温", "たいおん",
        "체온", "온도",
    ],
    "spo2": [
        "%SpO2", "SpO2", "%", "SAT",
        "血氧", "血氧饱和度",
        "酸素",
        "산소포화도",
    ],
    "weight": [
        "kg", "KG", "lb",
        "体重", "体脂", "体脂率", "BMI",
        "体重", "たいじゅう",
        "체중", "체지방",
    ],
    "generic": [
        "mmHg", "bpm", "°C", "kg", "%",
        "ON", "OFF", "MEM", "SET",
    ],
}


def _try_load_font(size: int):
    """尝试加载支持 CJK 的字体，失败则返回默认字体。"""
    from PIL import ImageFont

    candidates = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Hiragino Sans GB.ttc",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


_LABEL_FONT_CACHE: dict = {}


def _get_label_font(size: int):
    if size not in _LABEL_FONT_CACHE:
        _LABEL_FONT_CACHE[size] = _try_load_font(size)
    return _LABEL_FONT_CACHE[size]


def add_medical_label(
    img: Image.Image, category: str | None = None
) -> Image.Image:
    """在图像边缘扩展画布并添加医疗标签文字，确保不与数字区域重叠。"""
    w, h = img.size

    if category is None:
        category = random.choice(list(MEDICAL_LABELS.keys()))
    labels = MEDICAL_LABELS.get(category, MEDICAL_LABELS["generic"])
    text = random.choice(labels)

    font_size = max(8, int(h * random.uniform(0.15, 0.30)))
    font = _get_label_font(font_size)

    bg_pixel = img.getpixel((0, 0))
    avg = sum(bg_pixel) / 3
    if avg > 128:
        color = tuple(max(0, c - random.randint(40, 100)) for c in bg_pixel)
    else:
        color = tuple(min(255, c + random.randint(40, 100)) for c in bg_pixel)

    # 测量文字尺寸
    temp_img = Image.new("RGB", (1, 1))
    temp_draw = ImageDraw.Draw(temp_img)
    bbox = temp_draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad = max(2, int(h * 0.03))

    # 扩展画布方向
    side = random.choice(["bottom", "top", "right"])
    if side == "bottom":
        new_h = h + th + pad * 2
        canvas = Image.new("RGB", (w, new_h), bg_pixel)
        canvas.paste(img, (0, 0))
        tx = random.randint(pad, max(pad, w - tw - pad))
        ty = h + pad
    elif side == "top":
        new_h = h + th + pad * 2
        canvas = Image.new("RGB", (w, new_h), bg_pixel)
        canvas.paste(img, (0, th + pad * 2))
        tx = random.randint(pad, max(pad, w - tw - pad))
        ty = pad
    else:  # right
        new_w = w + tw + pad * 2
        canvas = Image.new("RGB", (new_w, h), bg_pixel)
        canvas.paste(img, (0, 0))
        tx = w + pad
        ty = random.randint(pad, max(pad, h - th - pad))

    draw = ImageDraw.Draw(canvas)
    draw.text((tx, ty), text, fill=color, font=font)
    return canvas


def partial_occlusion(img: Image.Image, max_rects: int = 3) -> Image.Image:
    """随机矩形遮挡（模拟部分被遮挡/手指遮挡）。"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    bg = img.getpixel((0, 0))

    for _ in range(random.randint(1, max_rects)):
        rect_w = random.randint(2, max(3, w // 8))
        rect_h = random.randint(2, max(3, h // 6))
        x = random.randint(0, w - rect_w)
        y = random.randint(0, h - rect_h)
        draw.rectangle([x, y, x + rect_w, y + rect_h], fill=bg)
    return img


def add_lcd_scanlines(img: Image.Image) -> Image.Image:
    """加入 LCD/相机采样产生的横向扫描纹和轻微摩尔纹。"""
    arr = np.array(img.convert("RGB"), dtype=np.float32)
    h, w = arr.shape[:2]
    period = random.uniform(3.0, 9.0)
    phase = random.uniform(0, math.pi * 2)
    yy = np.arange(h, dtype=np.float32).reshape(-1, 1)
    xx = np.arange(w, dtype=np.float32).reshape(1, -1)
    horizontal = np.sin(yy / period + phase) * random.uniform(3.0, 14.0)
    diagonal = np.sin((xx + yy * random.uniform(0.15, 0.45)) / random.uniform(8.0, 18.0)) * random.uniform(1.0, 6.0)
    arr += (horizontal + diagonal)[:, :, None]
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def add_uneven_backlight(img: Image.Image) -> Image.Image:
    """模拟 LCD 背光/反射不均：局部变暗、变亮、偏色。"""
    arr = np.array(img.convert("RGB"), dtype=np.float32)
    h, w = arr.shape[:2]
    noise = _perlin_noise_2d((h, w), scale=max(12, min(w, h) // 2))
    gain = 0.70 + noise * random.uniform(0.35, 0.75)
    tint = np.array(
        [
            random.uniform(0.92, 1.08),
            random.uniform(0.92, 1.10),
            random.uniform(0.90, 1.12),
        ],
        dtype=np.float32,
    )
    arr = arr * gain[:, :, None] * tint
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def add_glass_glare_band(img: Image.Image) -> Image.Image:
    """模拟玻璃面板上斜向高光带。"""
    base = img.convert("RGB")
    w, h = base.size
    overlay = Image.new("RGB", (w, h), (0, 0, 0))
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    band_w = random.randint(max(4, w // 12), max(6, w // 4))
    x = random.randint(-band_w, w)
    slope = random.uniform(-0.8, 0.8)
    points = [
        (x, 0),
        (x + band_w, 0),
        (int(x + band_w + slope * h), h),
        (int(x + slope * h), h),
    ]
    draw.polygon(points, fill=random.randint(45, 130))
    mask = mask.filter(ImageFilter.GaussianBlur(radius=random.uniform(2.0, 8.0)))
    glare_color = (
        random.randint(210, 255),
        random.randint(210, 255),
        random.randint(210, 255),
    )
    overlay.paste(glare_color, (0, 0, w, h))
    return Image.composite(overlay, base, mask)


def add_segment_wear(img: Image.Image) -> Image.Image:
    """模拟七段管局部断笔、污渍、弱段显示。"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    bg = img.getpixel((0, 0))
    for _ in range(random.randint(1, 5)):
        rect_w = random.randint(max(1, w // 60), max(2, w // 18))
        rect_h = random.randint(max(1, h // 45), max(2, h // 14))
        x = random.randint(0, max(0, w - rect_w))
        y = random.randint(0, max(0, h - rect_h))
        if random.random() < 0.55:
            fill = tuple(int(c * random.uniform(0.75, 1.05)) for c in bg)
        else:
            fill = tuple(random.randint(60, 190) for _ in range(3))
        draw.rounded_rectangle([x, y, x + rect_w, y + rect_h], radius=1, fill=fill)
    if random.random() < 0.5:
        img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.2, 0.8)))
    return img


def augment_image(img: Image.Image, difficulty: str = "normal") -> Image.Image:
    """对图片应用随机增强变换。"""
    if difficulty == "easy":
        brightness = random.uniform(0.85, 1.15)
        contrast = random.uniform(0.9, 1.1)
        img = adjust_brightness_contrast(img, brightness, contrast)
        if random.random() < 0.3:
            img = add_noise(img, random.uniform(0.01, 0.03))
        if random.random() < 0.1:
            img = add_ghosting(img, 0.08)
        return img

    if difficulty in ("real", "real_world"):
        if random.random() < 0.65:
            img = add_uneven_backlight(img)
        if random.random() < 0.70:
            img = add_lcd_scanlines(img)
        if random.random() < 0.55:
            img = add_glass_glare_band(img)
        if random.random() < 0.45:
            img = add_segment_wear(img)
        if random.random() < 0.45:
            img = perspective_transform(img, random.uniform(0.08, 0.24))
        if random.random() < 0.55:
            img = adjust_brightness_contrast(
                img, random.uniform(0.55, 1.15), random.uniform(0.35, 0.85)
            )
        if random.random() < 0.35:
            img = add_reflection(img, random.uniform(0.25, 0.55))
        if random.random() < 0.35:
            img = add_motion_blur(img)
        if random.random() < 0.30:
            img = add_cast_shadow(img)
        if random.random() < 0.25:
            img = add_jpeg_artifacts(img, random.randint(10, 35))
        if random.random() < 0.25:
            img = add_noise(img, random.uniform(0.04, 0.14))
        if random.random() < 0.18:
            img = add_smudge(img)
        if random.random() < 0.14:
            img = partial_occlusion(img, max_rects=2)
        if random.random() < 0.60:
            img = random_rotate(img, max_angle=12.0)
        return img

    if difficulty == "hard":
        if random.random() < 0.35:
            img = add_background_clutter(img)
        if random.random() < 0.25:
            img = add_edge_frame(img)
        # 视角对比度绑定
        if random.random() < 0.6:
            ps = random.uniform(0.06, 0.20)
            img = perspective_transform(img, ps)
            if ps > 0.12:
                img = adjust_brightness_contrast(
                    img, random.uniform(0.6, 0.85), random.uniform(0.4, 0.65)
                )
        if random.random() < 0.4:
            img = adjust_brightness_contrast(
                img, random.uniform(0.6, 0.9), random.uniform(0.4, 0.6)
            )
        if random.random() < 0.5:
            img = add_reflection(img, random.uniform(0.15, 0.5))
        if random.random() < 0.4:
            img = add_color_cast(img)
        # 物理仿真
        if random.random() < 0.20:
            img = add_ghosting(img)
        if random.random() < 0.20:
            img = add_motion_blur(img)
        if random.random() < 0.15:
            img = add_barrel_distortion(img)
        if random.random() < 0.20:
            img = add_cast_shadow(img)
        if random.random() < 0.12:
            img = add_chromatic_aberration(img)
        if random.random() < 0.10:
            img = add_scratches(img)
        if random.random() < 0.10:
            img = add_smudge(img)
        # 原有增强
        if random.random() < 0.5:
            img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.8, 2.5)))
        if random.random() < 0.5:
            img = add_noise(img, random.uniform(0.05, 0.15))
        if random.random() < 0.3:
            img = add_salt_pepper_noise(img, random.uniform(0.01, 0.05))
        if random.random() < 0.3:
            img = add_jpeg_artifacts(img, random.randint(15, 40))
        if random.random() < 0.2:
            img = partial_occlusion(img, max_rects=2)
        if random.random() < 0.6:
            img = random_rotate(img, max_angle=10.0)
        if random.random() < 0.08:
            img = invert_polarity(img)
        return img

    # normal
    img = adjust_brightness_contrast(
        img, random.uniform(0.7, 1.3), random.uniform(0.8, 1.3)
    )
    if random.random() < 0.7:
        img = add_noise(img, random.uniform(0.02, 0.10))
    if random.random() < 0.4:
        img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.3, 1.5)))
    if random.random() < 0.5:
        img = random_rotate(img, max_angle=5.0)
    if random.random() < 0.2:
        img = add_reflection(img, random.uniform(0.1, 0.25))
    if random.random() < 0.15:
        img = add_color_cast(img)
    if random.random() < 0.25:
        img = perspective_transform(img, random.uniform(0.03, 0.10))
    if random.random() < 0.1:
        img = add_background_clutter(img)
    # normal 也加入部分物理仿真
    if random.random() < 0.10:
        img = add_ghosting(img, 0.10)
    if random.random() < 0.08:
        img = add_barrel_distortion(img, random.uniform(0.03, 0.10))
    if random.random() < 0.08:
        img = add_cast_shadow(img)
    if random.random() < 0.05:
        img = add_chromatic_aberration(img, 1)
    return img


