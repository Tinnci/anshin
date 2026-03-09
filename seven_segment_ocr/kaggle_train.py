"""
七段数码管 OCR 训练 - Kaggle GPU 版
====================================
这个脚本可以直接在 Kaggle Notebook (GPU) 中运行。
它包含完整的：数据生成 → 模型定义 → 训练 → ONNX 导出

使用方法:
1. 在 Kaggle 创建一个新 Notebook
2. 开启 GPU 加速器 (Settings → Accelerator → GPU T4 x2)
3. 粘贴此脚本到一个 Code Cell 中运行
4. 结果输出到 /kaggle/working/ 目录
5. 下载 crnn_seven_seg.onnx 到本地

或者通过 Kaggle API:
    kaggle kernels push -p kaggle_notebook/
"""

# %% [markdown]
# # 七段数码管 OCR - GPU 训练

# %%
import csv
import io
import math
import os
import random
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from PIL import Image, ImageDraw, ImageFilter

# 检查 GPU
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"🚀 使用设备: {device}")
if torch.cuda.is_available():
    print(f"   GPU: {torch.cuda.get_device_name()}")
    print(f"   显存: {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")

# ─────────────────────────────────────────────────────────
# 数据生成（完整的七段管渲染 + 增强管道）
# ─────────────────────────────────────────────────────────

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


