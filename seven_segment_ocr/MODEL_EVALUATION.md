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

## Paddle2ONNX On Intel macOS

The official PyPI macOS wheels for `paddle2onnx` may be tagged as
`universal2` while containing an arm64-only native extension. On Intel/x86_64
macOS, you can directly install the precompiled x86_64 wheel published in our GitHub fork:

```bash
pixi run python -m pip install --force-reinstall --no-deps https://github.com/Tinnci/Paddle2ONNX/releases/download/v2.1.0-macos-x86_64/paddle2onnx-2.1.0-cp312-cp312-macosx_15_0_x86_64.whl
```

Alternatively, you can compile and build a local wheel from source:

```bash
./build_paddle2onnx_macos_x86_64.sh
pixi run python -m pip install --force-reinstall --no-deps .local_wheels/paddle2onnx-*.whl
```

The helper builds Protobuf 21.12 and Paddle2ONNX under `/tmp`, then writes only
the final wheel under `.local_wheels/`.

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

## External Download And Conversion Status

The authoritative download routes currently used are:

- PaddleOCR/PaddleX: official inference model archives from `paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/`.
- TrOCR: Hugging Face Hub repositories `microsoft/trocr-small-printed` and `microsoft/trocr-base-printed`.
- PARSeq: official Torch Hub entrypoint `torch.hub.load("baudm/parseq", "parseq", pretrained=True)`.
- ML Kit: Android Gradle/Maven SDK artifacts only; evaluate via official runtime prediction import.

Local artifact status:

| Candidate | Downloaded | Converted | Local artifact |
| --- | --- | --- | --- |
| `ppocrv5_mobile_rec` | Yes | Yes | `exported_candidates/ppocrv5_mobile_rec.onnx` |
| `en_ppocrv5_mobile_rec` | Yes | Yes | `exported_candidates/en_ppocrv5_mobile_rec.onnx` |
| `ppocrv5_server_rec` | Yes | Yes | `exported_candidates/ppocrv5_server_rec.onnx` |
| `repsvtr` | Yes | Yes | `exported_candidates/repsvtr.onnx` |
| `svtrv2_server` | Yes | Yes | `exported_candidates/svtrv2_server.onnx` |
| `parseq` | Yes | Pending adapter | `exported_candidates/parseq/parseq-bb5792a6.pt` |
| `trocr_small_printed` | Yes | Yes | `exported_candidates/trocr_small_printed_onnx` |
| `trocr_base_printed` | Yes | Yes | `exported_candidates/trocr_base_printed_onnx` |

Paddle conversion originally failed on this Intel macOS machine because the
PyPI `paddle2onnx` macOS wheel was tagged `universal2` but contained an
arm64-only native extension. The local x86_64 wheel build described above fixes
that blocker, and the Paddle candidates have been converted to ONNX under
`exported_candidates/`.

Current TrOCR ONNX baseline on `/tmp/medlog_bare_benchmark`, 120 samples:

| Model | Backend | Size | Params | Mean latency | Exact | CER | Digit accuracy |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `trocr_small_printed` | Optimum ONNX Runtime | 234.82 MB | 61,447,552 | 356.94 ms | 2.50% | 106.19% | 13.62% |
| `trocr_base_printed` | Optimum ONNX Runtime | 1468.56 MB | 384,802,560 | 1665.23 ms | 10.83% | 78.51% | 31.20% |

The current local environment used PyPI packages for conversion/runtime:

```bash
python -m pip install paddle2onnx paddlex paddlepaddle
python -m pip install optimum-onnx transformers sentencepiece
```

These PyPI dependencies could not be cleanly recorded in `pixi.toml` because
the conda solve pins `huggingface_hub==1.15.0` and `numpy==2.4.2`, while
Optimum ONNX currently resolves `huggingface-hub<1.0` and an older ONNX stack.
For reproducible CI, use a separate conversion environment for Paddle/Optimum
exports, then copy the exported ONNX artifacts into `exported_candidates/`.

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

For desktop CPU throughput evaluation, use a batch size for dynamic-batch
PaddleOCR models and import existing TrOCR prediction files:

```bash
pixi run python run_candidate_evaluation.py \
  --dataset /tmp/medlog_bare_benchmark \
  --candidate-config model_candidates.json \
  --output /tmp/medlog_bare_benchmark/candidate_results_cpu_batch_with_trocr.json \
  --table-output /tmp/medlog_bare_benchmark/candidate_results_cpu_batch_with_trocr.txt \
  --batch-size 8 \
  --warmup 2 \
  --import-predictions trocr_small_printed:/tmp/medlog_bare_benchmark/trocr_small_predictions.json \
  --import-predictions trocr_base_printed:/tmp/medlog_bare_benchmark/trocr_base_predictions.json \
  --print-table
```

Current desktop CPU batch result on `/tmp/medlog_bare_benchmark`, 120 synthetic
samples:

| Model | Backend | Size | Params | Mean latency | Throughput | Exact | CER | Digit accuracy |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `light_svtr_exported` | ONNX Runtime CTC | 2.64 MB | 670,384 | 3.55 ms | 281.36/s | 79.17% | 7.29% | 92.31% |
| `app_dequant_svtr` | ONNX Runtime CTC | 2.70 MB | 670,404 | 5.43 ms | 184.30/s | 78.33% | 7.47% | 92.31% |
| `light_svtr_kaggle_domain` | ONNX Runtime CTC | 2.64 MB | 670,384 | 3.74 ms | 267.61/s | 65.00% | 14.75% | 84.89% |
| `ppocrv5_mobile_rec` | PaddleOCR CTC ONNX | 15.80 MB | 4,113,247 | 51.15 ms | 19.55/s | 10.00% | 74.13% | 29.79% |
| `en_ppocrv5_mobile_rec` | PaddleOCR CTC ONNX | 7.35 MB | 1,900,399 | 69.96 ms | 14.29/s | 21.67% | 51.91% | 54.27% |
| `ppocrv5_server_rec` | PaddleOCR CTC ONNX | 80.59 MB | 21,094,619 | 1423.65 ms | 0.70/s | 17.50% | 56.83% | 49.47% |
| `repsvtr` | PaddleOCR CTC ONNX | 24.20 MB | 6,314,309 | 32.16 ms | 31.10/s | 19.17% | 52.64% | 54.32% |
| `svtrv2_server` | PaddleOCR CTC ONNX | 80.30 MB | 20,986,236 | 152.65 ms | 6.55/s | 28.33% | 42.44% | 64.41% |
| `trocr_small_printed` | Imported Optimum ONNX prediction | 234.82 MB | 61,447,552 | 356.94 ms | 2.80/s | 2.50% | 106.19% | 13.62% |
| `trocr_base_printed` | Imported Optimum ONNX prediction | 1468.56 MB | 384,802,560 | 1665.23 ms | 0.60/s | 10.83% | 78.51% | 31.20% |

`parseq` still needs a dedicated adapter. `mlkit_text_recognition_bundled`
still needs an official Android runtime prediction JSON; it is a closed SDK and
is intentionally not converted to ONNX.

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
