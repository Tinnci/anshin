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
- Pushed Kaggle candidate fine-tune kernel version 3; remote status reported `KernelWorkerStatus.RUNNING`.
- Probed Kaggle v3 while running: output files still reflected the previous run and logs were empty.
- Added a failing test and fix for PaddlePaddle GPU installation, defaulting Kaggle to `paddlepaddle-gpu==3.3.0` via the official CUDA 12.6 wheel index.
- Pushed Kaggle candidate fine-tune kernel version 4 with the official CUDA 12.6 Paddle wheel index; remote status reported `KernelWorkerStatus.RUNNING`.
- Added failing tests and fixes for the v4 CUDA dependency conflict: FastViT now runs before PaddleOCR, and Paddle installs with `--no-deps` unless `--paddle-install-deps` is explicitly set.
- Pushed Kaggle candidate fine-tune kernel version 5 with FastViT-before-Paddle ordering and Paddle `--no-deps`; remote status reported `KernelWorkerStatus.RUNNING`.
- Started formal Android package rename and debug-device install task.
- Confirmed connected adb device: `6b9f2b84` (`M2012K11C`).
- Renamed Android app/source package to `com.driezy.medlog`, including Gradle `namespace`/`applicationId`, Kotlin packages/imports, manifest actions, shortcuts, widget config class names, benchmark script package names, and Room schema export directory.
- Verified `./gradlew :app:assembleDebug`, `./gradlew :app:installDebug`, and `./gradlew :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- Launched `com.driezy.medlog/.ui.MainActivity` on M2012K11C; process `21676` ran in foreground with no recent fatal logcat entries for the new package.
- Optimized OCR settings UI before commit by extracting the repeated model option card, moving visible badge/spec labels into string resources, and cancelling `SevenSegmentRecognizer`'s settings collection scope on close.
- Verified optimization with `./gradlew :app:ktlintCheck` and `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin`.
- Started Material Expressive home layout implementation slice focused on layout hierarchy, emphasized typography, surface elevation, and primary action placement.
- Completed the home layout slice: Today overview now owns progress, streak, next-dose, and take-all action; empty state no longer duplicates the add action with a FAB.
- Started HealthScreen Material Expressive slice focused on OCR hero action, health metric sections, supporting recent-record hierarchy, and emphasized metric typography.
- Completed the HealthScreen slice: added a primary OCR scan hero with manual-entry secondary action, wrapped stats in a health metrics section, lowered recent records into supporting content, and separated emphasized metric values from smaller unit labels.
- Confirmed OCR processing overlay and HealthScreen loading state already use Material3 Expressive `LoadingIndicator`.
- Verified HealthScreen changes with `./gradlew :app:compileDebugKotlin`, `./gradlew :app:ktlintCheck`, `./gradlew :app:assembleDebug`, `./gradlew :app:installDebug`, and a no-fatal `adb logcat` check after launching `com.driezy.medlog/.ui.MainActivity`.
- Completed the broader Material Expressive pass: Home now defaults to "Now" and "Later today" task groups with PRN separated, time-period group containers use flat surface hierarchy, and Settings is consolidated into the requested Appearance / Reminders / OCR & Health / Widgets / Data & About containers.
- Re-verified with `./gradlew :app:compileDebugKotlin`, `./gradlew :app:ktlintCheck`, `./gradlew :app:assembleDebug`, `./gradlew :app:installDebug`, and launched `com.driezy.medlog/.ui.MainActivity` on M2012K11C with no recent fatal logcat entries.
- Completed a Material Expressive micro-motion pass after checking current official Compose Material3 motion guidance: Home progress digits, medication status confirmation, OCR content swaps, Add Medication dynamic form fields, Settings expand/collapse groups, and shared animated list items now use `MotionScheme` spatial specs for movement/size and effects specs for alpha/color.
- Installed the micro-motion build on M2012K11C and launched `com.driezy.medlog/.ui.MainActivity`; no recent fatal logcat entries were found.
- Started current lint cleanup after `:app:lintDebug` report showed 2 errors and 18 warnings despite Gradle success due to lint abort being disabled.
- First post-upgrade lint run failed because AGP 9.2.1 requires Gradle 9.4.1; updated the Gradle wrapper URL and checksum before retrying.
- `:app:lintDebug` passed with "Lint found no errors or warnings"; parsed `app/build/reports/lint-results-debug.xml` and confirmed `total=0`.
- Combined ktlint/test/assemble run failed once at `:app:packageDebug`; isolated `:app:packageDebug --stacktrace` then passed, so continued by suppressing the remaining JVM test warning via `-Xshare:off`.
- Re-ran `./gradlew :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug`; build passed cleanly with no visible warning output.
- Re-ran `./gradlew :app:lintDebug` after build-script changes; lint passed with no errors or warnings and XML `lint_issues=0`.
- Installed debug APK on connected M2012K11C (`6b9f2b84`), launched `com.driezy.medlog`, confirmed process startup, and found no recent fatal AndroidRuntime entries for the app.
- Pushed `48e81b9 Resolve Android lint warnings`; GitHub CI passed ktlint, unit tests, and Android Lint, then failed at `Build Debug APK` with `PackageAndroidArtifact$IncrementalSplitterRunnable`.
- Verified local `./gradlew clean :app:assembleDebug --stacktrace` passes; updated CI and release workflows to opt JS actions into Node 24 and run clean APK builds with stacktraces.
- GitHub CI for `1891a37` passed, but still emitted a Node 20 deprecation annotation because v4 JS actions were merely forced onto Node 24. Checked upstream tags and upgraded CI/release actions to current Node 24-capable major versions.
- Started deterministic debug seed data and all-page Material Expressive audit request. Stopped the interrupted local `gh run watch` process; GitHub Release run for `v1.15.5` was still in progress at handoff.
- Added red `SeedDataFormatTest`, then implemented debug-only `SeedDemoDataUseCase`, deterministic `SeedDemoCalendar`, `SeedDemoProfile`, and `DebugSeedReceiver`.
- Verified `SeedDataFormatTest` passes. Installed debug APK on `6b9f2b84`, seeded via explicit adb broadcast with `reset=true profile=standard`, and queried copied Room database: medications=5, logs=8, health_records=6, today_taken_scheduled=3/7, today_taken_all=4/8, low_stock=2, health latest values for all six `HealthType` entries, orphan_logs=0.
- Unified the first scanner/entry surface pass: camera permission empty state, OCR/health OCR/QR guidance pill, QR viewfinder overlay, OCR flash button, Home QR entry, Health OCR toolbar entry, Add Medication OCR entry, and QR dialog import/share/replace actions. Verified with ktlint, unit tests, assemble, install, adb seed, and copied Room database queries; no screenshot/visual check used.
- Switched code-level Material audit away from long Android lint runs. Standardized top-level page primary actions to right-side `ExtendedFloatingActionButton` with labels on Home, Health, History, Drugs, and Diary; kept `VibrantFloatingActionButton` only inside the OCR camera surface as an in-context capture action. Flattened remaining low-priority `ElevatedCard` usages in PRN, Health BMI/chart, and Settings widget picker into `surfaceContainerLow` cards.
- Applied title/FAB/I18N audit: Health and Diary now use collapsible `LargeTopAppBar` like other top-level destinations; Health adds a secondary `SmallFloatingActionButton` for manual entry while keeping scan as the labeled primary FAB. Added missing Japanese and Korean strings for Home groups, Health OCR hero/sections, OCR states, and OCR model settings; XML/i18n key parity checks, compile, ktlint, seed format test, and debug install all passed.
- Clarified and corrected OCR scan-frame behavior: the previous camera frame was visual guidance only while OCR processed the full captured image. Added `OcrRecognitionRegion` so medication OCR and health OCR now crop the captured bitmap to the frame-shaped center region before ML Kit, seven-segment CRNN, and LCD display detection run; medication uses a wide text frame and health uses a device-display frame. Updated localized hints to say content should be inside the frame, added region unit tests, and verified compile, ktlint, assemble, tests, and debug install.
- Added a pre-capture readiness panel for OCR camera flows with frame-fill, hold-steady, and glare-avoidance guidance. Added `OcrFrameInputPreprocessor` so the cropped frame image is normalized to a model-friendly long-edge range before OCR variants/model inference, with sampled luminance/contrast metrics logged for non-visual diagnosis. Verified XML/i18n parity, compile, ktlint, targeted OCR tests, assemble, and debug install.
- Started OCR result grouping and home hierarchy refinement. Checked current official Material/Android guidance for M3 Expressive, emphasis, tonal elevation, FAB variants, top app bars, and Google Sans Flex / variable font direction.
- Parallel Gradle verification triggered KSP cache/file-generation corruption in `app/build/kspCaches/debug`; stop running KSP-backed Gradle tasks in parallel and clear generated KSP cache before retrying serial verification.
- Completed OCR result grouping by recognition source/model, strengthened low-stock warning surfaces, allowed important medication names to wrap instead of single-line ellipsizing, and removed negative tracking from display typography.
- Verified serially with `:app:compileDebugKotlin`, targeted `OcrRecognitionOutputTest`, `:app:ktlintCheck`, `:app:assembleDebug`, `:app:installDebug` on `6b9f2b84`, app launch, no recent `AndroidRuntime`/`FATAL EXCEPTION`, and full `:app:testDebugUnitTest`.
- Started theme-layer Flex font integration. Current approach: use Compose `ui-text-google-fonts` and one shared `MedLogFontFamily` applied to baseline and emphasized typography roles.
- Adjusted implementation to bundle Google Sans Flex TTF weights locally instead of using runtime downloadable fonts, then wired `MedLogFontFamily` into every baseline and emphasized `TextStyle`.
- Verified Flex font integration with `:app:compileDebugKotlin`, targeted `MedLogTypographyTest`, `:app:ktlintCheck`, full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:installDebug` on `6b9f2b84`, app launch, and no recent `AndroidRuntime`/`FATAL EXCEPTION`.
- Started widgets/notifications/data settings UX pass. Initial audit: widget previews are static vector placeholders with fake exact times; reminder settings lack an at-a-glance summary; backup/restore are plain list items despite restore being destructive.
- Checked GitHub Actions Gradle caching strategy. Current workflows already used `gradle/actions/setup-gradle`, but split ktlint/tests/lint/package into separate Gradle invocations and still ran `clean assemble*`.
- Updated CI and release workflows so GitHub Actions performs ktlint, unit tests, Android lint, and APK assembly in one cache-aware Gradle invocation with `--build-cache --configuration-cache --stacktrace`; removed clean builds and added debug APK artifact upload.
- Verified workflow YAML parses locally and confirmed no `clean assemble*` command remains in `.github/workflows`.
- Added a red `MaterialSymbolsMigrationGuardTest`, confirmed it failed on the old Compose Material Icons dependency/imports and missing XML icon set, then migrated icon usage to local Material Symbols XML drawables.
- Generated 101 `ic_symbol_*.xml` drawables from Material Symbols Rounded paths and centralized rendering through explicit `MedLogIcon(icon = ...)`; removed the incorrect fake `Icon(imageVector: Int)` compatibility layer and guarded against it in tests.
- Removed `material-icons-extended` from Gradle dependencies and replaced all `Icons.*` imports/usages across navigation, scanning, home, settings, and the remaining modules with `MedLogIcons`.
- Optimized build/CI behavior by increasing Gradle daemon heap to 4G with ParallelGC, keeping GitHub Actions on `setup-gradle`, disabling lint's expensive `VectorPath` check for official Material Symbols vectors, and keeping Gradle executions serial to avoid KSP cache contention.
- Fixed all current lint warnings: debug seed receiver exported warning, OCR result count pluralization, unused strings, KTX bitmap pixel access, and external vector path analysis.
- Verified `:app:compileDebugKotlin :app:lintDebug`, parsed lint XML with `lint_issues=0`, and ran `:app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug` successfully; warm assemble completed in 8s and final combined verification in 19s.
- Checked current official Google Fonts Material Symbols and Android Compose icon guidance. Android now recommends Google Font Icons / Material Symbols XML from the Android tab instead of the old Compose `material-icons` artifacts.
- Confirmed the provided family download URL is a font/ligature asset path, not the runtime model MedLog wants for app icons. MedLog keeps icons as local VectorDrawable XML rendered through `MedLogIcon` and `painterResource`.
- Added `scripts/update_material_symbols.mjs` to batch-regenerate the 101 Material Symbols Rounded VectorDrawable XML files and `MedLogIcons.kt`, with generated-source comments in every output file.
- Added `docs/material-symbols.md` documenting the source, update workflow, and rules against runtime symbol fonts, `androidx.compose.material.icons`, `material-icons-extended`, and fake `Icon(imageVector: Int)` overloads.
- Added a red-then-green reproducibility guard in `MaterialSymbolsMigrationGuardTest`; targeted guard test passed after adding the script, generated markers, and docs.
- Verified the Material Symbols sourcing iteration with `./gradlew :app:compileDebugKotlin :app:lintDebug :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug`; lint reported no errors or warnings.
- Added failing guard coverage for the two Material Symbols enhancements: selected top-level navigation must use `fill=1` variants, and large display symbols must use 40/48dp optical-size resources.
- Extended `scripts/update_material_symbols.mjs` to generate 112 XML symbols total: 101 baseline 24dp icons, 6 selected `fill=1` navigation icons, and 5 optical-size display icons.
- Wired selected variants into bottom navigation, rail, and drawer destinations; wired optical-size variants into the drawer brand icon, OCR empty state, Health empty state, Drugs empty state, and the welcome confirmation moment.
- Re-ran targeted `MaterialSymbolsMigrationGuardTest`; the new state/optical-size checks passed.
- Started M3 Expressive motion physics pass from the May 2025 guidance: keep the product on `MotionScheme.expressive()`, use spatial tokens for movement/size and effects tokens for opacity/color, and avoid legacy duration/easing for custom motion hotspots.
- Added failing `MotionSystemGuardTest`, then migrated navigation transitions, welcome pager/entry/pulse motion, History color transitions, and Medication Detail adherence color transitions to `MaterialTheme.motionScheme` spring tokens.
- Verified motion changes with targeted `MotionSystemGuardTest`, then ran `:app:ktlintCheck`, targeted motion/icon guard tests, and `:app:assembleDebug`; installed and launched the debug APK on `6b9f2b84`.
- Android lint was attempted twice after the icon/motion work, but both runs stalled for more than 10 minutes in lint analysis/report generation. The verified compile/ktlint/tests/assemble/install path passed; lint needs a separate tooling-performance investigation.
