import tempfile
import unittest
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper

from dequantize_onnx import convert_integer_ops_to_float


def _make_dynamic_conv_integer_model(path: Path) -> None:
    weight_q = np.array([[[[1, -2], [3, 4]]]], dtype=np.int8)
    weight_scale = np.array(0.25, dtype=np.float32)
    weight_zp = np.array(0, dtype=np.int8)
    nodes = [
        helper.make_node(
            "DynamicQuantizeLinear",
            ["input"],
            ["input_quantized", "input_scale", "input_zero_point"],
            name="input_dql",
        ),
        helper.make_node(
            "Mul",
            ["input_scale", "weight_scale"],
            ["conv_scale_product"],
            name="conv_scales_mul",
        ),
        helper.make_node(
            "ConvInteger",
            ["input_quantized", "weight_quantized", "input_zero_point", "weight_zero_point"],
            ["conv_integer_output"],
            name="conv_integer",
        ),
        helper.make_node(
            "Cast",
            ["conv_integer_output"],
            ["conv_integer_float"],
            to=TensorProto.FLOAT,
            name="conv_integer_cast",
        ),
        helper.make_node(
            "Mul",
            ["conv_integer_float", "conv_scale_product"],
            ["output"],
            name="conv_output_scale_mul",
        ),
    ]
    graph = helper.make_graph(
        nodes,
        "dynamic_conv_integer",
        [helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 1, 3, 3])],
        [helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 1, 2, 2])],
        initializer=[
            numpy_helper.from_array(weight_q, "weight_quantized"),
            numpy_helper.from_array(weight_scale, "weight_scale"),
            numpy_helper.from_array(weight_zp, "weight_zero_point"),
        ],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 17)])
    model.ir_version = 8
    onnx.save(model, path)


class DequantizeOnnxTest(unittest.TestCase):
    def test_rewrites_conv_integer_pattern_to_executable_float_graph(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "src.onnx"
            dst = Path(tmp) / "dst.onnx"
            _make_dynamic_conv_integer_model(src)

            report = convert_integer_ops_to_float(src, dst)
            converted = onnx.load(dst, load_external_data=False)

            op_types = [node.op_type for node in converted.graph.node]
            self.assertEqual(report["converted_conv_integer"], 1)
            self.assertNotIn("ConvInteger", op_types)
            self.assertIn("DequantizeLinear", op_types)
            self.assertIn("Conv", op_types)

            session = ort.InferenceSession(str(dst), providers=["CPUExecutionProvider"])
            x = np.arange(9, dtype=np.float32).reshape(1, 1, 3, 3)
            output = session.run(None, {"input": x})[0]

        self.assertEqual(output.shape, (1, 1, 2, 2))
        self.assertTrue(np.isfinite(output).all())


if __name__ == "__main__":
    unittest.main()
