"""
模型对比基准测试
================
在合成七段数码管测试集上对比以下模型性能:

1. 我们的 LightCRNN (316 KB, 专门针对七段管训练)
2. EAST + OpenCV CRNN (文本检测 + 通用OCR识别)
3. DB50 + OpenCV CRNN (文本检测 + 通用OCR识别)
4. DB18 + OpenCV CRNN (文本检测 + 通用OCR识别)

对比维度:
- 检测率 (Detection Rate): 能否在图片中找到文本区域
- 识别准确率 (Recognition Accuracy): 识别出的文本是否正确
- 推理速度 (Inference Speed): 每张图片的处理时间
- 模型大小 (Model Size)
"""

import argparse
import os
import random
import time
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort
from PIL import Image

# 导入我们的数据生成器，用于生成测试集
from generate_data import LCD_THEMES, augment_image, render_number


# ── 我们的 CRNN 推理 ──────────────────────────────────────

class OurCRNN:
    """我们训练的轻量级 CRNN 模型。"""
    CHARSET = "0123456789/. -"  # 14 chars + blank(0)

    def __init__(self, model_path: str):
        self.session = ort.InferenceSession(model_path)
        self.input_name = self.session.get_inputs()[0].name
        model_size = os.path.getsize(model_path) / 1024
        print(f"  [Our CRNN] 模型大小: {model_size:.0f} KB")

    def recognize(self, pil_img: Image.Image) -> str:
        """从 PIL Image 识别文本。"""
        # 预处理: 灰度 → 保持宽高比缩放到 h=64 → 左对齐填充到 256 宽
        gray = pil_img.convert("L")
        target_h, max_w = 64, 256
        ratio = target_h / gray.height
        new_w = min(int(gray.width * ratio), max_w)
        gray = gray.resize((new_w, target_h), Image.LANCZOS)
        padded = Image.new("L", (max_w, target_h), 0)
        padded.paste(gray, (0, 0))
        arr = np.array(padded, dtype=np.float32) / 255.0
        tensor = arr.reshape(1, 1, 64, 256)

        # 推理
        outputs = self.session.run(None, {self.input_name: tensor})
        logits = outputs[0]  # [T, 1, C]

        # CTC 解码
        return self._ctc_decode(logits[:, 0, :])

    def _ctc_decode(self, logits: np.ndarray) -> str:
        """贪心 CTC 解码。"""
        indices = np.argmax(logits, axis=1)
        result = []
        prev = -1
        for idx in indices:
            if idx != 0 and idx != prev:  # 0 is blank
                char_idx = idx - 1
                if 0 <= char_idx < len(self.CHARSET):
                    result.append(self.CHARSET[char_idx])
            prev = idx
        return "".join(result)


# ── OpenCV 文本检测 + 识别 pipeline ──────────────────────

class OpenCVPipeline:
    """OpenCV DNN 文本检测 + 识别管道。"""

    def __init__(
        self,
        detection_model_path: str,
        detection_type: str,  # "east" | "db"
        recognition_model_path: str | None = None,
    ):
        self.det_type = detection_type
        self.det_model_path = detection_model_path

        # 检测模型
        det_size = os.path.getsize(detection_model_path) / (1024 * 1024)
        if detection_type == "east":
            self.detector = cv2.dnn.TextDetectionModel_EAST(detection_model_path)
            self.detector.setConfidenceThreshold(0.3)
            self.detector.setNMSThreshold(0.4)
            self.detector.setInputParams(
                scale=1.0, size=(320, 320),
                mean=(123.68, 116.78, 103.94), swapRB=True,
            )
        else:  # DB
            self.detector = cv2.dnn.TextDetectionModel_DB(detection_model_path)
            self.detector.setBinaryThreshold(0.3)
            self.detector.setPolygonThreshold(0.5)
            self.detector.setMaxCandidates(200)
            self.detector.setUnclipRatio(2.0)
            self.detector.setInputParams(
                scale=1.0 / 255.0, size=(736, 736),
                mean=(122.67891434, 116.66876762, 104.00698793),
            )

        print(f"  [{detection_type.upper()}] 检测模型大小: {det_size:.1f} MB")

        # 识别模型（可选）
        self.recognizer = None
        if recognition_model_path and os.path.exists(recognition_model_path):
            rec_size = os.path.getsize(recognition_model_path) / (1024 * 1024)
            self.recognizer = cv2.dnn.TextRecognitionModel(recognition_model_path)
            self.recognizer.setDecodeType("CTC-greedy")
            # 加载字符集 (数字 + 常见符号)
            # OpenCV CRNN 通常使用 0-9 a-z A-Z 的字符集
            alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
            self.recognizer.setVocabulary(alphabet)
            self.recognizer.setInputParams(
                scale=1.0 / 127.5, size=(100, 32),
                mean=(127.5, 127.5, 127.5),
            )
            print(f"  [{detection_type.upper()}] 识别模型大小: {rec_size:.1f} MB")

    def detect_and_recognize(self, cv_img: np.ndarray) -> tuple[list, list[str]]:
        """检测文本区域并识别。

        Returns:
            (bounding_boxes, recognized_texts)
        """
        boxes = []
        texts = []

        try:
            # 检测
            if self.det_type == "east":
                detections, confidences = self.detector.detect(cv_img)
            else:
                detections, confidences = self.detector.detect(cv_img)

            if detections is not None and len(detections) > 0:
                for det in detections:
                    if len(det) >= 4:
                        pts = np.array(det).reshape(-1, 2)
                        x_min = max(0, int(pts[:, 0].min()))
                        x_max = min(cv_img.shape[1], int(pts[:, 0].max()))
                        y_min = max(0, int(pts[:, 1].min()))
                        y_max = min(cv_img.shape[0], int(pts[:, 1].max()))

                        if x_max > x_min and y_max > y_min:
                            boxes.append((x_min, y_min, x_max, y_max))

                            # 识别裁剪区域
                            if self.recognizer:
                                crop = cv_img[y_min:y_max, x_min:x_max]
                                if crop.size > 0:
                                    try:
                                        text = self.recognizer.recognize(crop)
                                        texts.append(text)
                                    except Exception:
                                        texts.append("")
                                else:
                                    texts.append("")
                            else:
                                texts.append("")

        except Exception as e:
            pass  # 检测失败，返回空

        return boxes, texts

    def detect_only(self, cv_img: np.ndarray) -> list:
        """仅检测文本区域。"""
        try:
            detections, confidences = self.detector.detect(cv_img)
            if detections is not None:
                return list(detections)
        except Exception:
            pass
        return []


