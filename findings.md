# Findings: Desktop CPU Batch OCR Evaluation

## Baseline
- Workspace was clean at start except current user/agent changes from Paddle2ONNX source build: `.gitignore`, `MODEL_EVALUATION.md`, and `build_paddle2onnx_macos_x86_64.sh`.
- Local x86_64 Paddle2ONNX import and smoke export have already been verified.

## Evaluator
- ONNX Runtime batch evaluation now reports per-model throughput in samples/sec.
- Seven-segment LightSVTR exports produce CTC logits as `[time, batch, class]`.
- PaddleOCR exported recognizers produce CTC logits as `[batch, time, class]` and must be decoded with the `inference.yml` character dictionary.
- PaddleOCR preprocessing should use `RecResizeImg.image_shape` from `inference.yml`; current converted candidates use `[3, 48, 320]`.
- Desktop CPU batch evaluation completed with 10 runnable/imported candidates, 2 pending candidates, and 0 errors.
- The best current CPU/accuracy tradeoff is still `light_svtr_exported`: 2.64 MB, 670k params, 3.55 ms mean, 281.36 samples/sec, 79.17% exact.
- General OCR pretrained models without seven-segment adaptation are poor baselines on this benchmark: PP-OCR/SVTR family tops out at 28.33% exact, TrOCR base at 10.83% exact.
