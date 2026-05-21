"""Evaluate an exported PARSeq ONNX model and write importable prediction JSON."""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Sequence

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps

from ocr_model_eval import load_labeled_dataset, measure_model_file, summarize_throughput


IMAGENET_MEAN = np.asarray([0.485, 0.456, 0.406], dtype=np.float32).reshape(3, 1, 1)
IMAGENET_STD = np.asarray([0.229, 0.224, 0.225], dtype=np.float32).reshape(3, 1, 1)


def preprocess_parseq_image(image_path: str | Path) -> np.ndarray:
    image = ImageOps.exif_transpose(Image.open(image_path)).convert("RGB")
    image = image.resize((128, 32), Image.Resampling.BILINEAR)
    arr = np.asarray(image, dtype=np.float32) / 255.0
    chw = arr.transpose(2, 0, 1)
    normalized = (chw - IMAGENET_MEAN) / IMAGENET_STD
    return normalized.reshape(1, 3, 32, 128).astype(np.float32)


def load_parseq_tokens(metadata_path: str | Path) -> list[str]:
    payload = json.loads(Path(metadata_path).read_text(encoding="utf-8"))
    tokens = payload.get("tokens")
    if not isinstance(tokens, list):
        raise ValueError(f"{metadata_path} must contain a tokens list")
    return [str(token) for token in tokens]


def decode_parseq_logits(logits: np.ndarray, tokens: Sequence[str]) -> str:
    if logits.ndim == 3:
        logits = logits[0]
    if logits.ndim != 2:
        raise ValueError(f"expected 2D or 3D PARSeq logits, got shape {logits.shape}")
    indices = np.argmax(logits, axis=-1)
    chars: list[str] = []
    for raw_idx in indices:
        idx = int(raw_idx)
        token = tokens[idx] if 0 <= idx < len(tokens) else ""
        if token in {"[E]", "[P]", "[B]"}:
            break
        chars.append(token)
    return "".join(chars).strip()


def evaluate_parseq_onnx(
    *,
    model_id: str,
    onnx_path: str | Path,
    dataset: str | Path,
    output: str | Path,
    metadata_path: str | Path,
    limit: int | None = None,
    providers: list[str] | None = None,
) -> dict[str, object]:
    samples = load_labeled_dataset(dataset)
    selected = samples[:limit] if limit else samples
    tokens = load_parseq_tokens(metadata_path)
    session = ort.InferenceSession(str(onnx_path), providers=providers or ["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    predictions = []
    total_inference_ms = 0.0

    for sample in selected:
        tensor = preprocess_parseq_image(sample.image_path)
        start = time.perf_counter()
        outputs = session.run(None, {input_name: tensor})
        latency_ms = (time.perf_counter() - start) * 1000.0
        total_inference_ms += latency_ms
        text = decode_parseq_logits(np.asarray(outputs[0]), tokens)
        predictions.append(
            {
                "filename": sample.filename,
                "text": text,
                "latency_ms": latency_ms,
            }
        )

    payload = {
        "model_id": model_id,
        "backend": "onnxruntime_parseq",
        **measure_model_file(onnx_path),
        "throughput": summarize_throughput(
            sample_count=len(predictions),
            inference_time_ms=total_inference_ms,
            batch_size=1,
        ),
        "predictions": predictions,
    }
    output_path = Path(output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"output": str(output_path), "samples": len(predictions)}, ensure_ascii=False))
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", default="parseq")
    parser.add_argument("--onnx-path", default="exported_candidates/parseq.onnx")
    parser.add_argument("--metadata-path", default="exported_candidates/parseq_metadata.json")
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args(argv)
    evaluate_parseq_onnx(
        model_id=args.model_id,
        onnx_path=args.onnx_path,
        metadata_path=args.metadata_path,
        dataset=args.dataset,
        output=args.output,
        limit=args.limit,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
