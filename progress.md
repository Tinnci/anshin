# Progress: Desktop CPU Batch OCR Evaluation

## 2026-05-21
- Started batch desktop CPU throughput/accuracy evaluation work.
- Added tests for PaddleOCR CTC decoding, throughput summary, model output layout selection, and PaddleOCR metadata parsing.
- Implemented PaddleOCR ONNX evaluation, batch throughput measurement, and candidate config metadata paths.
- Fixed a LightSVTR regression caused by treating `[time,batch,class]` output as `[batch,time,class]`.
- Ran all Python tests: `pixi run python -m unittest discover -s tests -v` passed 23 tests.
- Ran full CPU batch report with TrOCR imported predictions:
  `/tmp/medlog_bare_benchmark/candidate_results_cpu_batch_with_trocr.json`
  and `/tmp/medlog_bare_benchmark/candidate_results_cpu_batch_with_trocr.txt`.
- Started PARSeq integration work. Read planning-with-files and TDD guidance, then began verifying the real checkpoint/API before coding.
- Added failing PARSeq integration tests first, then implemented `export_parseq_onnx.py` and `evaluate_parseq_onnx.py`.
- Verified PARSeq unit tests: `pixi run python -m unittest tests.test_parseq_integration -v` passed 4 tests.
- Exported PARSeq to `exported_candidates/parseq.onnx` plus external data file `exported_candidates/parseq.onnx.data`.
- Added direct PARSeq support to `run_candidate_evaluation.py`, then verified integrated report:
  `/tmp/medlog_bare_benchmark/candidate_results_cpu_batch_parseq_direct.json`
  and `/tmp/medlog_bare_benchmark/candidate_results_cpu_batch_parseq_direct.txt`.
- Implemented FastViT-T8 CTC candidate with multi-scale feature fusion, two-stage training, ONNX export metadata, and `torch_ctc_onnx` runner support.
- Ran FastViT no-pretrained CPU smoke train/export:
  `/tmp/fastvit_ctc_smoke/fastvit_t8_ctc.onnx`.
- Ran FastViT smoke candidate evaluation:
  `/tmp/fastvit_ctc_smoke/results.json` and `/tmp/fastvit_ctc_smoke/results.txt`.
- Added `kaggle_candidate_finetune_kernel` for Kaggle-side all-candidate reporting and fine-tuning of trainable architectures.
- Verified plan-only run:
  `pixi run python kaggle_candidate_finetune_kernel/kaggle_candidate_finetune.py --output-dir /tmp/kaggle_candidate_plan --plan-only --candidates all`.
- Pushed Kaggle candidate fine-tune kernel version 2; remote status reported `KernelWorkerStatus.RUNNING`.

## 2026-05-22
- Pulled Kaggle candidate fine-tune results from `/tmp/medlog_kaggle_candidate_results/candidate_finetune` and analyzed FastViT/LightSVTR accuracy, capacity, and local CPU latency.
- Added failing tests first for PaddleOCR Kaggle trainable candidates and PaddleOCR config materialization.
- Implemented PaddleOCR Kaggle dispatch support for `ppocrv5_mobile_rec`, `ppocrv5_server_rec`, `repsvtr`, and `svtrv2_server`.
- Verified PaddleOCR candidate plan:
  `pixi run python kaggle_candidate_finetune_kernel/kaggle_candidate_finetune.py --output-dir /tmp/kaggle_candidate_plan_paddle --plan-only --candidates all`.
- Verified PaddleOCR config materialization smoke under `/tmp/kaggle_paddle_config_smoke`.
- Added Android instrumentation benchmark for FastViT ONNX Runtime CPU/NNAPI and helper script `scripts/run_fastvit_nnapi_benchmark.sh`.
- Compiled Android benchmark:
  `./gradlew :app:compileDebugAndroidTestKotlin`.
- Ran FastViT reparameterized ONNX on connected M2012K11C:
  CPU `mean_ms=49.70 p50_ms=50 p95_ms=51 throughput_sps=20.12`;
  NNAPI `mean_ms=49.20 p50_ms=49 p95_ms=50 throughput_sps=20.33`;
  `nnapi_cpu_disabled` one-run smoke `mean_ms=52.00`.
