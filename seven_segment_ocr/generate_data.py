"""
七段数码管合成数据生成器
======================
生成用于训练轻量 OCR 模型的七段管数字图片。

支持的增强变换:
- LCD 颜色 (绿/红/蓝/橙/白/黄绿)
- 暗底亮字: 黑/深绿/深蓝/深灰/深橄榄底
- 亮底暗字: 白底/灰绿底/灰蓝底/绿色背光/蓝色背光/琥珀背光
- 段粗细变化
- 旋转/倾斜
- 高斯噪声
- 模糊
- 亮度/对比度变化
- 段间间隙变化（模拟真实 LCD 显示的段间距）
- 真实世界背景纹理 (木纹/金属/塑料/布料/医疗设备表面)
- 背景图案 (格子/条纹/圆点/干扰文字)

输出格式:
- 单数字分类: images/digit_{label}_{id}.png + labels.csv
- 多数字序列 (CRNN+CTC): images/seq_{label}_{id}.png + sequences.csv
"""

import argparse
import csv
import math
import os
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ── 七段管数字定义 ──────────────────────────────────────────
# 七段排列:
#  aaaa
# f    b
# f    b
#  gggg
# e    c
# e    c
#  dddd
#
# 每个数字由 7 个布尔值表示: [a, b, c, d, e, f, g]
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

# LCD 颜色主题: (段亮色, 段暗色, 背景色)
# ── 暗底亮字主题 ──────────────────────────────────────────
LCD_THEMES_DARK_BG = [
    # 经典绿色 LCD
    {"fg": (0, 255, 70), "dim": (0, 40, 10), "bg": (5, 20, 5)},
    # 红色 LED
    {"fg": (255, 30, 30), "dim": (40, 5, 5), "bg": (10, 2, 2)},
    # 蓝色 LCD
    {"fg": (60, 160, 255), "dim": (8, 20, 40), "bg": (3, 8, 18)},
    # 橙色 LED
    {"fg": (255, 160, 30), "dim": (40, 25, 5), "bg": (12, 8, 2)},
    # 白色 LCD (如医疗设备)
    {"fg": (240, 240, 240), "dim": (30, 30, 30), "bg": (8, 8, 8)},
    # 黄绿色 (老式计算器)
    {"fg": (180, 220, 40), "dim": (25, 30, 8), "bg": (60, 70, 50)},
]

# ── 亮底暗字主题 (血压计/体温计常见) ────────────────────────
LCD_THEMES_LIGHT_BG = [
    # 浅灰绿底 + 黑字 (经典血压计)
    {"fg": (20, 20, 20), "dim": (200, 210, 200), "bg": (210, 220, 210)},
    # 浅蓝灰底 + 深蓝字
    {"fg": (30, 30, 80), "dim": (180, 185, 200), "bg": (190, 195, 210)},
    # 纯白底 + 黑字 (欧姆龙/鱼跃等主流血压计)
    {"fg": (15, 15, 15), "dim": (220, 225, 220), "bg": (235, 240, 235)},
    {"fg": (10, 10, 10), "dim": (230, 230, 230), "bg": (245, 245, 245)},
    # 绿色背光 + 黑字 (大量中端血压计)
    {"fg": (20, 30, 20), "dim": (120, 170, 110), "bg": (140, 195, 130)},
    {"fg": (15, 25, 15), "dim": (100, 160, 90), "bg": (160, 210, 150)},
    # 蓝色背光 + 深色字 (部分高端设备)
    {"fg": (15, 15, 30), "dim": (100, 130, 180), "bg": (130, 160, 210)},
    # 琥珀/黄色背光 (部分医疗设备)
    {"fg": (40, 20, 5), "dim": (180, 150, 80), "bg": (200, 175, 100)},
    # 灰白色 LCD (中性色调)
    {"fg": (25, 25, 25), "dim": (195, 195, 200), "bg": (220, 220, 225)},
]

# 合并所有主题（保持向后兼容）
LCD_THEMES = LCD_THEMES_DARK_BG + LCD_THEMES_LIGHT_BG


def pick_lcd_theme() -> dict:
    """按权重选择 LCD 主题，亮底暗字占 45%。"""
    if random.random() < 0.45:
        return random.choice(LCD_THEMES_LIGHT_BG)
    return random.choice(LCD_THEMES_DARK_BG)


# ── 背景纹理生成 ──────────────────────────────────────────

def _perlin_noise_2d(shape, scale=32.0):
    """简化的 Perlin-like 噪声（多层随机梯度插值）。"""
    h, w = shape
    noise = np.zeros((h, w), dtype=np.float32)
    for octave in range(4):
        freq = 2 ** octave
        s = scale / freq
        gh = max(2, int(h / s) + 2)
        gw = max(2, int(w / s) + 2)
        grid = np.random.randn(gh, gw).astype(np.float32)
        # 双线性插值放大
        from PIL import Image as _Img
        grid_img = _Img.fromarray(grid, mode="F")
        grid_up = np.array(grid_img.resize((w, h), _Img.BILINEAR))
        noise += grid_up * (0.5 ** octave)
    # 归一化到 [0, 1]
    noise = (noise - noise.min()) / (noise.max() - noise.min() + 1e-8)
    return noise


