"""Rewrite ORT integer quantization ops into executable floating-point ONNX ops.

This converter targets the dynamic quantization pattern emitted by ONNX Runtime
for this project:

    DynamicQuantizeLinear -> ConvInteger/MatMulInteger -> Cast -> Mul(scale)

The rewritten graph keeps the quantized activation semantics by inserting
DequantizeLinear on the quantized activation, dequantizes constant weights into
FP32 initializers, and replaces the integer op plus final scale multiplication
with a standard Conv or MatMul.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import onnx
from onnx import helper, numpy_helper


def _node_attrs(node: onnx.NodeProto) -> dict[str, Any]:
    return {attr.name: helper.get_attribute_value(attr) for attr in node.attribute}


def _as_float_weight(
    quantized_weight: np.ndarray,
    scale: np.ndarray,
    zero_point: np.ndarray | None,
) -> np.ndarray:
    weight = quantized_weight.astype(np.float32)
    zp = 0.0 if zero_point is None else zero_point.astype(np.float32)
    scale = scale.astype(np.float32)

    if scale.ndim == 1 and quantized_weight.ndim >= 2:
        if scale.shape[0] == quantized_weight.shape[0]:
            shape = (scale.shape[0],) + (1,) * (quantized_weight.ndim - 1)
            scale = scale.reshape(shape)
            if isinstance(zp, np.ndarray) and zp.ndim == 1:
                zp = zp.reshape(shape)
        elif scale.shape[0] == quantized_weight.shape[-1]:
            shape = (1,) * (quantized_weight.ndim - 1) + (scale.shape[0],)
            scale = scale.reshape(shape)
            if isinstance(zp, np.ndarray) and zp.ndim == 1:
                zp = zp.reshape(shape)
    return (weight - zp) * scale


def _producer_map(nodes: list[onnx.NodeProto]) -> dict[str, onnx.NodeProto]:
    producers: dict[str, onnx.NodeProto] = {}
    for node in nodes:
        for output in node.output:
            producers[output] = node
    return producers


def _consumer_map(nodes: list[onnx.NodeProto]) -> dict[str, list[onnx.NodeProto]]:
    consumers: dict[str, list[onnx.NodeProto]] = {}
    for node in nodes:
        for input_name in node.input:
            consumers.setdefault(input_name, []).append(node)
    return consumers


def _single_consumer(
    consumers: dict[str, list[onnx.NodeProto]],
    value_name: str,
    op_type: str,
) -> onnx.NodeProto | None:
    matches = [node for node in consumers.get(value_name, []) if node.op_type == op_type]
    return matches[0] if len(matches) == 1 else None


def _find_scale_mul_for_integer_output(
    producers: dict[str, onnx.NodeProto],
    consumers: dict[str, list[onnx.NodeProto]],
    integer_node: onnx.NodeProto,
) -> tuple[onnx.NodeProto, onnx.NodeProto, onnx.NodeProto] | None:
    cast_node = _single_consumer(consumers, integer_node.output[0], "Cast")
    if cast_node is None:
        return None
    scale_mul = _single_consumer(consumers, cast_node.output[0], "Mul")
    if scale_mul is None:
        return None
    scale_input = next((name for name in scale_mul.input if name != cast_node.output[0]), None)
    if scale_input is None:
        return None
    scale_product = producers.get(scale_input)
    if scale_product is None or scale_product.op_type != "Mul":
        return None
    return cast_node, scale_mul, scale_product


def _weight_scale_from_scale_product(
    scale_product: onnx.NodeProto,
    activation_scale: str,
) -> str | None:
    for input_name in scale_product.input:
        if input_name != activation_scale:
            return input_name
    return None


def convert_integer_ops_to_float(
    input_path: str | Path,
    output_path: str | Path,
) -> dict[str, int | str]:
    model = onnx.load(str(input_path), load_external_data=False)
    graph = model.graph
    original_nodes = list(graph.node)
    producers = _producer_map(original_nodes)
    consumers = _consumer_map(original_nodes)
    initializers = {tensor.name: numpy_helper.to_array(tensor) for tensor in graph.initializer}

    replacement_by_name: dict[str, list[onnx.NodeProto]] = {}
    skip_names: set[str] = set()
    new_initializers: list[onnx.TensorProto] = []
    converted_conv = 0
    converted_matmul = 0
    skipped_integer = 0

    for node in original_nodes:
        if node.op_type not in {"ConvInteger", "MatMulInteger"}:
            continue
        if len(node.input) < 2:
            skipped_integer += 1
            continue
        activation_quantized = node.input[0]
        weight_quantized = node.input[1]
        activation_zero_point = node.input[2] if len(node.input) > 2 else ""
        weight_zero_point = node.input[3] if len(node.input) > 3 else ""
        dql = producers.get(activation_quantized)
        if dql is None or dql.op_type != "DynamicQuantizeLinear" or len(dql.output) < 3:
            skipped_integer += 1
            continue
        cast_scale = _find_scale_mul_for_integer_output(producers, consumers, node)
        if cast_scale is None:
            skipped_integer += 1
            continue
        cast_node, scale_mul, scale_product = cast_scale
        activation_scale = dql.output[1]
        weight_scale = _weight_scale_from_scale_product(scale_product, activation_scale)
        if not weight_scale:
            skipped_integer += 1
            continue
        if weight_quantized not in initializers or weight_scale not in initializers:
            skipped_integer += 1
            continue

        weight_zp_array = initializers.get(weight_zero_point)
        fp_weight = _as_float_weight(
            initializers[weight_quantized],
            initializers[weight_scale],
            weight_zp_array,
        )
        safe_name = node.name.replace("/", "_").strip("_") or node.output[0]
        weight_name = f"{safe_name}_weight_fp32"
        activation_dequant = f"{safe_name}_activation_dequant"
        new_initializers.append(numpy_helper.from_array(fp_weight.astype(np.float32), weight_name))

        dequant_inputs = [activation_quantized, activation_scale]
        if activation_zero_point:
            dequant_inputs.append(activation_zero_point)
        dequant_node = helper.make_node(
            "DequantizeLinear",
            dequant_inputs,
            [activation_dequant],
            name=f"{safe_name}_activation_dequantize",
        )
        op_type = "Conv" if node.op_type == "ConvInteger" else "MatMul"
        fp_node = helper.make_node(
            op_type,
            [activation_dequant, weight_name],
            [scale_mul.output[0]],
            name=f"{safe_name}_{op_type.lower()}_fp32",
            **_node_attrs(node),
        )

        replacement_by_name[node.name] = [dequant_node, fp_node]
        skip_names.update({node.name, cast_node.name, scale_mul.name, scale_product.name})
        if node.op_type == "ConvInteger":
            converted_conv += 1
        else:
            converted_matmul += 1

    rewritten_nodes: list[onnx.NodeProto] = []
    for node in original_nodes:
        if node.name in replacement_by_name:
            rewritten_nodes.extend(replacement_by_name[node.name])
        elif node.name in skip_names:
            continue
        else:
            rewritten_nodes.append(node)

    graph.ClearField("node")
    graph.node.extend(rewritten_nodes)
    graph.initializer.extend(new_initializers)
    _remove_unused_initializers(graph)
    onnx.checker.check_model(model)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, str(output_path))

    return {
        "input": str(input_path),
        "output": str(output_path),
        "converted_conv_integer": converted_conv,
        "converted_matmul_integer": converted_matmul,
        "skipped_integer_ops": skipped_integer,
    }


def _remove_unused_initializers(graph: onnx.GraphProto) -> None:
    used_inputs = {input_name for node in graph.node for input_name in node.input if input_name}
    kept = [initializer for initializer in graph.initializer if initializer.name in used_inputs]
    graph.ClearField("initializer")
    graph.initializer.extend(kept)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    report = convert_integer_ops_to_float(args.input, args.output)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
