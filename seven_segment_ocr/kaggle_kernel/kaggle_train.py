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
import re
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, Dataset
from PIL import Image, ImageDraw, ImageFilter

# 检查 GPU/TPU
try:
    import torch_xla
    import torch_xla.core.xla_model as xm
    device = torch_xla.device()
    _IS_TPU = True
    print(f"🚀 使用设备: TPU ({device})")
except ImportError:
    _IS_TPU = False
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

LCD_THEMES_DARK_BG = [
    {"fg": (0, 255, 70), "dim": (0, 40, 10), "bg": (5, 20, 5)},
    {"fg": (255, 30, 30), "dim": (40, 5, 5), "bg": (10, 2, 2)},
    {"fg": (60, 160, 255), "dim": (8, 20, 40), "bg": (3, 8, 18)},
    {"fg": (255, 160, 30), "dim": (40, 25, 5), "bg": (12, 8, 2)},
    {"fg": (240, 240, 240), "dim": (30, 30, 30), "bg": (8, 8, 8)},
    {"fg": (180, 220, 40), "dim": (25, 30, 8), "bg": (60, 70, 50)},
]

LCD_THEMES_LIGHT_BG = [
    {"fg": (20, 20, 20), "dim": (200, 210, 200), "bg": (210, 220, 210)},
    {"fg": (30, 30, 80), "dim": (180, 185, 200), "bg": (190, 195, 210)},
    # 纯白底 + 黑字 (欧姆龙/鱼跃等主流血压计)
    {"fg": (15, 15, 15), "dim": (220, 225, 220), "bg": (235, 240, 235)},
    {"fg": (10, 10, 10), "dim": (230, 230, 230), "bg": (245, 245, 245)},
    # 绿色背光 + 黑字 (大量中端血压计)
    {"fg": (20, 30, 20), "dim": (120, 170, 110), "bg": (140, 195, 130)},
    {"fg": (15, 25, 15), "dim": (100, 160, 90), "bg": (160, 210, 150)},
    # 蓝色背光 + 深色字
    {"fg": (15, 15, 30), "dim": (100, 130, 180), "bg": (130, 160, 210)},
    # 琥珀/黄色背光
    {"fg": (40, 20, 5), "dim": (180, 150, 80), "bg": (200, 175, 100)},
    # 灰白色 LCD
    {"fg": (25, 25, 25), "dim": (195, 195, 200), "bg": (220, 220, 225)},
]

LCD_THEMES = LCD_THEMES_DARK_BG + LCD_THEMES_LIGHT_BG


def pick_lcd_theme() -> dict:
    """按权重选择 LCD 主题，亮底暗字占 45%。"""
    if random.random() < 0.45:
        return random.choice(LCD_THEMES_LIGHT_BG)
    return random.choice(LCD_THEMES_DARK_BG)


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