def generate_textured_background(w, h, base_color):
    style = random.choice(["plastic", "metal", "wood", "fabric", "medical", "marble"])
    r, g, b = base_color
    if style == "plastic":
        noise = _perlin_noise_2d((h, w), scale=max(16, min(w, h) // 2))
        intensity = random.uniform(8, 25)
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :, 0] = r + (noise - 0.5) * intensity
        arr[:, :, 1] = g + (noise - 0.5) * intensity
        arr[:, :, 2] = b + (noise - 0.5) * intensity
    elif style == "metal":
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :] = [r, g, b]
        for y in range(h):
            arr[y, :] += random.gauss(0, random.uniform(3, 12))
        noise = _perlin_noise_2d((h, w), scale=max(16, w // 3))
        arr += (noise[:, :, None] - 0.5) * 8
    elif style == "wood":
        arr = np.zeros((h, w, 3), dtype=np.float32)
        freq = random.uniform(0.02, 0.06)
        angle = random.uniform(-0.3, 0.3)
        yy, xx = np.mgrid[0:h, 0:w]
        wave = np.sin((xx * math.cos(angle) + yy * math.sin(angle)) * freq * 2 * math.pi)
        noise = _perlin_noise_2d((h, w), scale=max(16, w // 4))
        pattern = wave * 0.5 + noise * 0.5
        intensity = random.uniform(10, 30)
        arr[:, :, 0] = r + random.uniform(5, 15) + pattern * intensity
        arr[:, :, 1] = g + random.uniform(0, 8) + pattern * intensity * 0.7
        arr[:, :, 2] = b + pattern * intensity * 0.4
    elif style == "fabric":
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :] = [r, g, b]
        grid_size = random.randint(3, 8)
        intensity = random.uniform(5, 18)
        for y in range(h):
            for x in range(w):
                if (y % grid_size < grid_size // 2) ^ (x % grid_size < grid_size // 2):
                    arr[y, x] += intensity
                else:
                    arr[y, x] -= intensity
    elif style == "medical":
        arr = np.zeros((h, w, 3), dtype=np.float32)
        grad = np.linspace(0, 1, w).reshape(1, -1)
        grad = np.broadcast_to(grad, (h, w))
        gi = random.uniform(5, 20)
        arr[:, :, 0] = r + (grad - 0.5) * gi
        arr[:, :, 1] = g + (grad - 0.5) * gi
        arr[:, :, 2] = b + (grad - 0.5) * gi
        bump = _perlin_noise_2d((h, w), scale=max(8, min(w, h) // 4))
        arr += (bump[:, :, None] - 0.5) * 6
    else:  # marble
        n1 = _perlin_noise_2d((h, w), scale=max(16, min(w, h) // 2))
        n2 = _perlin_noise_2d((h, w), scale=max(8, min(w, h) // 4))
        yy, xx = np.mgrid[0:h, 0:w]
        pattern = np.sin((xx / max(1, w) * 4 + n1 * 3) * math.pi) * 0.5 + n2 * 0.3
        intensity = random.uniform(8, 20)
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :, 0] = r + pattern * intensity
        arr[:, :, 1] = g + pattern * intensity * 0.9
        arr[:, :, 2] = b + pattern * intensity * 0.8
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def draw_seven_segment_digit(draw, digit, x, y, width, height, thickness, fg_color, dim_color=None, gap=1, skew=0.0):
    segments = DIGIT_SEGMENTS[digit]
    half_h = height // 2
    t = thickness
    g = gap
    seg_polys = []
    seg_polys.append([(x+g+t,y),(x+width-g-t,y),(x+width-g-t-t//2,y+t),(x+g+t+t//2,y+t)])
    seg_polys.append([(x+width-t,y+g+t),(x+width,y+g+t),(x+width,y+half_h-g),(x+width-t//2,y+half_h-g+t//2),(x+width-t,y+half_h-g)])
    seg_polys.append([(x+width-t,y+half_h+g),(x+width-t//2,y+half_h+g-t//2),(x+width,y+half_h+g),(x+width,y+height-g-t),(x+width-t,y+height-g-t)])
    seg_polys.append([(x+g+t+t//2,y+height-t),(x+width-g-t-t//2,y+height-t),(x+width-g-t,y+height),(x+g+t,y+height)])
    seg_polys.append([(x,y+half_h+g),(x+t//2,y+half_h+g-t//2),(x+t,y+half_h+g),(x+t,y+height-g-t),(x,y+height-g-t)])
    seg_polys.append([(x,y+g+t),(x+t,y+g+t),(x+t,y+half_h-g),(x+t//2,y+half_h-g+t//2),(x,y+half_h-g)])
    seg_polys.append([(x+g+t,y+half_h-t//2),(x+g+t+t//2,y+half_h-t),(x+width-g-t-t//2,y+half_h-t),(x+width-g-t,y+half_h-t//2),(x+width-g-t-t//2,y+half_h),(x+g+t+t//2,y+half_h)])
    if abs(skew) > 0.001:
        center_y = y + height / 2
        for poly in seg_polys:
            for i, (px, py) in enumerate(poly):
                poly[i] = (px + (py - center_y) * math.tan(skew), py)
    for i, (on, poly) in enumerate(zip(segments, seg_polys)):
        color = fg_color if on else dim_color
        if color is not None:
            draw.polygon(poly, fill=color)


def render_number(text, digit_width=40, digit_height=70, thickness=6, theme=None, gap=1, spacing=8, padding=10, skew=0.0, show_dim=True, use_textured_bg=False):
    if theme is None:
        theme = random.choice(LCD_THEMES)
    fg, dim, bg = theme["fg"], theme["dim"] if show_dim else None, theme["bg"]
    char_widths = []
    for ch in text:
        if ch in "0123456789": char_widths.append(digit_width)
        elif ch == "/": char_widths.append(digit_width // 2)
        elif ch == " ": char_widths.append(digit_width // 2)
        elif ch == "-": char_widths.append(digit_width // 2)
        elif ch == ".": char_widths.append(thickness * 2)
        else: char_widths.append(digit_width // 3)
    total_w = sum(char_widths) + spacing * (len(text) - 1) + padding * 2
    total_h = digit_height + padding * 2
    if use_textured_bg:
        img = generate_textured_background(total_w, total_h, bg)
    else:
        img = Image.new("RGB", (total_w, total_h), bg)
    draw = ImageDraw.Draw(img)
    cx = padding
    for ch, cw in zip(text, char_widths):
        if ch in "0123456789":
            draw_seven_segment_digit(draw, int(ch), cx, padding, cw, digit_height, thickness, fg, dim, gap, skew)
        elif ch == "/":
            draw.line([(cx+cw, padding+2), (cx, padding+digit_height-2)], fill=fg, width=max(2, thickness//2))
        elif ch == "-":
            mid_y = padding + digit_height // 2
            draw.rectangle([cx+2, mid_y-thickness//2, cx+cw-2, mid_y+thickness//2], fill=fg)
        elif ch == ".":
            dot_y = padding + digit_height - thickness
            draw.ellipse([cx, dot_y, cx+thickness*2, dot_y+thickness*2], fill=fg)
        cx += cw + spacing
    return img


# ── 增强函数 ──
def add_noise(img, intensity=0.05):
    arr = np.array(img, dtype=np.float32)
    arr = np.clip(arr + np.random.normal(0, intensity * 255, arr.shape), 0, 255)
    return Image.fromarray(arr.astype(np.uint8))

def add_salt_pepper_noise(img, amount=0.02):
    arr = np.array(img)
    mask = np.random.random(arr.shape[:2])
    arr[mask < amount / 2] = 0
    arr[mask > 1 - amount / 2] = 255
    return Image.fromarray(arr)

def adjust_brightness_contrast(img, brightness=1.0, contrast=1.0):
    arr = np.array(img, dtype=np.float32)
    arr = ((arr - 128) * contrast + 128) * brightness
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))

def random_rotate(img, max_angle=5.0):
    angle = random.uniform(-max_angle, max_angle)
    return img.rotate(angle, resample=Image.BICUBIC, fillcolor=img.getpixel((0, 0)), expand=True)

def add_reflection(img, intensity=0.3):
    arr = np.array(img, dtype=np.float32)
    h, w = arr.shape[:2]
    pattern = random.choice(["gradient", "spot", "stripe"])
    if pattern == "gradient":
        angle = random.uniform(0, math.pi)
        yy, xx = np.mgrid[0:h, 0:w]
        gradient = (xx * math.cos(angle) + yy * math.sin(angle)) / max(w, h)
        gradient = (gradient - gradient.min()) / (gradient.max() - gradient.min() + 1e-6)
        reflection = gradient * intensity * 255
    elif pattern == "spot":
        cx, cy = random.uniform(0.2, 0.8) * w, random.uniform(0.2, 0.8) * h
        radius = random.uniform(0.2, 0.5) * max(w, h)
        yy, xx = np.mgrid[0:h, 0:w]
        reflection = np.clip(1.0 - np.sqrt((xx-cx)**2 + (yy-cy)**2) / radius, 0, 1) * intensity * 255
    else:
        freq = random.uniform(0.01, 0.05)
        yy = np.arange(h).reshape(-1, 1)
        reflection = np.broadcast_to((np.sin(yy * freq * 2 * math.pi + random.uniform(0, 2*math.pi)) * 0.5 + 0.5) * intensity * 255, (h, w)).copy()
    if len(arr.shape) == 3:
        reflection = reflection.reshape(h, w, 1)
    return Image.fromarray(np.clip(arr + reflection, 0, 255).astype(np.uint8))

def add_color_cast(img):
    arr = np.array(img, dtype=np.float32)
    for c in range(3):
        arr[:, :, c] = np.clip(arr[:, :, c] + random.uniform(-20, 20), 0, 255)
    return Image.fromarray(arr.astype(np.uint8))

def perspective_transform(img, strength=0.08):
    w, h = img.size
    s = strength
    coeffs = [random.uniform(-s, s) * d for d in [w, h, w, h, w, h, w, h]]
    src = [(0,0),(w,0),(w,h),(0,h)]
    dst = [(coeffs[0],coeffs[1]),(w+coeffs[2],coeffs[3]),(w+coeffs[4],h+coeffs[5]),(coeffs[6],h+coeffs[7])]
    try:
        matrix = []
        for s_pt, t_pt in zip(src, dst):
            matrix.append([t_pt[0],t_pt[1],1,0,0,0,-s_pt[0]*t_pt[0],-s_pt[0]*t_pt[1]])
            matrix.append([0,0,0,t_pt[0],t_pt[1],1,-s_pt[1]*t_pt[0],-s_pt[1]*t_pt[1]])
        A = np.array(matrix, dtype=np.float64)
        B = np.array([c for pair in src for c in pair], dtype=np.float64)
        res = np.linalg.solve(A, B)
        return img.transform((w,h), Image.PERSPECTIVE, tuple(res.flatten()), resample=Image.BICUBIC, fillcolor=img.getpixel((0,0)))
    except Exception:
        return img

def add_jpeg_artifacts(img, quality=30):
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality)
    buf.seek(0)
    return Image.open(buf).convert("RGB")

def partial_occlusion(img, max_rects=3):
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    bg = img.getpixel((0,0))
    for _ in range(random.randint(1, max_rects)):
        rw, rh = random.randint(2, max(3, w//8)), random.randint(2, max(3, h//6))
        x, y = random.randint(0, w-rw), random.randint(0, h-rh)
        draw.rectangle([x, y, x+rw, y+rh], fill=bg)
    return img

def add_background_clutter(img):
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for _ in range(random.randint(2, 6)):
        bg_r, bg_g, bg_b = img.getpixel((0, 0))
        color = tuple(max(0, min(255, c + random.randint(-15, 15))) for c in (bg_r, bg_g, bg_b))
        et = random.choice(["line", "rect", "circle", "dots"])
        if et == "line":
            draw.line([(random.randint(0,w),random.randint(0,h)),(random.randint(0,w),random.randint(0,h))], fill=color, width=random.randint(1,2))
        elif et == "rect":
            x1, y1 = random.randint(0,w-5), random.randint(0,h-5)
            draw.rectangle([x1, y1, x1+random.randint(3,w//4), y1+random.randint(3,h//4)], outline=color)
        elif et == "circle":
            cx, cy = random.randint(0,w), random.randint(0,h)
            r = random.randint(2, max(3, min(w,h)//6))
            draw.ellipse([cx-r, cy-r, cx+r, cy+r], outline=color)
        else:
            for _ in range(random.randint(3,10)):
                draw.point((random.randint(0,w-1), random.randint(0,h-1)), fill=color)
    return img

def add_edge_frame(img):
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    bg = img.getpixel((0,0))
    shift = random.choice([-30, -20, 20, 30])
    color = tuple(max(0, min(255, c+shift)) for c in bg)
    bw = random.randint(1, 3)
    draw.rectangle([0, 0, w-1, h-1], outline=color, width=bw)
    return img


def augment_image(img, difficulty="normal"):
    if difficulty == "easy":
        img = adjust_brightness_contrast(img, random.uniform(0.85, 1.15), random.uniform(0.9, 1.1))
        if random.random() < 0.3:
            img = add_noise(img, random.uniform(0.01, 0.03))
        return img

    if difficulty == "hard":
        if random.random() < 0.35: img = add_background_clutter(img)
        if random.random() < 0.25: img = add_edge_frame(img)
        if random.random() < 0.4: img = adjust_brightness_contrast(img, random.uniform(0.6, 0.9), random.uniform(0.3, 0.6))
        if random.random() < 0.5: img = add_reflection(img, random.uniform(0.15, 0.5))
        if random.random() < 0.4: img = add_color_cast(img)
        if random.random() < 0.5: img = perspective_transform(img, random.uniform(0.05, 0.15))
        if random.random() < 0.5: img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.8, 2.5)))
        if random.random() < 0.5: img = add_noise(img, random.uniform(0.05, 0.15))
        if random.random() < 0.3: img = add_salt_pepper_noise(img, random.uniform(0.01, 0.05))
        if random.random() < 0.3: img = add_jpeg_artifacts(img, random.randint(15, 40))
        if random.random() < 0.2: img = partial_occlusion(img, max_rects=2)
        if random.random() < 0.6: img = random_rotate(img, max_angle=10.0)
        return img

    # normal
    img = adjust_brightness_contrast(img, random.uniform(0.7, 1.3), random.uniform(0.8, 1.3))
    if random.random() < 0.7: img = add_noise(img, random.uniform(0.02, 0.10))
    if random.random() < 0.4: img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.3, 1.5)))
    if random.random() < 0.5: img = random_rotate(img, max_angle=5.0)
    if random.random() < 0.2: img = add_reflection(img, random.uniform(0.1, 0.25))
    if random.random() < 0.15: img = add_color_cast(img)
    if random.random() < 0.15: img = perspective_transform(img, random.uniform(0.03, 0.08))
    if random.random() < 0.1: img = add_background_clutter(img)
    return img


# ─────────────────────────────────────────────────────────
# 数据集生成 (在内存中)
# ─────────────────────────────────────────────────────────

CHARS = "0123456789/. -"
BLANK = 0
CHAR_TO_IDX = {ch: i + 1 for i, ch in enumerate(CHARS)}
IDX_TO_CHAR = {i + 1: ch for i, ch in enumerate(CHARS)}
NUM_CLASSES = len(CHARS) + 1

def encode_label(text):
    return [CHAR_TO_IDX[ch] for ch in text if ch in CHAR_TO_IDX]

def decode_prediction(indices):
    result = []
    prev = BLANK
    for idx in indices:
        if idx != BLANK and idx != prev:
            if idx in IDX_TO_CHAR:
                result.append(IDX_TO_CHAR[idx])
        prev = idx
    return "".join(result)


class InMemorySeqDataset(Dataset):
    """在内存中生成并缓存图片（GPU 训练更快）。"""

    def __init__(self, num_samples=10000, target_h=64, max_w=256, seed=42):
        self.target_h = target_h
        self.max_w = max_w
        self.data = []

        random.seed(seed)
        np.random.seed(seed)

        def random_bp():
            sys = random.randint(80, 200)
            dia = random.randint(40, 130)
            return f"{sys}{random.choice(['/', ' '])}{dia}"
        def random_hr(): return str(random.randint(40, 200))
        def random_temp(): return str(round(random.uniform(35.0, 42.0), 1))
        def random_glucose(): return str(round(random.uniform(2.0, 30.0), 1))
        def random_spo2(): return str(random.randint(85, 100))
        def random_weight(): return str(round(random.uniform(30.0, 150.0), 1))
        def random_generic():
            return "".join([str(random.randint(0, 9)) for _ in range(random.randint(1, 6))])

        generators = [
            (random_bp, 0.35), (random_hr, 0.15), (random_temp, 0.10),
            (random_glucose, 0.10), (random_spo2, 0.10), (random_weight, 0.05),
            (random_generic, 0.15),
        ]

        print(f"  生成 {num_samples} 张序列图片...")
        for i in range(num_samples):
            r = random.random()
            cumul = 0.0
            gen_func = random_generic
            for func, w in generators:
                cumul += w
                if r < cumul:
                    gen_func = func
                    break

            text = gen_func()
            theme = random.choice(LCD_THEMES)
            dw = random.randint(28, 50)
            dh = random.randint(50, 85)

            img = render_number(
                text, digit_width=dw, digit_height=dh,
                thickness=random.randint(4, max(5, dw // 5)),
                theme=theme, gap=random.randint(0, 3),
                spacing=random.randint(4, 12), padding=random.randint(5, 15),
                skew=random.uniform(-0.12, 0.12),
                show_dim=random.random() < 0.5,
                use_textured_bg=random.random() < 0.4,
            )
            r2 = random.random()
            difficulty = "easy" if r2 < 0.25 else ("normal" if r2 < 0.6 else "hard")
            img = augment_image(img, difficulty)

            # 预处理
            gray = img.convert("L")
            ratio = target_h / gray.height
            new_w = min(int(gray.width * ratio), max_w)
            gray = gray.resize((new_w, target_h), Image.LANCZOS)
            padded = Image.new("L", (max_w, target_h), 0)
            padded.paste(gray, (0, 0))
            tensor = torch.from_numpy(np.array(padded, dtype=np.float32) / 255.0).unsqueeze(0)

            encoded = encode_label(text)
            self.data.append((tensor, torch.tensor(encoded, dtype=torch.long), len(encoded), new_w))

            if (i + 1) % 2000 == 0:
                print(f"    {i + 1}/{num_samples}")

        print(f"  ✅ 数据集生成完成")

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        return self.data[idx]


# ─────────────────────────────────────────────────────────
# 模型
# ─────────────────────────────────────────────────────────

class DepthwiseSeparableConv(nn.Module):
    def __init__(self, in_ch, out_ch, kernel=3, stride=1, padding=1):
        super().__init__()
        self.depthwise = nn.Conv2d(in_ch, in_ch, kernel, stride, padding, groups=in_ch, bias=False)
        self.pointwise = nn.Conv2d(in_ch, out_ch, 1, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)

    def forward(self, x):
        return F.relu(self.bn(self.pointwise(self.depthwise(x))))


class LightCRNN(nn.Module):
    def __init__(self, num_classes=NUM_CLASSES, rnn_hidden=64):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv2d(1, 16, 3, 1, 1, bias=False), nn.BatchNorm2d(16), nn.ReLU(), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(16, 32), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(32, 48), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(48, 64), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(64, 64), nn.AvgPool2d((4, 1)),
        )
        self.rnn = nn.LSTM(input_size=64, hidden_size=rnn_hidden, num_layers=1, bidirectional=True, batch_first=False)
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        conv = self.cnn(x).squeeze(2).permute(2, 0, 1)
        rnn_out, _ = self.rnn(conv)
        return self.fc(rnn_out)


# ─────────────────────────────────────────────────────────
# 训练
# ─────────────────────────────────────────────────────────

def ctc_collate(batch):
    images, labels, label_lengths, img_widths = zip(*batch)
    return torch.stack(images, 0), torch.cat(labels, 0), torch.tensor(label_lengths, dtype=torch.long), None

# %%
# 配置
NUM_TRAIN = 15000
NUM_VAL = 2000
EPOCHS = 80
BATCH_SIZE = 64
LR = 0.0005
OUTPUT_DIR = Path("/kaggle/working") if os.path.exists("/kaggle") else Path("kaggle_output")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

print("=" * 60)
print("七段数码管 CRNN 训练 (GPU)")
print("=" * 60)

# 生成数据
train_dataset = InMemorySeqDataset(NUM_TRAIN, seed=42)
val_dataset = InMemorySeqDataset(NUM_VAL, seed=999)

train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, collate_fn=ctc_collate, num_workers=0, pin_memory=True)
val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, collate_fn=ctc_collate, num_workers=0, pin_memory=True)

model = LightCRNN().to(device)
optimizer = torch.optim.Adam(model.parameters(), lr=LR)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=1e-6)
ctc_loss = nn.CTCLoss(blank=BLANK, zero_infinity=True)

param_count = sum(p.numel() for p in model.parameters())
print(f"模型参数量: {param_count:,} ({param_count * 4 / 1024:.0f} KB FP32)")
print(f"训练集: {len(train_dataset)}, 验证集: {len(val_dataset)}")
print(f"Epochs: {EPOCHS}, Batch: {BATCH_SIZE}, LR: {LR}")
print()

best_val_loss = float("inf")
best_acc = 0.0

for epoch in range(EPOCHS):
    # 训练
    model.train()
    total_loss = 0.0
    num_batches = 0
    t0 = time.time()

    for images, labels, label_lengths, _ in train_loader:
        images = images.to(device, non_blocking=True)
        labels = labels.to(device, non_blocking=True)
        output = model(images)
        T, B = output.size(0), images.size(0)
        log_probs = F.log_softmax(output, dim=2)
        loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        total_loss += loss.item()
        num_batches += 1

    avg_train_loss = total_loss / max(num_batches, 1)
    scheduler.step()

    # 验证
    model.eval()
    val_loss_total = 0.0
    val_batches = 0
    correct = 0
    total = 0

    with torch.no_grad():
        for images, labels, label_lengths, _ in val_loader:
            images = images.to(device, non_blocking=True)
            labels = labels.to(device, non_blocking=True)
            output = model(images)
            T, B = output.size(0), images.size(0)
            log_probs = F.log_softmax(output, dim=2)
            loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
            val_loss_total += loss.item()
            val_batches += 1

            preds = output.argmax(dim=2)
            offset = 0
            for b in range(B):
                pred_text = decode_prediction(preds[:, b].cpu().tolist())
                llen = label_lengths[b].item()
                true_text = "".join(IDX_TO_CHAR.get(i, "?") for i in labels[offset:offset+llen].cpu().tolist())
                if pred_text == true_text:
                    correct += 1
                total += 1
                offset += llen

    avg_val_loss = val_loss_total / max(val_batches, 1)
    accuracy = correct / max(total, 1)
    epoch_time = time.time() - t0

    if (epoch + 1) % 5 == 0 or epoch == 0:
        lr_now = optimizer.param_groups[0]["lr"]
        print(f"  Epoch {epoch+1:3d}/{EPOCHS} | "
              f"Train: {avg_train_loss:.4f} | Val: {avg_val_loss:.4f} | "
              f"Acc: {accuracy:.1%} | LR: {lr_now:.6f} | "
              f"Time: {epoch_time:.1f}s")

    if avg_val_loss < best_val_loss:
        best_val_loss = avg_val_loss
        torch.save(model.state_dict(), OUTPUT_DIR / "crnn_best.pth")

    if accuracy > best_acc:
        best_acc = accuracy

print(f"\n✅ 训练完成!")
print(f"  最佳验证 Loss: {best_val_loss:.4f}")
print(f"  最佳准确率: {best_acc:.1%}")

# %%
# ONNX 导出
print("\n📦 导出 ONNX...")
model.load_state_dict(torch.load(OUTPUT_DIR / "crnn_best.pth", map_location="cpu"))
model.eval().cpu()

dummy = torch.randn(1, 1, 64, 256)
onnx_path = OUTPUT_DIR / "crnn_seven_seg.onnx"

torch.onnx.export(
    model, dummy, str(onnx_path),
    input_names=["input"], output_names=["output"],
    dynamic_axes={"input": {3: "width"}, "output": {0: "time_steps"}},
    opset_version=17, dynamo=False,
)

size_kb = os.path.getsize(onnx_path) / 1024
print(f"  ONNX 导出完成: {onnx_path}")
print(f"  大小: {size_kb:.0f} KB")

# 验证
try:
    import onnxruntime as ort
    sess = ort.InferenceSession(str(onnx_path))
    out = sess.run(None, {"input": dummy.numpy()})
    print(f"  ONNX 验证成功, 输出形状: {out[0].shape}")
except ImportError:
    print("  (跳过 ONNX Runtime 验证, 未安装)")

print(f"\n🎉 完成! 模型在: {onnx_path}")
print(f"   下载此文件并复制到 app/src/main/assets/crnn_seven_seg.onnx")
