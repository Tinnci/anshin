# Task Plan: OCR Candidate Evaluation And PARSeq Integration

## Goal
Batch export/evaluate available OCR candidate models on desktop CPU for throughput and accuracy, including PaddleOCR ONNX candidates and PARSeq where possible.

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

## Errors Encountered
| Error | Attempt | Resolution |
| --- | --- | --- |
| LightSVTR exact dropped to 0% after batching | First CPU batch run | Fixed output slicing: LightSVTR exports `[time,batch,class]`; PaddleOCR exports `[batch,time,class]`. |