def _seg_polys_pointy(x, y, width, height, t, g):
    """经典尖角菱形风格段（默认）。"""
    half_h = height // 2
    return [
        [(x+g+t,y),(x+width-g-t,y),(x+width-g-t-t//2,y+t),(x+g+t+t//2,y+t)],
        [(x+width-t,y+g+t),(x+width,y+g+t),(x+width,y+half_h-g),(x+width-t//2,y+half_h-g+t//2),(x+width-t,y+half_h-g)],
        [(x+width-t,y+half_h+g),(x+width-t//2,y+half_h+g-t//2),(x+width,y+half_h+g),(x+width,y+height-g-t),(x+width-t,y+height-g-t)],
        [(x+g+t+t//2,y+height-t),(x+width-g-t-t//2,y+height-t),(x+width-g-t,y+height),(x+g+t,y+height)],
        [(x,y+half_h+g),(x+t//2,y+half_h+g-t//2),(x+t,y+half_h+g),(x+t,y+height-g-t),(x,y+height-g-t)],
        [(x,y+g+t),(x+t,y+g+t),(x+t,y+half_h-g),(x+t//2,y+half_h-g+t//2),(x,y+half_h-g)],
        [(x+g+t,y+half_h-t//2),(x+g+t+t//2,y+half_h-t),(x+width-g-t-t//2,y+half_h-t),(x+width-g-t,y+half_h-t//2),(x+width-g-t-t//2,y+half_h),(x+g+t+t//2,y+half_h)],
    ]


def _seg_polys_rect(x, y, width, height, t, g):
    """简洁矩形段风格（无尖角）。"""
    half_h = height // 2
    return [
        [(x+g+t,y),(x+width-g-t,y),(x+width-g-t,y+t),(x+g+t,y+t)],
        [(x+width-t,y+g+t),(x+width,y+g+t),(x+width,y+half_h-g),(x+width-t,y+half_h-g)],
        [(x+width-t,y+half_h+g),(x+width,y+half_h+g),(x+width,y+height-g-t),(x+width-t,y+height-g-t)],
        [(x+g+t,y+height-t),(x+width-g-t,y+height-t),(x+width-g-t,y+height),(x+g+t,y+height)],
        [(x,y+half_h+g),(x+t,y+half_h+g),(x+t,y+height-g-t),(x,y+height-g-t)],
        [(x,y+g+t),(x+t,y+g+t),(x+t,y+half_h-g),(x,y+half_h-g)],
        [(x+g+t,y+half_h-t//2),(x+width-g-t,y+half_h-t//2),(x+width-g-t,y+half_h+t//2),(x+g+t,y+half_h+t//2)],
    ]


def _seg_polys_rounded(x, y, width, height, t, g):
    """圆角段风格 — 通过在端点添加额外点来模拟圆角。"""
    half_h = height // 2
    r = max(1, t // 3)
    return [
        [(x+g+t+r,y),(x+width-g-t-r,y),(x+width-g-t,y+r),(x+width-g-t-r,y+t),(x+g+t+r,y+t),(x+g+t,y+r)],
        [(x+width-t,y+g+t+r),(x+width-r,y+g+t),(x+width,y+g+t+r),(x+width,y+half_h-g-r),(x+width-r,y+half_h-g),(x+width-t,y+half_h-g-r)],
        [(x+width-t,y+half_h+g+r),(x+width-r,y+half_h+g),(x+width,y+half_h+g+r),(x+width,y+height-g-t-r),(x+width-r,y+height-g-t),(x+width-t,y+height-g-t-r)],
        [(x+g+t+r,y+height-t),(x+width-g-t-r,y+height-t),(x+width-g-t,y+height-r),(x+width-g-t-r,y+height),(x+g+t+r,y+height),(x+g+t,y+height-r)],
        [(x,y+half_h+g+r),(x+r,y+half_h+g),(x+t,y+half_h+g+r),(x+t,y+height-g-t-r),(x+r,y+height-g-t),(x,y+height-g-t-r)],
        [(x,y+g+t+r),(x+r,y+g+t),(x+t,y+g+t+r),(x+t,y+half_h-g-r),(x+r,y+half_h-g),(x,y+half_h-g-r)],
        [(x+g+t+r,y+half_h-t//2),(x+width-g-t-r,y+half_h-t//2),(x+width-g-t,y+half_h),(x+width-g-t-r,y+half_h+t//2),(x+g+t+r,y+half_h+t//2),(x+g+t,y+half_h)],
    ]


def _seg_polys_thin(x, y, width, height, t, g):
    """细线段风格 — 用更薄的段体。"""
    t2 = max(1, t * 2 // 3)
    off = (t - t2) // 2
    return _seg_polys_pointy(x + off, y + off, width - off * 2, height - off * 2, t2, g)


SEGMENT_STYLES = [_seg_polys_pointy, _seg_polys_rect, _seg_polys_rounded, _seg_polys_thin]


def draw_seven_segment_digit(draw, digit, x, y, width, height, thickness, fg_color, dim_color=None, gap=1, skew=0.0, seg_style=None, defect_rate=0.0):
    segments = DIGIT_SEGMENTS[digit]
    t = thickness
    g = gap
    if seg_style is None:
        seg_style = random.choice(SEGMENT_STYLES)
    seg_polys = seg_style(x, y, width, height, t, g)
    if abs(skew) > 0.001:
        center_y = y + height / 2
        for poly in seg_polys:
            for i, (px, py) in enumerate(poly):
                poly[i] = (px + (py - center_y) * math.tan(skew), py)
    for i, (on, poly) in enumerate(zip(segments, seg_polys)):
        if on and defect_rate > 0 and random.random() < defect_rate:
            # 段缺陷：该亮的段不亮或变暗
            if dim_color:
                draw.polygon(poly, fill=dim_color)
            continue
        color = fg_color if on else dim_color
        if color is not None:
            draw.polygon(poly, fill=color)


def _jitter_color(color, amount=20):
    """对 RGB 颜色做微小亮度抖动。"""
    shift = random.randint(-amount, amount)
    return tuple(max(0, min(255, c + shift)) for c in color)


def render_number(text, digit_width=40, digit_height=70, thickness=6, theme=None, gap=1, spacing=8, padding=10, skew=0.0, show_dim=True, use_textured_bg=False):
    if theme is None:
        theme = pick_lcd_theme()
    fg, dim, bg = theme["fg"], theme["dim"] if show_dim else None, theme["bg"]
    seg_style = random.choice(SEGMENT_STYLES)
    # 每位数字亮度抖动概率
    jitter = random.random() < 0.3
    # 段缺陷概率 (5%的图片)
    defect_rate = random.uniform(0.05, 0.15) if random.random() < 0.05 else 0.0
    char_widths = []
    for ch in text:
        if ch in "0123456789": char_widths.append(digit_width)
        elif ch == "/": char_widths.append(digit_width // 2)
        elif ch == " ": char_widths.append(digit_width // 2)
        elif ch == "-": char_widths.append(digit_width // 2)
        elif ch == ".": char_widths.append(thickness * 2)
        elif ch == ":": char_widths.append(thickness * 2)
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
        cur_fg = _jitter_color(fg, 15) if jitter else fg
        if ch in "0123456789":
            draw_seven_segment_digit(draw, int(ch), cx, padding, cw, digit_height, thickness, cur_fg, dim, gap, skew, seg_style=seg_style, defect_rate=defect_rate)
        elif ch == "/":
            draw.line([(cx+cw, padding+2), (cx, padding+digit_height-2)], fill=cur_fg, width=max(2, thickness//2))
        elif ch == "-":
            mid_y = padding + digit_height // 2
            draw.rectangle([cx+2, mid_y-thickness//2, cx+cw-2, mid_y+thickness//2], fill=cur_fg)
        elif ch == ".":
            dot_y = padding + digit_height - thickness
            draw.ellipse([cx, dot_y, cx+thickness*2, dot_y+thickness*2], fill=cur_fg)
        elif ch == ":":
            # 冒号：上下两个圆点
            dot_r = max(1, thickness)
            dot1_y = padding + digit_height // 3 - dot_r
            dot2_y = padding + digit_height * 2 // 3 - dot_r
            draw.ellipse([cx, dot1_y, cx+dot_r*2, dot1_y+dot_r*2], fill=cur_fg)
            draw.ellipse([cx, dot2_y, cx+dot_r*2, dot2_y+dot_r*2], fill=cur_fg)
        cx += cw + spacing
    # 段发光效果 (15% 概率)
    if random.random() < 0.15:
        from PIL import ImageFilter
        glow = img.filter(ImageFilter.GaussianBlur(radius=max(1, thickness // 2)))
        img = Image.blend(img, glow, alpha=0.3)
    return img


# ── 医疗标签干扰文字 ──

# 多语言医疗术语（作为背景干扰叠加到合成图上）
MEDICAL_LABELS = {
    "bp": [
        # 英文
        "mmHg", "SYS", "DIA", "BP", "SYS/DIA", "Systolic", "Diastolic",
        # 中文
        "血压", "高压", "低压", "收缩压", "舒张压", "毫米汞柱",
        # 日文
        "血圧", "最高", "最低",
        # 韩文
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

def _try_load_font(size):
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

_LABEL_FONT_CACHE = {}
def _get_label_font(size):
    if size not in _LABEL_FONT_CACHE:
        _LABEL_FONT_CACHE[size] = _try_load_font(size)
    return _LABEL_FONT_CACHE[size]

# 确保 Kaggle 上有 CJK 字体
def _ensure_cjk_fonts():
    """在 Kaggle 环境下安装 CJK 字体（只运行一次）。"""
    if not os.path.exists("/kaggle"):
        return
    marker = "/tmp/.cjk_fonts_installed"
    if os.path.exists(marker):
        return
    noto_paths = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    ]
    if any(os.path.exists(p) for p in noto_paths):
        open(marker, "w").close()
        return
    print("  📦 安装 CJK 字体...")
    os.system("apt-get update -qq && apt-get install -y -qq fonts-noto-cjk >/dev/null 2>&1")
    open(marker, "w").close()
    _LABEL_FONT_CACHE.clear()  # 清除缓存以使用新字体

_ensure_cjk_fonts()


def add_medical_label(img, category=None):
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
        arr[:, :, c] = np.clip(arr[:, :, c] + random.uniform(-30, 30), 0, 255)
    return Image.fromarray(arr.astype(np.uint8))

def invert_polarity(img):
    arr = np.array(img, dtype=np.float32)
    return Image.fromarray((255.0 - arr).astype(np.uint8))

def perspective_transform(img, strength=0.08):
    """透视变换：模拟从不同角度拍摄 LCD 屏幕。"""
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


def embed_with_margin(img, scale_factor):
    """将渲染图嵌入更大画布，模拟数字在大屏幕中占比小的情况。
    scale_factor: 1.0 = 无额外边距, 2.0 = 图片只占画布 50%"""
    if scale_factor <= 1.05:
        return img
    w, h = img.size
    new_w = int(w * scale_factor)
    new_h = int(h * scale_factor)
    bg = img.getpixel((0, 0))
    canvas = Image.new("RGB", (new_w, new_h), bg)
    # 随机偏移嵌入位置 (不总是居中)
    max_x = new_w - w
    max_y = new_h - h
    ox = random.randint(int(max_x * 0.15), int(max_x * 0.85)) if max_x > 1 else 0
    oy = random.randint(int(max_y * 0.15), int(max_y * 0.85)) if max_y > 1 else 0
    canvas.paste(img, (ox, oy))
    return canvas

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


# ── 物理仿真增强 ──

def add_ghosting(img, ghost_alpha=0.15):
    """残影/烧屏：将图片整体平移一个微小偏移并以低透明度叠加，模拟上一帧残影。"""
    w, h = img.size
    dx = random.randint(-3, 3)
    dy = random.randint(-2, 2)
    if dx == 0 and dy == 0:
        dx = 1
    ghost = Image.new("RGB", (w, h), img.getpixel((0, 0)))
    ghost.paste(img, (dx, dy))
    alpha = random.uniform(0.08, ghost_alpha)
    return Image.blend(img, ghost, alpha)


def add_motion_blur(img, kernel_size=None):
    """方向性运动模糊。"""
    w, h = img.size
    if kernel_size is None:
        kernel_size = random.randint(3, max(4, min(w, h) // 15))
    if kernel_size < 3:
        return img
    angle = random.uniform(0, math.pi)
    kernel = np.zeros((kernel_size, kernel_size), dtype=np.float32)
    cx, cy = kernel_size // 2, kernel_size // 2
    for i in range(kernel_size):
        x = int(round(cx + (i - cx) * math.cos(angle)))
        y = int(round(cy + (i - cy) * math.sin(angle)))
        if 0 <= x < kernel_size and 0 <= y < kernel_size:
            kernel[y, x] = 1.0
    if kernel.sum() > 0:
        kernel /= kernel.sum()
    from PIL import ImageFilter
    pil_kernel = ImageFilter.Kernel(
        size=(kernel_size, kernel_size),
        kernel=kernel.flatten().tolist(),
        scale=1, offset=0,
    )
    try:
        return img.filter(pil_kernel)
    except Exception:
        return img


def add_barrel_distortion(img, strength=None):
    """桶形畸变：模拟手机微距拍摄。"""
    arr = np.array(img, dtype=np.float32)
    h, w = arr.shape[:2]
    if strength is None:
        strength = random.uniform(0.05, 0.25)
    cx, cy = w / 2, h / 2
    max_r = math.sqrt(cx ** 2 + cy ** 2)
    # 创建映射
    y_coords, x_coords = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = x_coords - cx
    dy = y_coords - cy
    r = np.sqrt(dx ** 2 + dy ** 2) / max_r
    factor = 1.0 + strength * r ** 2
    src_x = (cx + dx / factor).clip(0, w - 1)
    src_y = (cy + dy / factor).clip(0, h - 1)
    # 最近邻插值
    src_xi = src_x.astype(np.int32)
    src_yi = src_y.astype(np.int32)
    result = arr[src_yi, src_xi]
    return Image.fromarray(result.astype(np.uint8))


def add_cast_shadow(img):
    """投射阴影：模拟手/手机阴影落在屏幕上。"""
    w, h = img.size
    arr = np.array(img, dtype=np.float32)
    mask = np.ones((h, w), dtype=np.float32)
    # 生成一个从某个方向渐变的阴影带
    direction = random.choice(["left", "right", "top", "bottom", "diagonal"])
    shadow_width = random.uniform(0.3, 0.6)
    shadow_strength = random.uniform(0.3, 0.7)
    if direction == "left":
        border = int(w * shadow_width)
        for x in range(border):
            mask[:, x] = 1.0 - shadow_strength * (1.0 - x / border)
    elif direction == "right":
        border = int(w * (1 - shadow_width))
        for x in range(border, w):
            mask[:, x] = 1.0 - shadow_strength * ((x - border) / (w - border))
    elif direction == "top":
        border = int(h * shadow_width)
        for y in range(border):
            mask[y, :] = 1.0 - shadow_strength * (1.0 - y / border)
    elif direction == "bottom":
        border = int(h * (1 - shadow_width))
        for y in range(border, h):
            mask[y, :] = 1.0 - shadow_strength * ((y - border) / (h - border))
    else:  # diagonal
        for y in range(h):
            for x in range(w):
                d = (x / w + y / h) / 2
                if d < shadow_width:
                    mask[y, x] = 1.0 - shadow_strength * (1.0 - d / shadow_width)
    for c in range(3):
        arr[:, :, c] *= mask
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def add_chromatic_aberration(img, shift=None):
    """色散/紫边：RGB 通道微小偏移。"""
    if shift is None:
        shift = random.randint(1, 3)
    arr = np.array(img)
    h, w = arr.shape[:2]
    result = arr.copy()
    # 红通道向一个方向偏移，蓝通道向另一个方向偏移
    dx_r = random.choice([-shift, shift])
    dx_b = -dx_r
    if dx_r > 0:
        result[:, dx_r:, 0] = arr[:, :w - dx_r, 0]
    elif dx_r < 0:
        result[:, :w + dx_r, 0] = arr[:, -dx_r:, 0]
    if dx_b > 0:
        result[:, dx_b:, 2] = arr[:, :w - dx_b, 2]
    elif dx_b < 0:
        result[:, :w + dx_b, 2] = arr[:, -dx_b:, 2]
    return Image.fromarray(result)


def add_scratches(img, num_scratches=None):
    """屏幕划痕：随机折线。"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size
    if num_scratches is None:
        num_scratches = random.randint(1, 3)
    for _ in range(num_scratches):
        points = []
        x, y = random.randint(0, w), random.randint(0, h)
        segs = random.randint(2, 5)
        for _ in range(segs):
            x += random.randint(-w // 3, w // 3)
            y += random.randint(-h // 4, h // 4)
            points.append((max(0, min(w - 1, x)), max(0, min(h - 1, y))))
        if len(points) >= 2:
            avg = sum(img.getpixel((0, 0))) / 3
            color = random.choice([(200, 200, 200), (255, 255, 255)] if avg < 128 else [(40, 40, 40), (80, 80, 80)])
            draw.line(points, fill=color, width=1)
    return img


def add_smudge(img):
    """指纹/油污：局部高斯模糊区域。"""
    w, h = img.size
    # 随机选取一块区域
    rw = random.randint(w // 4, w // 2)
    rh = random.randint(h // 4, h // 2)
    rx = random.randint(0, max(0, w - rw))
    ry = random.randint(0, max(0, h - rh))
    crop = img.crop((rx, ry, rx + rw, ry + rh))
    blur_r = random.uniform(1.5, 3.0)
    crop = crop.filter(ImageFilter.GaussianBlur(radius=blur_r))
    # 降低对比度
    arr = np.array(crop, dtype=np.float32)
    arr = arr * random.uniform(0.8, 0.95) + random.uniform(5, 15)
    crop = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    img = img.copy()
    img.paste(crop, (rx, ry))
    return img


def augment_image(img, difficulty="normal"):
    if difficulty == "easy":
        img = adjust_brightness_contrast(img, random.uniform(0.85, 1.15), random.uniform(0.9, 1.1))
        if random.random() < 0.3:
            img = add_noise(img, random.uniform(0.01, 0.03))
        if random.random() < 0.1: img = add_ghosting(img, 0.08)
        return img

    if difficulty == "hard":
        if random.random() < 0.35: img = add_background_clutter(img)
        if random.random() < 0.25: img = add_edge_frame(img)
        # 视角对比度绑定：透视变换时同时降低对比度
        if random.random() < 0.6:
            ps = random.uniform(0.06, 0.20)
            img = perspective_transform(img, ps)
            if ps > 0.12:
                img = adjust_brightness_contrast(img, random.uniform(0.6, 0.85), random.uniform(0.4, 0.65))
        if random.random() < 0.4: img = adjust_brightness_contrast(img, random.uniform(0.6, 0.9), random.uniform(0.4, 0.6))
        if random.random() < 0.5: img = add_reflection(img, random.uniform(0.15, 0.5))
        if random.random() < 0.4: img = add_color_cast(img)
        # 新增物理仿真
        if random.random() < 0.20: img = add_ghosting(img)
        if random.random() < 0.20: img = add_motion_blur(img)
        if random.random() < 0.15: img = add_barrel_distortion(img)
        if random.random() < 0.20: img = add_cast_shadow(img)
        if random.random() < 0.12: img = add_chromatic_aberration(img)
        if random.random() < 0.10: img = add_scratches(img)
        if random.random() < 0.10: img = add_smudge(img)
        # 原有增强
        if random.random() < 0.5: img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.8, 2.5)))
        if random.random() < 0.5: img = add_noise(img, random.uniform(0.05, 0.15))
        if random.random() < 0.3: img = add_salt_pepper_noise(img, random.uniform(0.01, 0.05))
        if random.random() < 0.3: img = add_jpeg_artifacts(img, random.randint(15, 40))
        if random.random() < 0.2: img = partial_occlusion(img, max_rects=2)
        if random.random() < 0.6: img = random_rotate(img, max_angle=10.0)
        if random.random() < 0.08: img = invert_polarity(img)
        return img

    # normal
    img = adjust_brightness_contrast(img, random.uniform(0.7, 1.3), random.uniform(0.8, 1.3))
    if random.random() < 0.7: img = add_noise(img, random.uniform(0.02, 0.10))
    if random.random() < 0.4: img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.3, 1.5)))
    if random.random() < 0.5: img = random_rotate(img, max_angle=5.0)
    if random.random() < 0.2: img = add_reflection(img, random.uniform(0.1, 0.25))
    if random.random() < 0.15: img = add_color_cast(img)
    if random.random() < 0.25: img = perspective_transform(img, random.uniform(0.03, 0.10))
    if random.random() < 0.1: img = add_background_clutter(img)
    # normal 也加入部分物理仿真（弱强度）
    if random.random() < 0.10: img = add_ghosting(img, 0.10)
    if random.random() < 0.08: img = add_barrel_distortion(img, random.uniform(0.03, 0.10))
    if random.random() < 0.08: img = add_cast_shadow(img)
    if random.random() < 0.05: img = add_chromatic_aberration(img, 1)
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


def postprocess_ctc(text):
    """CTC 解码后处理：修正常见错误模式。"""
    if not text:
        return text
    # 去除首尾空格
    text = text.strip()
    # 合并连续空格
    while "  " in text:
        text = text.replace("  ", " ")
    # 去除首尾多余的分隔符
    text = text.strip("/.-")
    # 去除连续重复的分隔符 (如 "//" -> "/", ".." -> ".")
    text = re.sub(r'([/.\-])\1+', r'\1', text)
    return text


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
            (random_bp, 0.35, "bp"), (random_hr, 0.15, "hr"), (random_temp, 0.10, "temp"),
            (random_glucose, 0.10, "spo2"), (random_spo2, 0.10, "spo2"), (random_weight, 0.05, "weight"),
            (random_generic, 0.15, "generic"),
        ]

        print(f"  生成 {num_samples} 张序列图片...")
        for i in range(num_samples):
            r = random.random()
            cumul = 0.0
            gen_func = random_generic
            label_cat = "generic"
            for func, w, cat in generators:
                cumul += w
                if r < cumul:
                    gen_func = func
                    label_cat = cat
                    break

            text = gen_func()
            theme = pick_lcd_theme()
            # 更大的尺寸范围：包含很小的数字 (模拟远距离拍摄)
            dw = random.randint(18, 55)
            dh = random.randint(35, 95)
            # 更宽的间距范围：紧凑(0) ~ 松散(22)
            sp = random.choice([
                random.randint(0, 2),    # 紧凑型 (20%)
                random.randint(3, 10),   # 标准型 (40%)
                random.randint(3, 10),
                random.randint(11, 22),  # 松散型 (20%)
                random.randint(3, 14),   # 混合   (20%)
            ])

            img = render_number(
                text, digit_width=dw, digit_height=dh,
                thickness=random.randint(2, max(3, dw // 4)),
                theme=theme, gap=random.randint(0, 4),
                spacing=sp, padding=random.randint(3, 22),
                skew=random.uniform(-0.18, 0.18),
                show_dim=random.random() < 0.5,
                use_textured_bg=random.random() < 0.4,
            )

            # 35%概率叠加医疗标签干扰文字（在 embed/augment 之前，使标签与数字一起经历变换）
            if random.random() < 0.35:
                img = add_medical_label(img, category=label_cat)

            # 随机在更大画布中嵌入 (30%概率, 模拟屏幕比数字大很多)
            if random.random() < 0.30:
                scale = random.uniform(1.3, 2.5)
                img = embed_with_margin(img, scale)

            r2 = random.random()
            difficulty = "easy" if r2 < 0.15 else ("normal" if r2 < 0.45 else "hard")
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
    def __init__(self, num_classes=NUM_CLASSES, rnn_hidden=96):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv2d(1, 24, 3, 1, 1, bias=False), nn.BatchNorm2d(24), nn.ReLU(), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(24, 48), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(48, 64), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(64, 96), nn.MaxPool2d(2, 2),
            DepthwiseSeparableConv(96, 96), nn.AvgPool2d((4, 1)),
        )
        self.rnn = nn.LSTM(input_size=96, hidden_size=rnn_hidden, num_layers=2, bidirectional=True, batch_first=False, dropout=0.3)
        self.drop = nn.Dropout(0.2)
        self.fc = nn.Linear(rnn_hidden * 2, num_classes)

    def forward(self, x):
        conv = self.cnn(x).squeeze(2).permute(2, 0, 1)
        rnn_out, _ = self.rnn(conv)
        return self.fc(self.drop(rnn_out))


# ─────────────────────────────────────────────────────────
# 训练
# ─────────────────────────────────────────────────────────

def ctc_collate(batch):
    images, labels, label_lengths, img_widths = zip(*batch)
    return torch.stack(images, 0), torch.cat(labels, 0), torch.tensor(label_lengths, dtype=torch.long), None

# %%
# 配置
NUM_TRAIN = 35000
NUM_VAL = 4000
EPOCHS = 80
BATCH_SIZE = 64
LR = 0.0005
OUTPUT_DIR = Path("/kaggle/working") if os.path.exists("/kaggle") else Path("kaggle_output")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

print("=" * 60)
print("七段数码管 CRNN 训练 (GPU/TPU)")
print("=" * 60)

# 生成数据
train_dataset = InMemorySeqDataset(NUM_TRAIN, seed=42)
val_dataset = InMemorySeqDataset(NUM_VAL, seed=999)

_pin = not _IS_TPU
train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, collate_fn=ctc_collate, num_workers=0, pin_memory=_pin)
val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, collate_fn=ctc_collate, num_workers=0, pin_memory=_pin)

model = LightCRNN().to(device)
optimizer = torch.optim.Adam(model.parameters(), lr=LR, weight_decay=1e-4)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=1e-6)
ctc_loss = nn.CTCLoss(blank=BLANK, zero_infinity=True)

param_count = sum(p.numel() for p in model.parameters())
print(f"模型参数量: {param_count:,} ({param_count * 4 / 1024:.0f} KB FP32)")
print(f"训练集: {len(train_dataset)}, 验证集: {len(val_dataset)}")
print(f"Epochs: {EPOCHS}, Batch: {BATCH_SIZE}, LR: {LR}")
print()

best_val_loss = float("inf")
best_acc = 0.0
epoch_history = []

for epoch in range(EPOCHS):
    # 训练
    model.train()
    total_loss = 0.0
    num_batches = 0
    t0 = time.time()

    for images, labels, label_lengths, _ in train_loader:
        images = images.to(device)
        labels = labels.to(device)
        output = model(images)
        T, B = output.size(0), images.size(0)
        log_probs = F.log_softmax(output, dim=2)
        loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
        optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        if _IS_TPU:
            xm.optimizer_step(optimizer)
            xm.mark_step()
        else:
            optimizer.step()
        total_loss += loss.item()
        num_batches += 1

    avg_train_loss = total_loss / max(num_batches, 1)
    train_time = time.time() - t0
    scheduler.step()

    # 验证
    model.eval()
    val_loss_total = 0.0
    val_batches = 0
    correct = 0
    correct_post = 0
    total = 0
    sample_preds = []  # 收集样本用于日志
    error_examples = []  # 收集错误样本

    with torch.no_grad():
        for images, labels, label_lengths, _ in val_loader:
            images = images.to(device)
            labels = labels.to(device)
            output = model(images)
            T, B = output.size(0), images.size(0)
            log_probs = F.log_softmax(output, dim=2)
            loss = ctc_loss(log_probs, labels, torch.full((B,), T, dtype=torch.long).to(device), label_lengths.to(device))
            val_loss_total += loss.item()
            val_batches += 1

            preds = output.argmax(dim=2)
            offset = 0
            for b in range(B):
                raw_text = decode_prediction(preds[:, b].cpu().tolist())
                post_text = postprocess_ctc(raw_text)
                llen = label_lengths[b].item()
                true_text = "".join(IDX_TO_CHAR.get(i, "?") for i in labels[offset:offset+llen].cpu().tolist())
                if raw_text == true_text:
                    correct += 1
                if post_text == true_text:
                    correct_post += 1
                elif len(error_examples) < 15:
                    error_examples.append((true_text, raw_text, post_text))
                if len(sample_preds) < 8:
                    sample_preds.append((true_text, raw_text, post_text))
                total += 1
                offset += llen

    avg_val_loss = val_loss_total / max(val_batches, 1)
    accuracy = correct / max(total, 1)
    accuracy_post = correct_post / max(total, 1)
    val_time = time.time() - t0 - train_time
    lr_now = optimizer.param_groups[0]["lr"]

    epoch_history.append({
        "epoch": epoch + 1,
        "train_loss": avg_train_loss,
        "val_loss": avg_val_loss,
        "acc_raw": accuracy,
        "acc_post": accuracy_post,
        "lr": lr_now,
    })

    # ── 每 epoch 都输出基本信息 ──
    improved_marker = ""
    if avg_val_loss < best_val_loss:
        improved_marker = " ★ best_loss"
        best_val_loss = avg_val_loss
        torch.save(model.state_dict(), OUTPUT_DIR / "crnn_best.pth")
    if accuracy_post > best_acc:
        improved_marker += " ★ best_acc"
        best_acc = accuracy_post

    print(f"  [{epoch+1:3d}/{EPOCHS}] "
          f"loss={avg_train_loss:.4f}/{avg_val_loss:.4f} "
          f"acc={accuracy:.1%}→{accuracy_post:.1%} "
          f"lr={lr_now:.2e} "
          f"t={train_time:.1f}+{val_time:.1f}s"
          f"{improved_marker}")

    # ── 每 5 个 epoch 输出详细日志 ──
    if (epoch + 1) % 5 == 0 or epoch == 0 or (epoch + 1) == EPOCHS:
        print(f"\n  {'─' * 50}")
        print(f"  📊 Epoch {epoch+1} 详细报告")
        print(f"  {'─' * 50}")

        # 样本预测
        print(f"  🔍 样本预测 (最多8个):")
        for gt, raw, post in sample_preds:
            status = "✓" if post == gt else "✗"
            if raw != post:
                print(f"    {status} GT='{gt}' → Raw='{raw}' → Post='{post}'")
            else:
                print(f"    {status} GT='{gt}' → Pred='{raw}'")

        # 错误样本
        if error_examples:
            print(f"  ❌ 错误样本 (最多15个):")
            for gt, raw, post in error_examples[:10]:
                if raw != post:
                    print(f"    GT='{gt}' → Raw='{raw}' → Post='{post}'")
                else:
                    print(f"    GT='{gt}' → Pred='{raw}'")

        # 后处理提升统计
        delta = accuracy_post - accuracy
        if delta > 0:
            print(f"  📈 后处理提升: {accuracy:.1%} → {accuracy_post:.1%} (+{delta:.1%})")
        else:
            print(f"  📈 后处理: {accuracy:.1%} → {accuracy_post:.1%} (无提升)")

        # 训练历史趋势
        if len(epoch_history) >= 5:
            recent = epoch_history[-5:]
            loss_trend = recent[-1]["val_loss"] - recent[0]["val_loss"]
            acc_trend = recent[-1]["acc_post"] - recent[0]["acc_post"]
            print(f"  📉 最近5epoch趋势: val_loss {loss_trend:+.4f}, acc {acc_trend:+.1%}")

        print(f"  {'─' * 50}\n")

print(f"\n{'=' * 60}")
print(f"✅ 训练完成!")
print(f"  最佳验证 Loss: {best_val_loss:.4f}")
print(f"  最佳准确率 (含后处理): {best_acc:.1%}")
print(f"{'=' * 60}")

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
