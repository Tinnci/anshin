# Task Plan: OCR Candidate Evaluation And FastViT Integration

## Goal
Batch export/evaluate available OCR candidate models on desktop CPU for throughput and accuracy, including PaddleOCR ONNX candidates, PARSeq, and Apple FastViT-T8.

## Phases
| Phase | Status | Notes |
| --- | --- | --- |
| 1. Inspect current evaluator | completed | Confirmed existing LightSVTR CTC path and candidate config. |
| 2. Add tests for batch/CTC adapters | completed | Added throughput, Paddle CTC, output-layout, and metadata tests. |
| 3. Implement export/eval support | completed | Added PaddleOCR ONNX adapter, batching, throughput, and metadata shape parsing. |
| 4. Run CPU evaluation | completed | Ran full 120-sample CPU batch evaluation with Paddle ONNX and imported TrOCR predictions. |
| 5. Record result | completed | Updated `MODEL_EVALUATION.md` and result artifacts under `/tmp/medlog_bare_benchmark`. |
| 6. Inspect PARSeq checkpoint/API | completed | Verified raw state_dict checkpoint and Torch Hub model/tokenizer API. |
| 7. Add PARSeq tests | completed | Covered export configuration, preprocessing, decoding, and prediction JSON schema. |
| 8. Implement PARSeq export/eval | completed | Added `export_parseq_onnx.py`, `evaluate_parseq_onnx.py`, pixi tasks, and direct candidate runner support. |
| 9. Run PARSeq and integrated report | completed | Exported PARSeq ONNX external-data graph, evaluated 120 samples, and generated integrated CPU report. |
| 10. Implement FastViT-T8 CTC candidate | completed | Added FastViT CTC model, two-stage fine-tuning script, RGB ImageNet eval preprocessing, and candidate runner support. |
| 11. Smoke test FastViT path | completed | Ran no-pretrained CPU smoke fine-tune/export and unified runner evaluation from `/tmp/fastvit_ctc_smoke`. |
| 12. Implement Kaggle all-candidate fine-tune kernel | completed | Added Kaggle runner for LightSVTR tiny/base/large and FastViT-T8, plus complete pending/eval-only rows for other architectures. |
| 13. Add PaddleOCR Kaggle fine-tune configs | in_progress | Added tests and implementation for PP-OCRv5 mobile/server, RepSVTR, and SVTRv2 config materialization and Kaggle train/export/eval dispatch. |
| 14. Run FastViT Android NNAPI benchmark | in_progress | Added instrumentation benchmark and collected initial M2012K11C CPU/NNAPI measurements for `fastvit_t8_ctc_reparam.onnx`. |

## Errors Encountered
| Error | Attempt | Resolution |
| --- | --- | --- |
| LightSVTR exact dropped to 0% after batching | First CPU batch run | Fixed output slicing: LightSVTR exports `[time,batch,class]`; PaddleOCR exports `[batch,time,class]`. |
| Initial FastViT ONNX smoke was only 47k params | First FastViT wrapper used only feature stage 0 | Switched to multi-scale feature fusion across all FastViT stages, producing a 3.25M-param smoke ONNX while keeping 64 time steps. |
| FastViT Android 100-run CPU benchmark exceeded the practical wait window | First instrumentation run used `RUNS=100` | Killed the stuck run and used `RUNS=10` for comparable CPU/NNAPI smoke measurements. |
