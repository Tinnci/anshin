"""Kaggle synthetic LCD rendering and augmentation pipeline."""

import io
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

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


def _bilinear_resize(arr, out_h, out_w):
    """纯 numpy 双线性插值放大 2D 数组（替代 PIL.Image.resize 避免 F-mode 开销）。"""
    in_h, in_w = arr.shape
    y_ratio = (in_h - 1) / max(1, out_h - 1)
    x_ratio = (in_w - 1) / max(1, out_w - 1)
    y_idx = np.arange(out_h, dtype=np.float32) * y_ratio
    x_idx = np.arange(out_w, dtype=np.float32) * x_ratio
    y0 = np.floor(y_idx).astype(np.int32).clip(0, in_h - 2)
    x0 = np.floor(x_idx).astype(np.int32).clip(0, in_w - 2)
    yf = y_idx - y0
    xf = x_idx - x0
    # 向量化双线性插值
    top = arr[y0][:, x0] * (1 - xf) + arr[y0][:, x0 + 1] * xf
    bot = arr[y0 + 1][:, x0] * (1 - xf) + arr[y0 + 1][:, x0 + 1] * xf
    return top * (1 - yf[:, None]) + bot * yf[:, None]


def _perlin_noise_2d(shape, scale=32.0):
    h, w = shape
    noise = np.zeros((h, w), dtype=np.float32)
    for octave in range(4):
        freq = 2 ** octave
        s = scale / freq
        gh = max(2, int(h / s) + 2)
        gw = max(2, int(w / s) + 2)
        grid = np.random.randn(gh, gw).astype(np.float32)
        grid_up = _bilinear_resize(grid, h, w)
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
        # 向量化织物网格纹理（替代逐像素循环）
        yy, xx = np.mgrid[0:h, 0:w]
        mask = (yy % grid_size < grid_size // 2) ^ (xx % grid_size < grid_size // 2)
        arr[mask] += intensity
        arr[~mask] -= intensity
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


def render_multiline_bp(theme=None):
    """渲染多行血压计 LCD 图像（高压/低压/脉率分行显示）。

    模拟真实血压计屏幕：
    - 第 1 行（大号）: 高压 SYS  e.g. 129
    - 第 2 行（中号）: 低压 DIA  e.g. 80
    - 第 3 行（小号）: 脉率 PUL  e.g. 55  (有时省略)

    返回: (img, label_text)
    label_text 用 '\\n' 分隔行, 如 "129\\n80\\n55"
    """
    if theme is None:
        theme = pick_lcd_theme()
    fg, dim, bg = theme["fg"], theme["dim"], theme["bg"]
    seg_style = random.choice(SEGMENT_STYLES)
    show_dim = random.random() < 0.5
    dim_c = dim if show_dim else None

    sys_val = str(random.randint(80, 200))
    dia_val = str(random.randint(40, 130))
    has_pulse = random.random() < 0.7
    pul_val = str(random.randint(40, 120)) if has_pulse else None

    # 各行字体大小（模拟真实血压计大号高压/中号低压/小号脉率）
    sys_h = random.randint(50, 95)   # 大号
    dia_h = random.randint(35, 70)   # 中号
    pul_h = random.randint(25, 50) if has_pulse else 0  # 小号

    sys_w = random.randint(max(12, sys_h // 3), max(14, sys_h * 2 // 3))
    dia_w = random.randint(max(10, dia_h // 3), max(12, dia_h * 2 // 3))
    pul_w = random.randint(max(8, pul_h // 3), max(10, pul_h * 2 // 3)) if has_pulse else 0
    sys_t = random.randint(2, max(3, sys_w // 4))
    dia_t = random.randint(2, max(3, dia_w // 4))
    pul_t = random.randint(2, max(3, pul_w // 4)) if has_pulse else 0

    gap = random.randint(0, 3)
    skew = random.uniform(-0.12, 0.12)

    # 计算每行宽度
    sp_sys = random.randint(2, 10)
    sp_dia = random.randint(2, 8)
    sp_pul = random.randint(1, 6) if has_pulse else 0

    def _calc_row_w(text, dw, sp, pad):
        cws = []
        for ch in text:
            if ch in "0123456789":
                cws.append(dw)
            else:
                cws.append(max(4, dw // 3))
        return sum(cws) + sp * max(0, len(text) - 1) + pad * 2

    pad = random.randint(4, 16)
    row_gap = random.randint(4, 20)

    w_sys = _calc_row_w(sys_val, sys_w, sp_sys, pad)
    w_dia = _calc_row_w(dia_val, dia_w, sp_dia, pad)
    w_pul = _calc_row_w(pul_val, pul_w, sp_pul, pad) if has_pulse else 0

    total_w = max(w_sys, w_dia, w_pul) + pad * 2
    total_h = pad + sys_h + row_gap + dia_h
    if has_pulse:
        total_h += row_gap + pul_h
    total_h += pad

    use_tex = random.random() < 0.4
    if use_tex:
        img = generate_textured_background(total_w, total_h, bg)
    else:
        img = Image.new("RGB", (total_w, total_h), bg)
    draw = ImageDraw.Draw(img)

    jitter = random.random() < 0.3
    defect_rate = random.uniform(0.05, 0.15) if random.random() < 0.05 else 0.0

    def _draw_row(text, y_off, d_w, d_h, d_t, spacing):
        """Draw a row of seven-segment digits."""
        # 行内水平偏移（左对齐/居中/右对齐随机）
        row_w_actual = _calc_row_w(text, d_w, spacing, 0)
        align = random.choice(["left", "center", "right"])
        if align == "left":
            x_off = pad + random.randint(0, max(0, (total_w - 2 * pad - row_w_actual) // 3))
        elif align == "center":
            x_off = (total_w - row_w_actual) // 2 + random.randint(-5, 5)
        else:
            x_off = total_w - pad - row_w_actual - random.randint(0, max(0, (total_w - 2 * pad - row_w_actual) // 3))
        x_off = max(pad, min(x_off, total_w - row_w_actual - pad))
        cx = x_off
        for ch in text:
            if ch in "0123456789":
                cur_fg = _jitter_color(fg, 15) if jitter else fg
                draw_seven_segment_digit(draw, int(ch), cx, y_off, d_w, d_h, d_t, cur_fg, dim_c, gap, skew, seg_style=seg_style, defect_rate=defect_rate)
                cx += d_w + spacing
            else:
                cx += max(4, d_w // 3) + spacing

    y = pad
    _draw_row(sys_val, y, sys_w, sys_h, sys_t, sp_sys)
    y += sys_h + row_gap
    _draw_row(dia_val, y, dia_w, dia_h, dia_t, sp_dia)
    if has_pulse:
        y += dia_h + row_gap
        _draw_row(pul_val, y, pul_w, pul_h, pul_t, sp_pul)

    # 随机添加分隔线（某些血压计在行间画横线）
    if random.random() < 0.3:
        line_y = pad + sys_h + row_gap // 2
        line_color = tuple(max(0, min(255, c + random.randint(-20, 20))) for c in bg)
        draw.line([(pad, line_y), (total_w - pad, line_y)], fill=line_color, width=1)

    # 段发光
    if random.random() < 0.15:
        glow = img.filter(ImageFilter.GaussianBlur(radius=max(1, sys_t // 2)))
        img = Image.blend(img, glow, alpha=0.3)

    if has_pulse:
        label = f"{sys_val}\n{dia_val}\n{pul_val}"
    else:
        label = f"{sys_val}\n{dia_val}"

    return img, label


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
        yy, xx = np.mgrid[0:h, 0:w]
        d = (xx / w + yy / h) / 2
        inside = d < shadow_width
        mask[inside] = 1.0 - shadow_strength * (1.0 - d[inside] / shadow_width)
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


def augment_geometric(img, difficulty="normal"):
    """几何增强：变换空间结构，计算较重。在 pool 生成阶段调用。"""
    if difficulty == "easy":
        if random.random() < 0.1:
            img = add_ghosting(img, 0.08)
        return img

    if difficulty == "hard":
        if random.random() < 0.35:
            img = add_background_clutter(img)
        if random.random() < 0.25:
            img = add_edge_frame(img)
        if random.random() < 0.6:
            ps = random.uniform(0.06, 0.20)
            img = perspective_transform(img, ps)
        if random.random() < 0.20:
            img = add_ghosting(img)
        if random.random() < 0.20:
            img = add_motion_blur(img)
        if random.random() < 0.15:
            img = add_barrel_distortion(img)
        if random.random() < 0.25:
            img = add_cast_shadow(img)
        if random.random() < 0.12:
            img = add_scratches(img)
        if random.random() < 0.12:
            img = add_smudge(img)
        if random.random() < 0.2:
            img = partial_occlusion(img, max_rects=2)
        if random.random() < 0.7:
            img = random_rotate(img, max_angle=15.0)  # v10: 增大旋转到 15°
        return img

    # normal
    if random.random() < 0.55:
        img = random_rotate(img, max_angle=8.0)  # v10: 从 5° 增到 8°
    if random.random() < 0.30:
        img = perspective_transform(img, random.uniform(0.03, 0.12))
    if random.random() < 0.12:
        img = add_background_clutter(img)
    if random.random() < 0.10:
        img = add_ghosting(img, 0.10)
    if random.random() < 0.12:
        img = add_barrel_distortion(img, random.uniform(0.03, 0.12))
    if random.random() < 0.12:
        img = add_cast_shadow(img)
    return img


def augment_photometric(img, difficulty="normal"):
    """光度增强：调整颜色/亮度/噪声，计算较轻。在 __getitem__ 中在线调用。"""
    if difficulty == "easy":
        img = adjust_brightness_contrast(img, random.uniform(0.85, 1.15), random.uniform(0.9, 1.1))
        if random.random() < 0.3:
            img = add_noise(img, random.uniform(0.01, 0.03))
        return img

    if difficulty == "hard":
        if random.random() < 0.4:
            img = adjust_brightness_contrast(img, random.uniform(0.6, 0.9), random.uniform(0.4, 0.6))
        if random.random() < 0.5:
            img = add_reflection(img, random.uniform(0.15, 0.5))
        if random.random() < 0.4:
            img = add_color_cast(img)
        if random.random() < 0.12:
            img = add_chromatic_aberration(img)
        if random.random() < 0.5:
            img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.8, 2.5)))
        if random.random() < 0.5:
            img = add_noise(img, random.uniform(0.05, 0.15))
        if random.random() < 0.3:
            img = add_salt_pepper_noise(img, random.uniform(0.01, 0.05))
        if random.random() < 0.3:
            img = add_jpeg_artifacts(img, random.randint(15, 40))
        if random.random() < 0.08:
            img = invert_polarity(img)
        return img

    # normal
    img = adjust_brightness_contrast(img, random.uniform(0.7, 1.3), random.uniform(0.8, 1.3))
    if random.random() < 0.7:
        img = add_noise(img, random.uniform(0.02, 0.10))
    if random.random() < 0.4:
        img = img.filter(ImageFilter.GaussianBlur(random.uniform(0.3, 1.5)))
    if random.random() < 0.2:
        img = add_reflection(img, random.uniform(0.1, 0.25))
    if random.random() < 0.15:
        img = add_color_cast(img)
    if random.random() < 0.05:
        img = add_chromatic_aberration(img, 1)
    return img


def augment_image(img, difficulty="normal"):
    """完整增强（几何+光度），用于验证集预生成等一次性场景。"""
    img = augment_geometric(img, difficulty)
    img = augment_photometric(img, difficulty)
    return img