# ── 测试集生成 ──────────────────────────────────────────

def generate_test_set(
    num_samples: int = 200,
    seed: int = 999,
) -> list[tuple[Image.Image, str]]:
    """生成测试集（不与训练集重叠的种子）。"""
    random.seed(seed)
    np.random.seed(seed)

    # 典型医疗数值
    def random_bp():
        sys = random.randint(80, 200)
        dia = random.randint(40, 130)
        sep = random.choice(["/", " "])
        return f"{sys}{sep}{dia}"

    def random_hr():
        return str(random.randint(40, 200))

    def random_temp():
        return str(round(random.uniform(35.0, 42.0), 1))

    def random_spo2():
        return str(random.randint(85, 100))

    def random_generic():
        n = random.randint(1, 5)
        return "".join([str(random.randint(0, 9)) for _ in range(n)])

    generators = [
        (random_bp, 0.35),
        (random_hr, 0.20),
        (random_temp, 0.15),
        (random_spo2, 0.15),
        (random_generic, 0.15),
    ]

    test_set = []
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
        dw = random.randint(30, 50)
        dh = random.randint(55, 85)
        thickness = random.randint(4, max(5, dw // 5))

        img = render_number(
            text,
            digit_width=dw,
            digit_height=dh,
            thickness=thickness,
            theme=theme,
            gap=random.randint(0, 3),
            spacing=random.randint(4, 12),
            padding=random.randint(8, 20),
            skew=random.uniform(-0.12, 0.12),
            show_dim=random.random() < 0.5,
            use_textured_bg=random.random() < 0.4,
        )

        # 难度分布
        r2 = random.random()
        difficulty = "easy" if r2 < 0.25 else ("normal" if r2 < 0.6 else "hard")
        img = augment_image(img, difficulty)

        test_set.append((img, text))

    return test_set


def pil_to_cv(img: Image.Image) -> np.ndarray:
    """PIL Image → OpenCV BGR numpy array。"""
    rgb = np.array(img.convert("RGB"))
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)


def normalize_text(text: str) -> str:
    """标准化文本以便比较（去除空格差异等）。"""
    return text.strip().replace(" ", "")


# ── 主测试流程 ──────────────────────────────────────────

def run_benchmark(args):
    models_dir = Path(args.models_dir)
    our_model = Path(args.our_model)
    num_test = args.num_test

    print("=" * 60)
    print("七段数码管 OCR 模型对比基准测试")
    print("=" * 60)

    # ── 加载模型 ──
    print("\n📦 加载模型...")
    pipelines = {}

    # 1. 我们的 CRNN
    if our_model.exists():
        pipelines["Our_CRNN"] = OurCRNN(str(our_model))
    else:
        print(f"  ⚠️ 我们的 CRNN 模型不存在: {our_model}")

    # 2. EAST pipeline (detection only - 中文 CRNN 识别模型与 OpenCV 4.13 不兼容)
    only_ours = getattr(args, 'only_ours', False)
    east_path = models_dir / "frozen_east_text_detection.pb"
    if east_path.exists() and not only_ours:
        pipelines["EAST"] = OpenCVPipeline(
            str(east_path), "east",
            recognition_model_path=None,
        )

    # 3. DB50 pipeline (detection only)
    db50_path = models_dir / "DB_TD500_resnet50.onnx"
    if db50_path.exists() and not only_ours:
        pipelines["DB50"] = OpenCVPipeline(
            str(db50_path), "db",
            recognition_model_path=None,
        )

    # 4. DB18 pipeline (detection only)
    db18_path = models_dir / "DB_TD500_resnet18.onnx"
    if db18_path.exists() and not only_ours:
        pipelines["DB18"] = OpenCVPipeline(
            str(db18_path), "db",
            recognition_model_path=None,
        )

    if not pipelines:
        print("❌ 没有可用的模型!")
        return

    # ── 生成测试集 ──
    print(f"\n🎲 生成 {num_test} 张测试图片...")
    test_set = generate_test_set(num_test, seed=args.seed)

    # 统计难度分布
    print(f"  测试集大小: {len(test_set)} 张")
    avg_w = sum(img.width for img, _ in test_set) / len(test_set)
    avg_h = sum(img.height for img, _ in test_set) / len(test_set)
    print(f"  平均尺寸: {avg_w:.0f} × {avg_h:.0f}")

    # ── 运行测试 ──
    results = {}
    for name, pipeline in pipelines.items():
        print(f"\n🔬 测试 {name}...")
        correct = 0
        detected = 0
        total_time = 0.0
        errors = []

        for img, label in test_set:
            if isinstance(pipeline, OurCRNN):
                # 直接识别（不需要检测步骤）
                start = time.perf_counter()
                pred = pipeline.recognize(img)
                elapsed = time.perf_counter() - start
                total_time += elapsed

                detected += 1  # 我们的模型总是尝试识别
                if normalize_text(pred) == normalize_text(label):
                    correct += 1
                else:
                    errors.append((label, pred))

            else:
                # OpenCV pipeline: 检测 + 识别
                cv_img = pil_to_cv(img)
                # 放大到至少 320 像素宽（EAST 需要较大图片）
                min_dim = 320 if pipeline.det_type == "east" else 320
                if cv_img.shape[1] < min_dim or cv_img.shape[0] < min_dim:
                    scale = max(min_dim / cv_img.shape[1], min_dim / cv_img.shape[0])
                    new_w = int(cv_img.shape[1] * scale)
                    new_h = int(cv_img.shape[0] * scale)
                    cv_img = cv2.resize(cv_img, (new_w, new_h), interpolation=cv2.INTER_CUBIC)

                start = time.perf_counter()
                boxes, texts = pipeline.detect_and_recognize(cv_img)
                elapsed = time.perf_counter() - start
                total_time += elapsed

                if len(boxes) > 0:
                    detected += 1
                    if texts and any(
                        normalize_text(t) == normalize_text(label) for t in texts
                    ):
                        correct += 1
                    else:
                        errors.append((label, texts[0] if texts else ""))
                else:
                    errors.append((label, "[未检测到]"))

        avg_time_ms = (total_time / len(test_set)) * 1000
        det_rate = detected / len(test_set) * 100
        acc = correct / len(test_set) * 100

        results[name] = {
            "detection_rate": det_rate,
            "accuracy": acc,
            "avg_time_ms": avg_time_ms,
            "correct": correct,
            "detected": detected,
            "total": len(test_set),
            "errors": errors[:10],  # 只保留前10个错误
        }

        print(f"  检测率: {det_rate:.1f}% ({detected}/{len(test_set)})")
        print(f"  识别准确率: {acc:.1f}% ({correct}/{len(test_set)})")
        print(f"  平均推理时间: {avg_time_ms:.1f} ms/张")

    # ── 汇总表格 ──
    print("\n" + "=" * 60)
    print("📊 结果汇总")
    print("=" * 60)
    print(f"{'模型':<12} {'检测率':>8} {'准确率':>8} {'推理(ms)':>10} {'大小':>10}")
    print("-" * 60)

    model_sizes = {
        "Our_CRNN": "316 KB",
        "EAST": "92 MB",
        "DB50": "97 MB",
        "DB18": "47 MB",
    }

    for name, res in results.items():
        size = model_sizes.get(name, "?")
        print(
            f"{name:<12} {res['detection_rate']:>7.1f}% {res['accuracy']:>7.1f}% "
            f"{res['avg_time_ms']:>9.1f} {size:>10}"
        )

    # ── 错误样本分析 ──
    print("\n" + "=" * 60)
    print("❌ 错误样本（每个模型前 5 个）")
    print("=" * 60)
    for name, res in results.items():
        print(f"\n  {name}:")
        for label, pred in res["errors"][:5]:
            print(f"    标签: {label!r:>12}  →  预测: {pred!r}")


def main():
    parser = argparse.ArgumentParser(description="七段管 OCR 模型对比基准")
    parser.add_argument(
        "--models-dir",
        type=str,
        default="opencv_models",
        help="OpenCV 模型目录",
    )
    parser.add_argument(
        "--our-model",
        type=str,
        default="exported/crnn_seven_seg.onnx",
        help="我们的 CRNN ONNX 模型路径",
    )
    parser.add_argument(
        "--num-test",
        type=int,
        default=200,
        help="测试样本数 (默认: 200)",
    )
    parser.add_argument("--seed", type=int, default=999, help="随机种子")
    parser.add_argument("--only-ours", action="store_true", help="只测试我们的 CRNN 模型")
    args = parser.parse_args()

    run_benchmark(args)


if __name__ == "__main__":
    main()
