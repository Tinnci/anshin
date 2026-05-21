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
- Apple FastViT-T8 via timm ImageNet-1K weights and a CTC fine-tuning head.
- TrOCR small/base via Hugging Face Optimum ONNX export.
- Google ML Kit via official Android runtime prediction import.

## External Download And Conversion Status

The authoritative download routes currently used are:

- PaddleOCR/PaddleX: official inference model archives from `paddle-model-ecology.bj.bcebos.com/paddlex/official_inference_model/paddle3.0.0/`.
- TrOCR: Hugging Face Hub repositories `microsoft/trocr-small-printed` and `microsoft/trocr-base-printed`.
- PARSeq: official Torch Hub entrypoint `torch.hub.load("baudm/parseq", "parseq", pretrained=True)`.
- FastViT-T8: timm model `fastvit_t8.apple_in1k`, mirrored on Hugging Face as `timm/fastvit_t8.apple_in1k`.
- ML Kit: Android Gradle/Maven SDK artifacts only; evaluate via official runtime prediction import.

Local artifact status:

| Candidate | Downloaded | Converted | Local artifact |
| --- | --- | --- | --- |
| `ppocrv5_mobile_rec` | Yes | Yes | `exported_candidates/ppocrv5_mobile_rec.onnx` |
| `en_ppocrv5_mobile_rec` | Yes | Yes | `exported_candidates/en_ppocrv5_mobile_rec.onnx` |
| `ppocrv5_server_rec` | Yes | Yes | `exported_candidates/ppocrv5_server_rec.onnx` |
| `repsvtr` | Yes | Yes | `exported_candidates/repsvtr.onnx` |
| `svtrv2_server` | Yes | Yes | `exported_candidates/svtrv2_server.onnx` |
| `parseq` | Yes | Yes | `exported_candidates/parseq.onnx` + `exported_candidates/parseq.onnx.data` |
| `fastvit_t8_ctc` | Via timm | Pending full fine-tune | `exported_candidates/fastvit_t8_ctc/fastvit_t8_ctc.onnx` |
| `trocr_small_printed` | Yes | Yes | `exported_candidates/trocr_small_printed_onnx` |
| `trocr_base_printed` | Yes | Yes | `exported_candidates/trocr_base_printed_onnx` |

Paddle conversion originally failed on this Intel macOS machine because the
PyPI `paddle2onnx` macOS wheel was tagged `universal2` but contained an
arm64-only native extension. The local x86_64 wheel build described above fixes
that blocker, and the Paddle candidates have been converted to ONNX under
`exported_candidates/`.

PARSeq export uses the official Torch Hub architecture with
`decode_ar=False` and `refine_iters=0`, then loads the local raw state dict into
the inner PARSeq model:

```bash
pixi run python export_parseq_onnx.py \
  --checkpoint exported_candidates/parseq/parseq-bb5792a6.pt \
  --output exported_candidates/parseq.onnx \
  --metadata-output exported_candidates/parseq_metadata.json
```

The exported PARSeq model uses ONNX external data, so keep
`parseq.onnx.data` beside `parseq.onnx`.

FastViT-T8 uses a two-stage CTC fine-tuning flow. The implementation fuses all
FastViT feature stages into a 64-step horizontal sequence, then applies a
lightweight CTC head:

```bash
pixi run python train_fastvit_ctc.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output-dir exported_candidates/fastvit_t8_ctc \
  --stage1-epochs 3 \
  --stage2-epochs 12 \
  --batch-size 16
```

For a CPU smoke run that avoids downloading pretrained weights:

```bash
pixi run python train_fastvit_ctc.py \
  --dataset /tmp/medlog_bare_benchmark \
  --output-dir /tmp/fastvit_ctc_smoke \
  --stage1-epochs 1 \
  --stage2-epochs 0 \
  --batch-size 4 \
  --no-pretrained
```

## Kaggle Candidate Fine-tuning

Use the candidate fine-tuning kernel to run all supported trainable OCR
architectures on Kaggle and emit one comparison report:

```bash
pixi run kaggle-push-candidates
pixi run kaggle-status-candidates
```

The kernel writes:

- `/kaggle/working/candidate_finetune/candidate_plan.json`
- `/kaggle/working/candidate_finetune/candidate_finetune_results.json`
- `/kaggle/working/candidate_finetune/candidate_finetune_results.txt`
- per-candidate checkpoints, ONNX exports, and evaluation reports under
  `/kaggle/working/candidate_finetune/<candidate_id>/`

Currently trainable in this kernel:

- `light_svtr_tiny`
- `light_svtr_base`
- `light_svtr_large`
- `fastvit_t8_ctc`

The report also includes non-trainable or blocked candidates so the table
remains complete: PARSeq, PaddleOCR/RepSVTR/SVTRv2, TrOCR, ML Kit, and
`siglip_nano`. `siglip_nano` is intentionally marked
`blocked_missing_checkpoint` until a concrete official checkpoint/repo id is
selected.

For a short Kaggle smoke run, edit the kernel arguments in the Kaggle UI or run
the script locally with:

```bash
pixi run python kaggle_candidate_finetune_kernel/kaggle_candidate_finetune.py \
  --output-dir /tmp/candidate_finetune_smoke \
  --candidates light_svtr_tiny,fastvit_t8_ctc \
  --synthetic-samples 200 \
  --light-svtr-epochs 1 \
  --fastvit-stage1-epochs 1 \
  --fastvit-stage2-epochs 0 \
  --fastvit-no-pretrained
```

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
PaddleOCR models. PARSeq is evaluated directly from its exported ONNX graph;
existing TrOCR predictions are imported because they use the Optimum
vision-to-sequence runtime:

```bash
pixi run python run_candidate_evaluation.py \
  --dataset /tmp/medlog_bare_benchmark \
  --candidate-config model_candidates.json \
  --output /tmp/medlog_bare_benchmark/candidate_results_cpu_batch_parseq_direct.json \
  --table-output /tmp/medlog_bare_benchmark/candidate_results_cpu_batch_parseq_direct.txt \
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
| `parseq` | PARSeq ONNX | 92.35 MB | 23,832,702 | 78.83 ms | 12.69/s | 19.17% | 50.82% | 57.97% |
| `trocr_small_printed` | Imported Optimum ONNX prediction | 234.82 MB | 61,447,552 | 356.94 ms | 2.80/s | 2.50% | 106.19% | 13.62% |
| `trocr_base_printed` | Imported Optimum ONNX prediction | 1468.56 MB | 384,802,560 | 1665.23 ms | 0.60/s | 10.83% | 78.51% | 31.20% |

`mlkit_text_recognition_bundled` still needs an official Android runtime
prediction JSON; it is a closed SDK and is intentionally not converted to ONNX.

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
