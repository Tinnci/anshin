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
| 15. Rename Android application ID for formal debug build | completed | Replaced app/source package with `com.driezy.medlog`, verified debug/test compilation, installed and launched on connected M2012K11C. |
| 16. Apply Material Expressive home layout slice | completed | Consolidated Today progress, streak, next-dose, and primary action into an expressive overview card; removed duplicate empty-state FAB; verified with compile, install, ktlint, and logcat only. |
| 17. Apply Material Expressive health layout slice | completed | Added OCR hero action, grouped health metrics into a titled section, weakened recent records with supporting header, separated metric value/unit typography, and verified LoadingIndicator coverage. |
| 18. Complete Material Expressive layout/elevation/settings pass | completed | Converted Home default medication list into Now/Later task groups with PRN separated, flattened time-group elevation, consolidated Settings into Appearance, Reminders, OCR & Health, Widgets, and Data & About containers, then installed and launched on M2012K11C. |
| 19. Apply Material Expressive micro-motion pass | completed | Aligned recurrent micro-interactions with `MotionScheme.expressive()` by using spatial specs for bounds/position changes and effects specs for alpha/color in Home, medication rows, OCR, Add Medication, and Settings. |
| 20. Clean current Android warnings/errors | completed | Fixed lint-reported resource format errors, English minute plural warnings, KTX bitmap API warnings, generated launcher vector warnings, and Android Gradle Plugin version warning; verified lint XML total=0, ktlint/unit tests/debug assemble, install, launch, and no recent fatal logcat entries. |
| 21. Add debug seed data and non-visual verification | completed | Added deterministic Clock/Calendar helpers, `SeedDemoDataUseCase`, debug-only receiver, and seed format tests; installed/seeding on M2012K11C and verified counts, progress, low stock, latest health values, and FK integrity via adb/Room database queries. |
| 22. Audit and modernize remaining Material Expressive surfaces | completed | Unified cross-page scan entries, scanner surfaces, top-level Extended FAB usage, and low-elevation surface container rules for ordinary content blocks; continued code-level audit against Material/Expressive guidance without visual screenshots. |
| 23. Separate OCR model outputs and strengthen home hierarchy | completed | Split OCR candidate text by source/model, improved low-stock hierarchy, prevented important medication names from truncating too early, and documented the Flex-font adoption path. |
| 24. Add Flex font at theme layer | completed | Bundled Google Sans Flex weights 400/500/600/700, connected them through shared `MedLogFontFamily`, kept typography roles intact, and verified theme typography uses the shared font family. |
| 25. Optimize widgets notifications and data settings UX | completed | Redesigned widget previews, notification/live-progress overview, and backup/restore action hierarchy; added tests for live notification dismissal and deterministic widget preview specs. |
| 26. Optimize GitHub Actions Gradle caching | completed | CI/release now run full ktlint/unit/lint/APK verification through `setup-gradle` cache, without duplicate Gradle caches or `clean assemble*` steps. |
| 27. Migrate Compose Material Icons to Material Symbols XML | completed | Added 101 local Material Symbols vector drawables, centralized rendering through `MedLogIcons`/`MedLogIcon`, removed `material-icons-extended`, and added a regression guard test. |
| 28. Make Material Symbols sourcing reproducible | completed | Added a deterministic update script, generated-source markers, usage docs, and a guard test so future Material Symbols updates stay on local VectorDrawable XML. |
| 29. Add Material Symbols state and optical-size variants | completed | Generated selected `fill=1` navigation symbols and 40/48dp optical-size display symbols, then wired them into top-level navigation and large icon moments. |
| 30. Align custom motion with M3 Expressive spring tokens | completed | Replaced legacy tween/duration and hand-coded Spring constants in navigation, welcome, history, and detail motion hotspots with `MaterialTheme.motionScheme` spatial/effects tokens. |
| 31. Analyze Material 3 Carousel adoption opportunities | completed | Reviewed current Compose screens/components and mapped suitable carousel layouts, accessibility constraints, and priority candidates. |
| 32. Implement Material 3 Carousel optimization slice | completed | Applied carousel to Health metrics and Settings widget previews first, with guard tests, ktlint, unit tests, and debug assemble verification. |
| 33. Complete dynamic color and M3 role optimization | completed | Added guard coverage, completed static ColorScheme roles, moved custom visual states to roles, wired notification/widget colors to Material roles, and verified ktlint/unit/assemble. |
| 34. Check Material motion and shape theming | completed | Added motion/shape guard coverage, removed unthemed default transition specs, aligned Compose shape scale to Material 3 role values, and verified ktlint/unit/assemble. |
| 35. Apply editorial treatment selectively | completed | Added editorial typography tokens, applied a single hero-style editorial moment to the Home today-progress card, localized completion text, and verified ktlint/unit/assemble. |
| 36. Acquire external seven-segment datasets | completed | Downloaded/preprocessed available Kaggle and Hugging Face sources under `seven_segment_ocr/external_datasets/`; Roboflow/YUVA/Oxford/PACMAN blockers recorded in `dataset_summary.json`. |
| 37. Design unified OCR pipeline with PySide6/Kaggle compatibility | completed | Added `seven_segment_ocr/PIPELINE_DESIGN.md` defining headless pipeline core, JSONL events, PySide6 visual console, artifact layout, and Kaggle packaging strategy. |
| 38. Implement unified OCR pipeline and PySide6/Kaggle shell | completed | Added headless `pipeline/`, PySide6 `pipeline_ui/`, example task config, Kaggle packager, dataset inspect/prepare adapters, JSONL events, preview artifacts, pixi tasks, and smoke/test verification. |
| 39. Upgrade pipeline DAG execution and PySide6 control console | completed | Added task cache, partial/resume CLI, concurrent scheduler, DAG view, builder, node logs/artifact filtering, leaderboard scanner/widget, and regression/smoke verification. |

