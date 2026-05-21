# Unified OCR Model Evaluation

This project compares OCR recognizers through one result schema:

- `capacity`: model file format, bytes, and parameter count when available.
- `latency_ms`: mean, p50, p95, min, and max for the runtime used by that adapter.
- `metrics`: exact match, normalized exact match, character error rate, and digit accuracy.
- `samples`: per-image truth, prediction, and latency.

## Backends

Use ONNX Runtime for models that have a faithful ONNX graph:

```bash
pixi run python ocr_model_eval.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output /tmp/medlog_bare_benchmark/results.json \
  --onnx-model exported_svtr:exported/svtr_seven_seg.onnx \
  --onnx-model kaggle_v5_domain:/tmp/kaggle_domain_v5_key/domain_adaptation/svtr_seven_seg_domain.onnx
```

Closed SDK models, including Google ML Kit Text Recognition, must be evaluated
through their official runtime and imported as prediction JSON. Do not treat
extracted AAR fragments as the complete OCR model: the SDK pipeline includes
native code, model metadata, preprocessing, layout logic, and postprocessing.

## ConvInteger Compatibility

The Android-packaged quantized SVTR model uses ONNX Runtime dynamic quantization
patterns with `ConvInteger` and `MatMulInteger`. Some desktop CPU ORT builds do
not implement `ConvInteger`, so desktop evaluation can fail even when Android
execution is valid.

Convert that graph into a desktop-executable floating-point compatibility graph:

```bash
pixi run python dequantize_onnx.py \
  --input ../app/src/main/assets/svtr_seven_seg.onnx \
  --output /tmp/medlog_app_svtr_dequant.onnx
```

The converter targets this pattern:

```text
DynamicQuantizeLinear -> ConvInteger/MatMulInteger -> Cast -> Mul(scale_product)
```

It inserts `DequantizeLinear` for the quantized activation, dequantizes constant
weights into FP32 initializers, and replaces the integer op plus scale
multiplication with standard `Conv` or `MatMul`. This is a compatibility graph
for desktop benchmarking; keep the original INT8 model for Android deployment.

Imported prediction schema:

```json
{
  "model_id": "mlkit_text_recognition_bundled",
  "backend": "android_mlkit",
  "model_bytes": 1234,
  "parameter_count": null,
  "predictions": [
    {
      "filename": "seq_000001.png",
      "text": "138/88",
      "latency_ms": 12.4
    }
  ]
}
```

Then import it into the shared metrics:

```bash
pixi run python ocr_model_eval.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output /tmp/medlog_bare_benchmark/mlkit_imported.json \
  --import-predictions mlkit_predictions.json
```

## Candidate Matrix

| Model family | Direct ONNX benchmark | Capacity source | Notes |
| --- | --- | --- | --- |
| LightSVTR exported | Yes | Local ONNX initializer count | Current custom seven-segment recognizer. |
| LightSVTR Kaggle domain | Yes | Local ONNX initializer count | Synthetic domain-adapted variant from Kaggle. |
| PP-OCRv5 mobile/server | After Paddle export | PaddleOCR model docs/artifacts | Good next open-source baseline for mobile/server tradeoffs. |
| RepSVTR | After Paddle export | PaddleOCR model docs/artifacts | Mobile recognizer candidate. |
| SVTRv2 | After Paddle export | PaddleOCR model docs/artifacts | Stronger server-side recognizer candidate. |
| PARSeq | After PyTorch/ONNX export | Checkpoint + ONNX initializer count | Scene text recognition baseline; likely heavier. |
| TrOCR small/base/large | After Hugging Face ONNX export | Checkpoint + ONNX initializer count | General OCR baseline; likely too heavy for app-side fallback. |
| Google ML Kit Text Recognition | No | SDK/AAR size only | Black-box baseline through Android SDK result import. |

## Current Smoke Result

Dataset: `/tmp/medlog_bare_benchmark`, 120 synthetic samples, `real_world_ratio=0.0`.

| Model | Backend | Size | Params | Mean latency | Exact | CER | Digit accuracy |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `exported_svtr` | ONNX Runtime CPU | 2.77 MB | 670,384 | 4.24 ms | 79.17% | 7.29% | 92.31% |
| `kaggle_v5_domain` | ONNX Runtime CPU | 2.77 MB | 670,384 | 4.30 ms | 64.17% | 14.21% | 86.14% |
| `app_quant_svtr` | ONNX Runtime CPU | 0.92 MB | n/a | n/a | n/a | n/a | n/a |

`app_quant_svtr` failed on desktop CPU ONNX Runtime because its graph contains
`ConvInteger`, which this ORT build does not implement. It may still be valid
for Android ONNX Runtime, but it is not a portable desktop benchmark artifact.

After conversion with `dequantize_onnx.py`, the app-packaged quantized model can
be included in the desktop ONNX Runtime smoke run:

| Model | Backend | Size | Params | Mean latency | Exact | CER | Digit accuracy |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `app_dequant_svtr` | ONNX Runtime CPU | 2.70 MB | 670,404 | 5.21 ms | 78.33% | 7.47% | 92.31% |
| `exported_svtr` | ONNX Runtime CPU | 2.64 MB | 670,384 | 3.69 ms | 79.17% | 7.29% | 92.31% |
| `kaggle_v5_domain` | ONNX Runtime CPU | 2.64 MB | 670,384 | 4.27 ms | 64.17% | 14.21% | 86.14% |

## Candidate Export Plan

Generate an auditable dry-run plan for all candidate families:

```bash
pixi run python download_and_export_candidates.py \
  --dry-run \
  --output export_candidates_plan.json
```

The plan covers:

- LightSVTR exported and Kaggle domain artifacts.
- PaddleOCR PP-OCRv5 mobile/server, RepSVTR, and SVTRv2 via Paddle2ONNX/PaddleX.
- PARSeq via a PyTorch checkpoint export path.
- TrOCR small/base via Hugging Face Optimum ONNX export.
- Google ML Kit via official Android runtime prediction import.

## Integrated Candidate Evaluation

Run the full candidate-level evaluation report. The runner evaluates local
artifacts that are already available, prepares `app_dequant_svtr` from the
Android-packaged quantized model, and marks missing external exports as
`pending` instead of dropping them from the report.

```bash
pixi run python run_candidate_evaluation.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output /tmp/medlog_bare_benchmark/candidate_results.json \
  --table-output /tmp/medlog_bare_benchmark/candidate_results.txt \
  --print-table
```

If an official-runtime baseline exists, pass it explicitly:

```bash
pixi run python run_candidate_evaluation.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output /tmp/medlog_bare_benchmark/candidate_results.json \
  --import-predictions mlkit:mlkit_predictions.json
```

## Sources

- Google ML Kit Android Text Recognition: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- PaddleOCR text recognition module docs: https://swhl.github.io/PaddleOCR/main/en/version3.x/module_usage/text_recognition.html
- PARSeq repository: https://github.com/baudm/parseq
- TrOCR model family listing: https://huggingface.co/models?search=trocr
