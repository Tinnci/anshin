"""Evaluate exported TrOCR ONNX models and write importable prediction JSON."""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import numpy as np
from PIL import Image

from ocr_model_eval import load_labeled_dataset


def measure_onnx_directory(model_dir: str | Path) -> dict[str, int | str]:
    import onnx

    root = Path(model_dir)
    model_bytes = 0
    parameter_count = 0
    for path in sorted(root.glob("*.onnx")):
        model_bytes += path.stat().st_size
        model = onnx.load(str(path), load_external_data=False)
        for initializer in model.graph.initializer:
            dims = [int(dim) for dim in initializer.dims]
            parameter_count += int(np.prod(dims)) if dims else 1
    return {
        "format": "onnx_directory",
        "model_bytes": model_bytes,
        "parameter_count": parameter_count,
    }


def evaluate_trocr_onnx(
    *,
    model_id: str,
    source_model_dir: str | Path,
    onnx_model_dir: str | Path,
    dataset: str | Path,
    output: str | Path,
    limit: int | None = None,
    max_length: int = 16,
) -> dict[str, object]:
    from optimum.onnxruntime import ORTModelForVision2Seq
    from transformers import TrOCRProcessor

    samples = load_labeled_dataset(dataset)
    selected = samples[:limit] if limit else samples
    processor = TrOCRProcessor.from_pretrained(source_model_dir, use_fast=False)
    model = ORTModelForVision2Seq.from_pretrained(onnx_model_dir, use_cache=False)
    capacity = measure_onnx_directory(onnx_model_dir)
    predictions = []

    for sample in selected:
        image = Image.open(sample.image_path).convert("RGB")
        pixel_values = processor(images=image, return_tensors="pt").pixel_values
        start = time.perf_counter()
        generated_ids = model.generate(pixel_values, max_length=max_length)
        latency_ms = (time.perf_counter() - start) * 1000.0
        text = processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        predictions.append(
            {
                "filename": sample.filename,
                "text": text,
                "latency_ms": latency_ms,
            }
        )

    payload = {
        "model_id": model_id,
        "backend": "onnxruntime_optimum_vision2seq",
        **capacity,
        "predictions": predictions,
    }
    output_path = Path(output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"output": str(output_path), "samples": len(predictions)}, ensure_ascii=False))
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", required=True)
    parser.add_argument("--source-model-dir", required=True)
    parser.add_argument("--onnx-model-dir", required=True)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--max-length", type=int, default=16)
    args = parser.parse_args(argv)
    evaluate_trocr_onnx(
        model_id=args.model_id,
        source_model_dir=args.source_model_dir,
        onnx_model_dir=args.onnx_model_dir,
        dataset=args.dataset,
        output=args.output,
        limit=args.limit,
        max_length=args.max_length,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