## Errors Encountered
| Error | Attempt | Resolution |
| --- | --- | --- |
| LightSVTR exact dropped to 0% after batching | First CPU batch run | Fixed output slicing: LightSVTR exports `[time,batch,class]`; PaddleOCR exports `[batch,time,class]`. |
| Initial FastViT ONNX smoke was only 47k params | First FastViT wrapper used only feature stage 0 | Switched to multi-scale feature fusion across all FastViT stages, producing a 3.25M-param smoke ONNX while keeping 64 time steps. |
| FastViT Android 100-run CPU benchmark exceeded the practical wait window | First instrumentation run used `RUNS=100` | Killed the stuck run and used `RUNS=10` for comparable CPU/NNAPI smoke measurements. |
| `FlowRow` compile error: unsupported `verticalAlignment` parameter | First home overview implementation compile | Switched to supported `verticalArrangement` spacing for wrapped chips. |
| Health app launch used old shorthand activity | First post-install launch attempted `com.driezy.medlog/.MainActivity` | Relaunched with resolved activity `com.driezy.medlog/.ui.MainActivity`; foreground launch succeeded with no recent fatal logs. |
| Lint reported 2 errors and 18 warnings while still exiting success | First `:app:lintDebug` returned success because lint abort is disabled | Treat lint XML as the source of truth and fix/suppress each reported issue before release tagging. |
| AGP 9.2.1 rejected Gradle 9.3.1 | First post-upgrade `:app:lintDebug` | Upgrade Gradle wrapper to 9.4.1, the minimum required by AGP 9.2.1. |
| Combined verification failed once at `:app:packageDebug` without detailed cause | First combined ktlint/test/assemble run after wrapper upgrade | Reran `:app:packageDebug --stacktrace`; task passed cleanly, indicating an incremental packaging transient after the toolchain switch. |
| GitHub CI failed at `Build Debug APK` after lint/test success | First remote CI run for `48e81b9` | Updated CI/release APK build steps to run clean builds with stacktraces; then upgraded GitHub JS actions to newer major versions after Node 24 force mode still emitted a deprecation annotation. |
| KSP generated output/cache corrupted during verification | Parallel Gradle compile and unit-test tasks both ran KSP | Stop concurrent Gradle runs, clear generated KSP debug caches, and retry verification serially. |
| External dataset access may require credentials/API tokens | Dataset acquisition phase | Attempt direct/package/API downloads first; record blocked sources and keep successfully downloaded sources staged with manifests. |
| PySide6 conda solve/install stalled and then Qt could not locate platform plugins | First pixi dependency attempt and first QApplication smoke | Switched PySide6 to pixi PyPI dependencies, ran `pixi install`, and set `QT_PLUGIN_PATH` / `QT_QPA_PLATFORM_PLUGIN_PATH` from `pipeline_ui.app` before PySide6 imports. |
| Cache skip of a parent task incorrectly blocked dependent tasks | First real rerun of `pipeline_task.example.yaml` after enabling default cache | Added a regression test and changed the scheduler to continue readiness evaluation after cache-only progress, so skipped dependencies unlock downstream tasks. |