def generate_textured_background(w: int, h: int, base_color: tuple) -> Image.Image:
    """生成带纹理的背景（模拟真实设备表面）。

    随机选择一种纹理风格:
    - 塑料/磨砂表面 (Perlin 噪声)
    - 金属拉丝 (水平条纹)
    - 木纹 (带结纹的条纹)
    - 布料/织物 (交叉网格)
    - 医疗设备面板 (渐变 + 小凹凸)
    - 桌面表面 (大理纹路)
    """
    style = random.choice(["plastic", "metal", "wood", "fabric", "medical", "marble"])
    r, g, b = base_color

    if style == "plastic":
        # 磨砂塑料: 低频 Perlin 噪声
        noise = _perlin_noise_2d((h, w), scale=max(16, min(w, h) // 2))
        intensity = random.uniform(8, 25)
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :, 0] = r + (noise - 0.5) * intensity
        arr[:, :, 1] = g + (noise - 0.5) * intensity
        arr[:, :, 2] = b + (noise - 0.5) * intensity

    elif style == "metal":
        # 金属拉丝: 水平细条纹
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :] = [r, g, b]
        for y in range(h):
            line_noise = random.gauss(0, random.uniform(3, 12))
            arr[y, :] += line_noise
        # 加微弱 Perlin 给一些不均匀
        noise = _perlin_noise_2d((h, w), scale=max(16, w // 3))
        arr += (noise[:, :, None] - 0.5) * 8

    elif style == "wood":
        # 木纹: 正弦波 + 噪声
        arr = np.zeros((h, w, 3), dtype=np.float32)
        freq = random.uniform(0.02, 0.06)
        angle = random.uniform(-0.3, 0.3)
        yy, xx = np.mgrid[0:h, 0:w]
        wave = np.sin((xx * math.cos(angle) + yy * math.sin(angle)) * freq * 2 * math.pi)
        noise = _perlin_noise_2d((h, w), scale=max(16, w // 4))
        pattern = wave * 0.5 + noise * 0.5
        # 暖色调偏移
        wood_r = r + random.uniform(5, 15)
        wood_g = g + random.uniform(0, 8)
        intensity = random.uniform(10, 30)
        arr[:, :, 0] = wood_r + pattern * intensity
        arr[:, :, 1] = wood_g + pattern * intensity * 0.7
        arr[:, :, 2] = b + pattern * intensity * 0.4

    elif style == "fabric":
        # 织物: 交叉网格（向量化）
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :] = [r, g, b]
        grid_size = random.randint(3, 8)
        intensity = random.uniform(5, 18)
        yy, xx = np.mgrid[0:h, 0:w]
        mask = (yy % grid_size < grid_size // 2) ^ (xx % grid_size < grid_size // 2)
        arr[mask] += intensity
        arr[~mask] -= intensity

    elif style == "medical":
        # 医疗设备面板: 渐变 + 细微凹凸
        arr = np.zeros((h, w, 3), dtype=np.float32)
        # 渐变方向
        grad_dir = random.choice(["h", "v", "d"])
        if grad_dir == "h":
            grad = np.linspace(0, 1, w).reshape(1, -1)
            grad = np.broadcast_to(grad, (h, w))
        elif grad_dir == "v":
            grad = np.linspace(0, 1, h).reshape(-1, 1)
            grad = np.broadcast_to(grad, (h, w))
        else:
            yy, xx = np.mgrid[0:h, 0:w]
            grad = (xx / max(1, w - 1) + yy / max(1, h - 1)) / 2
        grad_intensity = random.uniform(5, 20)
        arr[:, :, 0] = r + (grad - 0.5) * grad_intensity
        arr[:, :, 1] = g + (grad - 0.5) * grad_intensity
        arr[:, :, 2] = b + (grad - 0.5) * grad_intensity
        # 细微凹凸
        bump = _perlin_noise_2d((h, w), scale=max(8, min(w, h) // 4))
        arr += (bump[:, :, None] - 0.5) * 6

    else:  # marble
        # 大理石: 多层 Perlin + 正弦扭曲
        n1 = _perlin_noise_2d((h, w), scale=max(16, min(w, h) // 2))
        n2 = _perlin_noise_2d((h, w), scale=max(8, min(w, h) // 4))
        yy, xx = np.mgrid[0:h, 0:w]
        pattern = np.sin((xx / max(1, w) * 4 + n1 * 3) * math.pi)
        pattern = pattern * 0.5 + n2 * 0.3
        intensity = random.uniform(8, 20)
        arr = np.zeros((h, w, 3), dtype=np.float32)
        arr[:, :, 0] = r + pattern * intensity
        arr[:, :, 1] = g + pattern * intensity * 0.9
        arr[:, :, 2] = b + pattern * intensity * 0.8

    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def add_background_clutter(img: Image.Image) -> Image.Image:
    """在背景上添加干扰元素（随机线条/形状/文字，模拟桌面杂物/设备标签等）。"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size

    num_elements = random.randint(2, 6)
    for _ in range(num_elements):
        elem_type = random.choice(["line", "rect", "circle", "dots"])
        # 使用接近背景的颜色（不能比数字更亮）
        bg_r, bg_g, bg_b = img.getpixel((0, 0))
        dr = random.randint(-15, 15)
        dg = random.randint(-15, 15)
        db = random.randint(-15, 15)
        color = (
            max(0, min(255, bg_r + dr)),
            max(0, min(255, bg_g + dg)),
            max(0, min(255, bg_b + db)),
        )

        if elem_type == "line":
            x1, y1 = random.randint(0, w), random.randint(0, h)
            x2, y2 = random.randint(0, w), random.randint(0, h)
            draw.line([(x1, y1), (x2, y2)], fill=color, width=random.randint(1, 2))

        elif elem_type == "rect":
            x1 = random.randint(0, w - 5)
            y1 = random.randint(0, h - 5)
            x2 = x1 + random.randint(3, w // 4)
            y2 = y1 + random.randint(3, h // 4)
            if random.random() < 0.5:
                draw.rectangle([x1, y1, x2, y2], outline=color, width=1)
            else:
                draw.rectangle([x1, y1, x2, y2], fill=color)

        elif elem_type == "circle":
            cx = random.randint(0, w)
            cy = random.randint(0, h)
            r = random.randint(2, max(3, min(w, h) // 6))
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=1)

        else:  # dots
            for _ in range(random.randint(3, 10)):
                dx = random.randint(0, w - 1)
                dy = random.randint(0, h - 1)
                draw.point((dx, dy), fill=color)

    return img


def add_edge_frame(img: Image.Image) -> Image.Image:
    """给图片添加设备边框（模拟显示窗口边缘）。"""
    img = img.copy()
    draw = ImageDraw.Draw(img)
    w, h = img.size

    frame_type = random.choice(["thin", "bevel", "rounded_rect"])
    bg = img.getpixel((0, 0))
    # 比背景稍亮或暗的边框色
    shift = random.choice([-30, -20, 20, 30])
    frame_color = tuple(max(0, min(255, c + shift)) for c in bg)

    if frame_type == "thin":
        bw = random.randint(1, 3)
        draw.rectangle([0, 0, w - 1, h - 1], outline=frame_color, width=bw)

    elif frame_type == "bevel":
        bw = random.randint(2, 5)
        light = tuple(min(255, c + 40) for c in bg)
        dark = tuple(max(0, c - 40) for c in bg)
        draw.line([(0, 0), (w - 1, 0)], fill=light, width=bw)
        draw.line([(0, 0), (0, h - 1)], fill=light, width=bw)
        draw.line([(0, h - 1), (w - 1, h - 1)], fill=dark, width=bw)
        draw.line([(w - 1, 0), (w - 1, h - 1)], fill=dark, width=bw)

    else:  # rounded_rect
        bw = random.randint(1, 3)
        margin = bw
        draw.rounded_rectangle(
            [margin, margin, w - 1 - margin, h - 1 - margin],
            radius=random.randint(2, 6),
            outline=frame_color,
            width=bw,
        )

    return img


# ── 物理仿真增强 ──


def add_ghosting(img: Image.Image, ghost_alpha: float = 0.15) -> Image.Image:
    """残影/烧屏：将图片整体平移一个微小偏移并以低透明度叠加。"""
    w, h = img.size
    dx = random.randint(-3, 3)
    dy = random.randint(-2, 2)
    if dx == 0 and dy == 0:
        dx = 1
    ghost = Image.new("RGB", (w, h), img.getpixel((0, 0)))
    ghost.paste(img, (dx, dy))
    alpha = random.uniform(0.08, ghost_alpha)
    return Image.blend(img, ghost, alpha)


def add_motion_blur(img: Image.Image, kernel_size: int | None = None) -> Image.Image:
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
    pil_kernel = ImageFilter.Kernel(
        size=(kernel_size, kernel_size),
        kernel=kernel.flatten().tolist(),
        scale=1,
        offset=0,
    )
    try:
        return img.filter(pil_kernel)
    except Exception:
        return img


def add_barrel_distortion(
    img: Image.Image, strength: float | None = None
) -> Image.Image:
    """桶形畸变：模拟手机微距拍摄。"""
    arr = np.array(img, dtype=np.float32)
    h, w = arr.shape[:2]
    if strength is None:
        strength = random.uniform(0.05, 0.25)
    cx, cy = w / 2, h / 2
    max_r = math.sqrt(cx**2 + cy**2)
    y_coords, x_coords = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = x_coords - cx
    dy = y_coords - cy
    r = np.sqrt(dx**2 + dy**2) / max_r
    factor = 1.0 + strength * r**2
    src_x = (cx + dx / factor).clip(0, w - 1)
    src_y = (cy + dy / factor).clip(0, h - 1)
    src_xi = src_x.astype(np.int32)
    src_yi = src_y.astype(np.int32)
    result = arr[src_yi, src_xi]
    return Image.fromarray(result.astype(np.uint8))


def add_cast_shadow(img: Image.Image) -> Image.Image:
    """投射阴影：模拟手/手机阴影落在屏幕上。"""
    w, h = img.size
    arr = np.array(img, dtype=np.float32)
    mask = np.ones((h, w), dtype=np.float32)
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
        yy, xx = np.mgrid[0:h, 0:w]
        d = (xx / w + yy / h) / 2
        inside = d < shadow_width
        mask[inside] = 1.0 - shadow_strength * (1.0 - d[inside] / shadow_width)
    for c in range(3):
        arr[:, :, c] *= mask
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def add_chromatic_aberration(
    img: Image.Image, shift: int | None = None
) -> Image.Image:
    """色散/紫边：RGB 通道微小偏移。"""
    if shift is None:
        shift = random.randint(1, 3)
    arr = np.array(img)
    h, w = arr.shape[:2]
    result = arr.copy()
    dx_r = random.choice([-shift, shift])
    dx_b = -dx_r
    if dx_r > 0:
        result[:, dx_r:, 0] = arr[:, : w - dx_r, 0]
    elif dx_r < 0:
        result[:, : w + dx_r, 0] = arr[:, -dx_r:, 0]
    if dx_b > 0:
        result[:, dx_b:, 2] = arr[:, : w - dx_b, 2]
    elif dx_b < 0:
        result[:, : w + dx_b, 2] = arr[:, -dx_b:, 2]
    return Image.fromarray(result)


def add_scratches(
    img: Image.Image, num_scratches: int | None = None
) -> Image.Image:
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
            color = random.choice(
                [(200, 200, 200), (255, 255, 255)]
                if avg < 128
                else [(40, 40, 40), (80, 80, 80)]
            )
            draw.line(points, fill=color, width=1)
    return img


def add_smudge(img: Image.Image) -> Image.Image:
    """指纹/油污：局部高斯模糊区域。"""
    w, h = img.size
    rw = random.randint(w // 4, w // 2)
    rh = random.randint(h // 4, h // 2)
    rx = random.randint(0, max(0, w - rw))
    ry = random.randint(0, max(0, h - rh))
    crop = img.crop((rx, ry, rx + rw, ry + rh))
    blur_r = random.uniform(1.5, 3.0)
    crop = crop.filter(ImageFilter.GaussianBlur(radius=blur_r))
    arr = np.array(crop, dtype=np.float32)
    arr = arr * random.uniform(0.8, 0.95) + random.uniform(5, 15)
    crop = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    img = img.copy()
    img.paste(crop, (rx, ry))
    return img


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
    """圆角段风格。"""
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
    """细线段风格。"""
    t2 = max(1, t * 2 // 3)
    off = (t - t2) // 2
    return _seg_polys_pointy(x + off, y + off, width - off * 2, height - off * 2, t2, g)


SEGMENT_STYLES = [_seg_polys_pointy, _seg_polys_rect, _seg_polys_rounded, _seg_polys_thin]


def draw_seven_segment_digit(
    draw: ImageDraw.ImageDraw,
    digit: int,
    x: int,
    y: int,
    width: int,
    height: int,
    thickness: int,
    fg_color: tuple,
    dim_color: tuple | None = None,
    gap: int = 1,
    skew: float = 0.0,
    seg_style=None,
    defect_rate: float = 0.0,
):
    """在指定位置绘制一个七段管数字。"""
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
                offset = (py - center_y) * math.tan(skew)
                poly[i] = (px + offset, py)

    for i, (on, poly) in enumerate(zip(segments, seg_polys)):
        if on and defect_rate > 0 and random.random() < defect_rate:
            if dim_color:
                draw.polygon(poly, fill=dim_color)
            continue
        color = fg_color if on else dim_color
        if color is not None:
            draw.polygon(poly, fill=color)


def _jitter_color(color: tuple, amount: int = 20) -> tuple:
    """对 RGB 颜色做微小亮度抖动。"""
    shift = random.randint(-amount, amount)
    return tuple(max(0, min(255, c + shift)) for c in color)


def render_number(
    text: str,
    digit_width: int = 40,
    digit_height: int = 70,
    thickness: int = 6,
    theme: dict | None = None,
    gap: int = 1,
    spacing: int = 8,
    padding: int = 10,
    skew: float = 0.0,
    show_dim: bool = True,
    use_textured_bg: bool = False,
) -> Image.Image:
    """将数字字符串渲染为七段管显示图片。

    支持的字符: 0-9, /, 空格, -, ., :
    """
    if theme is None:
        theme = pick_lcd_theme()

    fg = theme["fg"]
    dim = theme["dim"] if show_dim else None
    bg = theme["bg"]
    seg_style = random.choice(SEGMENT_STYLES)
    jitter = random.random() < 0.3
    defect_rate = random.uniform(0.05, 0.15) if random.random() < 0.05 else 0.0

    char_widths = []
    for ch in text:
        if ch in "0123456789":
            char_widths.append(digit_width)
        elif ch == "/":
            char_widths.append(digit_width // 2)
        elif ch == " ":
            char_widths.append(digit_width // 2)
        elif ch == "-":
            char_widths.append(digit_width // 2)
        elif ch == ".":
            char_widths.append(thickness * 2)
        elif ch == ":":
            char_widths.append(thickness * 2)
        else:
            char_widths.append(digit_width // 3)

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
            draw_seven_segment_digit(
                draw, int(ch), cx, padding, cw, digit_height,
                thickness, cur_fg, dim, gap, skew,
                seg_style=seg_style, defect_rate=defect_rate,
            )
        elif ch == "/":
            draw.line(
                [(cx + cw, padding + 2), (cx, padding + digit_height - 2)],
                fill=cur_fg, width=max(2, thickness // 2),
            )
        elif ch == "-":
            mid_y = padding + digit_height // 2
            draw.rectangle(
                [cx + 2, mid_y - thickness // 2, cx + cw - 2, mid_y + thickness // 2],
                fill=cur_fg,
            )
        elif ch == ".":
            dot_y = padding + digit_height - thickness
            draw.ellipse(
                [cx, dot_y, cx + thickness * 2, dot_y + thickness * 2],
                fill=cur_fg,
            )
        elif ch == ":":
            dot_r = max(1, thickness)
            dot1_y = padding + digit_height // 3 - dot_r
            dot2_y = padding + digit_height * 2 // 3 - dot_r
            draw.ellipse([cx, dot1_y, cx + dot_r * 2, dot1_y + dot_r * 2], fill=cur_fg)
            draw.ellipse([cx, dot2_y, cx + dot_r * 2, dot2_y + dot_r * 2], fill=cur_fg)
        cx += cw + spacing

    # 段发光效果 (15%)
    if random.random() < 0.15:
        from PIL import ImageFilter
        glow = img.filter(ImageFilter.GaussianBlur(radius=max(1, thickness // 2)))
        img = Image.blend(img, glow, alpha=0.3)

    return img


def add_noise(img: Image.Image, intensity: float = 0.05) -> Image.Image:
    """添加高斯噪声。"""
    arr = np.array(img, dtype=np.float32)
    noise = np.random.normal(0, intensity * 255, arr.shape)
    arr = np.clip(arr + noise, 0, 255).astype(np.uint8)
    return Image.fromarray(arr)


def add_salt_pepper_noise(img: Image.Image, amount: float = 0.02) -> Image.Image:
    """添加椒盐噪声（模拟 LCD 坏点/灰尘）。"""
    arr = np.array(img)
    mask = np.random.random(arr.shape[:2])
    arr[mask < amount / 2] = 0
    arr[mask > 1 - amount / 2] = 255
    return Image.fromarray(arr)


def adjust_brightness_contrast(
    img: Image.Image,
    brightness: float = 1.0,
    contrast: float = 1.0,
) -> Image.Image:
    """调整亮度和对比度。"""
    arr = np.array(img, dtype=np.float32)
    arr = (arr - 128) * contrast + 128
    arr = arr * brightness
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    return Image.fromarray(arr)


def random_rotate(img: Image.Image, max_angle: float = 5.0) -> Image.Image:
    """随机小角度旋转。"""
    angle = random.uniform(-max_angle, max_angle)
    bg_color = img.getpixel((0, 0))
    return img.rotate(angle, resample=Image.BICUBIC, fillcolor=bg_color, expand=True)


def add_reflection(img: Image.Image, intensity: float = 0.3) -> Image.Image:
    """模拟 LCD 屏幕反光（渐变白色叠加）。"""
    arr = np.array(img, dtype=np.float32)
    h, w = arr.shape[:2]

    # 随机反光区域
    pattern = random.choice(["gradient", "spot", "stripe"])

    if pattern == "gradient":
        # 对角渐变反光
        angle = random.uniform(0, math.pi)
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        yy, xx = np.mgrid[0:h, 0:w]
        gradient = (xx * cos_a + yy * sin_a) / max(w, h)
        gradient = (gradient - gradient.min()) / (gradient.max() - gradient.min() + 1e-6)
        reflection = gradient * intensity * 255

    elif pattern == "spot":
        # 圆形光斑
        cx = random.uniform(0.2, 0.8) * w
        cy = random.uniform(0.2, 0.8) * h
        radius = random.uniform(0.2, 0.5) * max(w, h)
        yy, xx = np.mgrid[0:h, 0:w]
        dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
        reflection = np.clip(1.0 - dist / radius, 0, 1) * intensity * 255

    else:  # stripe
        # 条纹反光
        freq = random.uniform(0.01, 0.05)
        offset = random.uniform(0, 2 * math.pi)
        yy = np.arange(h).reshape(-1, 1)
        reflection = np.broadcast_to(
            (np.sin(yy * freq * 2 * math.pi + offset) * 0.5 + 0.5) * intensity * 255,
            (h, w),
        ).copy()

    if len(arr.shape) == 3:
        reflection = reflection.reshape(h, w, 1)
    arr = np.clip(arr + reflection, 0, 255).astype(np.uint8)
    return Image.fromarray(arr)


def add_color_cast(img: Image.Image) -> Image.Image:
    """给图片添加随机色偏（模拟不同光源环境）。"""
    arr = np.array(img, dtype=np.float32)
    # 随机色偏量
    r_shift = random.uniform(-30, 30)
    g_shift = random.uniform(-30, 30)
    b_shift = random.uniform(-30, 30)
    arr[:, :, 0] = np.clip(arr[:, :, 0] + r_shift, 0, 255)
    arr[:, :, 1] = np.clip(arr[:, :, 1] + g_shift, 0, 255)
    arr[:, :, 2] = np.clip(arr[:, :, 2] + b_shift, 0, 255)
    return Image.fromarray(arr.astype(np.uint8))


def invert_polarity(img: Image.Image) -> Image.Image:
    """反转图片亮暗极性（帮助模型泛化不同底色/字色组合）。"""
    arr = np.array(img, dtype=np.float32)
    arr = 255.0 - arr
    return Image.fromarray(arr.astype(np.uint8))


def perspective_transform(img: Image.Image, strength: float = 0.08) -> Image.Image:
    """透视变换（模拟非正对拍摄角度）。"""
    w, h = img.size
    s = strength

    # 四个角的随机偏移
    coeffs = [
        random.uniform(-s, s) * w,  # 左上 x
        random.uniform(-s, s) * h,  # 左上 y
        random.uniform(-s, s) * w,  # 右上 x
        random.uniform(-s, s) * h,  # 右上 y
        random.uniform(-s, s) * w,  # 右下 x
        random.uniform(-s, s) * h,  # 右下 y
        random.uniform(-s, s) * w,  # 左下 x
        random.uniform(-s, s) * h,  # 左下 y
    ]

    # 源四角 → 目标四角
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    dst = [
        (coeffs[0], coeffs[1]),
        (w + coeffs[2], coeffs[3]),
        (w + coeffs[4], h + coeffs[5]),
        (coeffs[6], h + coeffs[7]),
    ]

    # 计算透视系数
    try:
        matrix = _find_coeffs(dst, src)
        bg_color = img.getpixel((0, 0))
        return img.transform(
            (w, h), Image.PERSPECTIVE, matrix,
            resample=Image.BICUBIC, fillcolor=bg_color,
        )
    except Exception:
        return img  # 退化情况直接返回原图


def _find_coeffs(source_coords, target_coords):
    """计算透视变换的 8 个系数。"""
    matrix = []
    for s, t in zip(source_coords, target_coords):
        matrix.append([t[0], t[1], 1, 0, 0, 0, -s[0] * t[0], -s[0] * t[1]])
        matrix.append([0, 0, 0, t[0], t[1], 1, -s[1] * t[0], -s[1] * t[1]])
    A = np.array(matrix, dtype=np.float64)
    B = np.array([c for pair in source_coords for c in pair], dtype=np.float64)
    res = np.linalg.solve(A, B)
    return tuple(res.flatten())


def add_jpeg_artifacts(img: Image.Image, quality: int = 30) -> Image.Image:
    """添加 JPEG 压缩伪影（模拟低质量照片）。"""
    import io
    buffer = io.BytesIO()
    img.save(buffer, format="JPEG", quality=quality)
    buffer.seek(0)
    return Image.open(buffer).convert("RGB")


def embed_with_margin(img: Image.Image, scale_factor: float) -> Image.Image:
    """将渲染图嵌入更大画布，模拟数字在大屏幕中占比小的情况。

    scale_factor: 1.0 = 无额外边距, 2.0 = 图片只占画布 50%
    """
    if scale_factor <= 1.05:
        return img
    w, h = img.size
    new_w = int(w * scale_factor)
    new_h = int(h * scale_factor)
    bg = img.getpixel((0, 0))
    canvas = Image.new("RGB", (new_w, new_h), bg)
    # 随机偏移嵌入位置
    max_x = new_w - w
    max_y = new_h - h
    ox = random.randint(int(max_x * 0.15), int(max_x * 0.85)) if max_x > 1 else 0
    oy = random.randint(int(max_y * 0.15), int(max_y * 0.85)) if max_y > 1 else 0
    canvas.paste(img, (ox, oy))
    return canvas


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


def generate_single_digit_dataset(
    output_dir: Path,
    samples_per_digit: int = 500,
) -> None:
    """生成单数字分类数据集。

    输出:
    - output_dir/images/digit_{label}_{id}.png
    - output_dir/labels.csv
    """
    img_dir = output_dir / "images"
    img_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    total = 10 * samples_per_digit
    print(f"生成单数字分类数据: {total} 张图片...")

    for digit in range(10):
        for i in range(samples_per_digit):
            # 随机参数
            theme = pick_lcd_theme()
            dw = random.randint(30, 55)
            dh = random.randint(55, 90)
            thickness = random.randint(4, max(5, dw // 5))
            gap = random.randint(0, 3)
            skew = random.uniform(-0.15, 0.15)
            show_dim = random.random() < 0.6
            padding = random.randint(5, 15)

            img = render_number(
                str(digit),
                digit_width=dw,
                digit_height=dh,
                thickness=thickness,
                theme=theme,
                gap=gap,
                padding=padding,
                skew=skew,
                show_dim=show_dim,
                use_textured_bg=random.random() < 0.4,
            )
            # 难度分布: 25% easy, 35% normal, 40% hard
            r = random.random()
            difficulty = "easy" if r < 0.25 else ("normal" if r < 0.6 else "hard")
            img = augment_image(img, difficulty)

            # 调整到统一大小
            img = img.resize((32, 64), Image.LANCZOS)

            fname = f"digit_{digit}_{i:04d}.png"
            img.save(img_dir / fname)
            rows.append({"filename": fname, "label": digit})

    # 写 CSV
    with open(output_dir / "labels.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["filename", "label"])
        writer.writeheader()
        writer.writerows(rows)

    print(f"  已保存到 {output_dir}, 共 {len(rows)} 张")


def generate_sequence_dataset(
    output_dir: Path,
    num_samples: int = 5000,
    max_digits: int = 6,
) -> None:
    """生成多数字序列数据集 (用于 CRNN + CTC 训练)。

    格式示例: "138/88", "120 80", "97.2", "72", "5.6"

    输出:
    - output_dir/images/seq_{label}_{id}.png
    - output_dir/sequences.csv
    """
    img_dir = output_dir / "images"
    img_dir.mkdir(parents=True, exist_ok=True)

    # 常见医疗数值模式
    def random_bp():
        sys = random.randint(80, 200)
        dia = random.randint(40, 130)
        sep = random.choice(["/", " "])
        return f"{sys}{sep}{dia}"

    def random_hr():
        return str(random.randint(40, 200))

    def random_temp():
        t = round(random.uniform(35.0, 42.0), 1)
        return str(t)

    def random_glucose():
        g = round(random.uniform(2.0, 30.0), 1)
        return str(g)

    def random_spo2():
        return str(random.randint(85, 100))

    def random_weight():
        w = round(random.uniform(30.0, 150.0), 1)
        return str(w)

    def random_generic():
        n = random.randint(1, max_digits)
        return "".join([str(random.randint(0, 9)) for _ in range(n)])

    generators = [
        (random_bp, 0.35, "bp"),
        (random_hr, 0.15, "hr"),
        (random_temp, 0.10, "temp"),
        (random_glucose, 0.10, "spo2"),
        (random_spo2, 0.10, "spo2"),
        (random_weight, 0.05, "weight"),
        (random_generic, 0.15, "generic"),
    ]

    rows = []
    print(f"生成序列数据: {num_samples} 张图片...")

    for i in range(num_samples):
        # 加权随机选择生成器
        r = random.random()
        cumulative = 0.0
        gen_func = random_generic
        label_cat = "generic"
        for func, weight, cat in generators:
            cumulative += weight
            if r < cumulative:
                gen_func = func
                label_cat = cat
                break

        text = gen_func()

        # 随机渲染参数
        theme = pick_lcd_theme()
        dw = random.randint(18, 55)
        dh = random.randint(35, 95)
        thickness = random.randint(2, max(3, dw // 4))
        gap = random.randint(0, 4)
        skew = random.uniform(-0.18, 0.18)
        show_dim = random.random() < 0.5
        spacing = random.choice([
            random.randint(0, 2),
            random.randint(3, 10),
            random.randint(3, 10),
            random.randint(11, 22),
            random.randint(3, 14),
        ])
        padding = random.randint(3, 22)

        img = render_number(
            text,
            digit_width=dw,
            digit_height=dh,
            thickness=thickness,
            theme=theme,
            gap=gap,
            spacing=spacing,
            padding=padding,
            skew=skew,
            show_dim=show_dim,
            use_textured_bg=random.random() < 0.4,
        )

        # 35%概率叠加医疗标签干扰文字（在 embed/augment 之前）
        if random.random() < 0.35:
            img = add_medical_label(img, category=label_cat)

        # 随机在更大画布中嵌入 (30%概率, 模拟屏幕比数字大很多)
        if random.random() < 0.30:
            scale = random.uniform(1.3, 2.5)
            img = embed_with_margin(img, scale)

        # 难度分布: 15% easy, 30% normal, 55% hard
        r = random.random()
        difficulty = "easy" if r < 0.15 else ("normal" if r < 0.45 else "hard")
        img = augment_image(img, difficulty)

        # 调整高度为 64，保持宽高比
        target_h = 64
        ratio = target_h / img.height
        target_w = max(32, int(img.width * ratio))
        img = img.resize((target_w, target_h), Image.LANCZOS)

        fname = f"seq_{i:06d}.png"
        img.save(img_dir / fname)
        rows.append({"filename": fname, "label": text})

    # 写 CSV
    with open(output_dir / "sequences.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["filename", "label"])
        writer.writeheader()
        writer.writerows(rows)

    print(f"  已保存到 {output_dir}, 共 {len(rows)} 张")


def preview(output_dir: Path) -> None:
    """生成少量预览图片并显示。"""
    preview_dir = output_dir / "preview"
    preview_dir.mkdir(parents=True, exist_ok=True)

    samples = [
        "138/88",
        "120 80",
        "97.2",
        "72",
        "5.6",
        "98",
        "65.5",
        "180/110",
        "36.5",
        "100",
    ]

    print("生成预览图片...")
    for j, theme in enumerate(LCD_THEMES):
        for i, text in enumerate(samples):
            img = render_number(
                text,
                digit_width=40,
                digit_height=70,
                thickness=6,
                theme=theme,
                gap=1,
                spacing=8,
                padding=10,
                show_dim=True,
            )
            img.save(preview_dir / f"theme{j}_{text.replace('/', '_').replace(' ', '_')}.png")

    # 增强变化样本: 三种难度各一张
    for i, text in enumerate(samples):
        for difficulty in ["easy", "normal", "hard"]:
            img = render_number(
                text,
                digit_width=random.randint(30, 50),
                digit_height=random.randint(55, 85),
                thickness=random.randint(4, 8),
                theme=pick_lcd_theme(),
                gap=random.randint(0, 3),
                spacing=random.choice([random.randint(0, 2), random.randint(3, 10), random.randint(11, 22), random.randint(4, 12)]),
                padding=random.randint(5, 15),
                skew=random.uniform(-0.15, 0.15),
                show_dim=random.random() < 0.5,
                use_textured_bg=(difficulty == "hard"),
            )
            img = augment_image(img, difficulty)
            img.save(
                preview_dir
                / f"aug_{difficulty}_{text.replace('/', '_').replace(' ', '_')}.png"
            )

    print(f"  预览图片已保存到 {preview_dir}")


def main():
    parser = argparse.ArgumentParser(description="七段数码管合成数据生成器")
    parser.add_argument(
        "--output",
        type=str,
        default="dataset",
        help="输出目录 (默认: dataset)",
    )
    parser.add_argument(
        "--preview",
        action="store_true",
        help="仅生成预览图片",
    )
    parser.add_argument(
        "--digits-per-class",
        type=int,
        default=800,
        help="单数字分类: 每个数字的样本数 (默认: 800)",
    )
    parser.add_argument(
        "--sequences",
        type=int,
        default=8000,
        help="序列数据: 总样本数 (默认: 8000)",
    )
    parser.add_argument("--seed", type=int, default=42, help="随机种子")

    args = parser.parse_args()
    random.seed(args.seed)
    np.random.seed(args.seed)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    if args.preview:
        preview(output_dir)
        return

    # 生成两种数据集
    generate_single_digit_dataset(
        output_dir / "single_digit",
        samples_per_digit=args.digits_per_class,
    )
    generate_sequence_dataset(
        output_dir / "sequence",
        num_samples=args.sequences,
    )

    print("\n✅ 数据生成完成!")
    print(f"  单数字分类: {output_dir}/single_digit/")
    print(f"  序列数据:   {output_dir}/sequence/")


if __name__ == "__main__":
    main()
