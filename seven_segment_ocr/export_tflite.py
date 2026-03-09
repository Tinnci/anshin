"""
PyTorch → ONNX → TFLite 导出脚本
=================================
将训练好的 PyTorch 模型导出为 TFLite 格式，用于 Android 部署。

导出流程: PyTorch → ONNX → TFLite (via ai_edge_torch 或 onnx2tf)

也支持直接导出 ONNX 后用 onnxruntime-mobile 在 Android 上运行。
"""

import argparse
from pathlib import Path

import numpy as np
import torch

from train import DigitClassifier, LightCRNN, NUM_CLASSES


def export_onnx(
    model: torch.nn.Module,
    dummy_input: torch.Tensor,
    output_path: str,
    input_names: list[str],
    output_names: list[str],
    dynamic_axes: dict | None = None,
):
    """导出 PyTorch 模型为 ONNX 格式。"""
    model.eval()
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=input_names,
        output_names=output_names,
        dynamic_axes=dynamic_axes,
        opset_version=17,
        dynamo=False,  # 使用 legacy exporter，对 LSTM 兼容性更好
    )
    print(f"  ONNX 模型已保存: {output_path}")
    size_kb = Path(output_path).stat().st_size / 1024
    print(f"  大小: {size_kb:.0f} KB")


def export_digit_classifier(model_path: str, output_dir: str):
    """导出单数字分类器。"""
    print("导出单数字分类器...")

    model = DigitClassifier()
    model.load_state_dict(torch.load(model_path, map_location="cpu", weights_only=True))
    model.eval()

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)

    # 导出 ONNX
    dummy = torch.randn(1, 1, 64, 32)
    export_onnx(
        model,
        dummy,
        str(out / "digit_classifier.onnx"),
        input_names=["image"],
        output_names=["logits"],
    )

    # 尝试导出 TFLite (需要额外依赖)
    try:
        _export_tflite_via_onnx(
            str(out / "digit_classifier.onnx"),
            str(out / "digit_classifier.tflite"),
            dummy.numpy(),
        )
    except ImportError as e:
        print(f"  ⚠ TFLite 导出跳过 (缺少依赖: {e})")
        print("  可以手动用 onnx2tf 转换: onnx2tf -i digit_classifier.onnx")


def export_crnn(model_path: str, output_dir: str, max_w: int = 256):
    """导出 CRNN 序列模型。"""
    print("导出 CRNN 序列模型...")

    model = LightCRNN()
    model.load_state_dict(torch.load(model_path, map_location="cpu", weights_only=True))
    model.eval()

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)

    # 导出 ONNX
    dummy = torch.randn(1, 1, 64, max_w)
    export_onnx(
        model,
        dummy,
        str(out / "crnn_seven_seg.onnx"),
        input_names=["image"],
        output_names=["logits"],
        dynamic_axes={"image": {3: "width"}, "logits": {0: "time_steps"}},
    )

    # 尝试导出 TFLite
    try:
        _export_tflite_via_onnx(
            str(out / "crnn_seven_seg.onnx"),
            str(out / "crnn_seven_seg.tflite"),
            dummy.numpy(),
        )
    except ImportError as e:
        print(f"  ⚠ TFLite 导出跳过 (缺少依赖: {e})")
        print("  可以手动用 onnx2tf 转换: onnx2tf -i crnn_seven_seg.onnx")


def _export_tflite_via_onnx(onnx_path: str, tflite_path: str, sample_input: np.ndarray):
    """通过 onnx2tf 转换为 TFLite。"""
    try:
        import onnx
        from onnx_tf.backend import prepare

        onnx_model = onnx.load(onnx_path)
        tf_rep = prepare(onnx_model)

        # 保存为 SavedModel
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            tf_rep.export_graph(tmpdir)

            # 转换为 TFLite
            import tensorflow as tf

            converter = tf.lite.TFLiteConverter.from_saved_model(tmpdir)
            converter.optimizations = [tf.lite.Optimize.DEFAULT]  # INT8 量化
            converter.target_spec.supported_types = [tf.float16]

            tflite_model = converter.convert()
            with open(tflite_path, "wb") as f:
                f.write(tflite_model)

            size_kb = len(tflite_model) / 1024
            print(f"  TFLite 模型已保存: {tflite_path}")
            print(f"  大小: {size_kb:.0f} KB")

    except ImportError:
        # 如果没有 tensorflow/onnx-tf，尝试用 onnxruntime
        print("  尝试使用 onnxruntime 验证 ONNX 模型...")
        try:
            import onnxruntime as ort

            sess = ort.InferenceSession(onnx_path)
            result = sess.run(None, {"image": sample_input})
            print(f"  ONNX 推理验证成功, 输出形状: {result[0].shape}")
            print(f"  可以在 Android 上使用 ONNX Runtime Mobile 直接加载 .onnx 文件")
        except ImportError:
            raise ImportError(
                "需要安装 onnx-tf + tensorflow 或 onnxruntime 来验证/转换模型"
            )


def main():
    parser = argparse.ArgumentParser(description="导出模型为 ONNX/TFLite")
    parser.add_argument(
        "--mode",
        choices=["crnn", "classifier", "both"],
        default="both",
    )
    parser.add_argument("--models-dir", type=str, default="models")
    parser.add_argument("--output-dir", type=str, default="exported")

    args = parser.parse_args()

    if args.mode in ("classifier", "both"):
        model_path = f"{args.models_dir}/digit_classifier_best.pth"
        if Path(model_path).exists():
            export_digit_classifier(model_path, args.output_dir)
        else:
            print(f"⚠ 未找到分类器模型: {model_path}")

    if args.mode in ("crnn", "both"):
        model_path = f"{args.models_dir}/crnn_best.pth"
        if Path(model_path).exists():
            export_crnn(model_path, args.output_dir)
        else:
            print(f"⚠ 未找到 CRNN 模型: {model_path}")


if __name__ == "__main__":
    main()
