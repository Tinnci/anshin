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
import random
from pathlib import Path

import numpy as np
from PIL import Image

from lcd_rendering import LCD_THEMES, embed_with_margin, pick_lcd_theme, render_number
from lcd_realism import add_medical_label, augment_image


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
    real_world_ratio: float = 0.25,
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

        # 难度分布: 默认额外加入 25% real_world 域随机化，覆盖真实 LCD 摄影失真。
        r = random.random()
        if r < real_world_ratio:
            difficulty = "real_world"
        else:
            rr = random.random()
            difficulty = "easy" if rr < 0.15 else ("normal" if rr < 0.45 else "hard")
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
    parser.add_argument(
        "--real-world-ratio",
        type=float,
        default=0.25,
        help="序列数据中使用 real_world 增强的比例 (默认: 0.25)",
    )

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
        real_world_ratio=args.real_world_ratio,
    )

    print("\n✅ 数据生成完成!")
    print(f"  单数字分类: {output_dir}/single_digit/")
    print(f"  序列数据:   {output_dir}/sequence/")


if __name__ == "__main__":
    main()
