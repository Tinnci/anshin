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
- Official PARSeq usage uses `torch.hub.load("baudm/parseq", "parseq", pretrained=True).eval()` and `model.tokenizer.decode(pred)` for decoding. Non-autoregressive evaluation is exposed through `decode_ar=false` and `refine_iters` controls in the official project examples.
- Local PARSeq checkpoint `exported_candidates/parseq/parseq-bb5792a6.pt` is a raw `OrderedDict` state_dict. It should be loaded into the inner `model.model`, not into the Lightning wrapper.
- PARSeq Torch Hub exposes `parseq(pretrained=False, decode_ar=False, refine_iters=0)`. The inner `model.model` stores `decode_ar` and `refine_iters`.
- PARSeq ONNX export uses external data: `parseq.onnx` plus `parseq.onnx.data`. Capacity accounting must include the sibling `.data` file.
- PARSeq direct CPU result on the 120-sample benchmark: 92.35 MB, 23,832,702 params, 78.83 ms mean, 12.69 samples/sec, 19.17% exact, 50.82% CER, 57.97% digit accuracy.
- timm exposes `fastvit_t8.apple_in1k` and `fastvit_t8.apple_dist_in1k`; HF also lists `timm/fastvit_t8.apple_in1k`.
- FastViT `features_only=True` returns four stages on 128x256 input: `[48,32,64]`, `[96,16,32]`, `[192,8,16]`, `[384,4,8]` in `[B,C,H,W]` shape.
- A single early FastViT stage preserves OCR time resolution but prunes most backbone params during ONNX export. Multi-scale fusion across all stages keeps 64 time steps and includes the full T8 backbone.
- No official Google `SigLIP-Nano 15M` checkpoint was found in HF/timm discovery; keep SigLIP out of executable candidates until a concrete repo id is selected.
- The all-candidate Kaggle fine-tune kernel can train LightSVTR tiny/base/large and FastViT-T8. PARSeq, PaddleOCR/RepSVTR/SVTRv2, TrOCR, ML Kit, and SigLIP-Nano are reported as eval-only/blocked rows unless dedicated fine-tuning adapters are added.
