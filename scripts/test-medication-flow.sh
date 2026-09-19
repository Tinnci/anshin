#!/usr/bin/env bash
# Run against an already booted emulator/device. No screenshots are captured.
set -euo pipefail

./gradlew :core:database:connectedDebugAndroidTest --build-cache --stacktrace
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.driezy.medlog.feature.medications.MedicationFlowUiTest \
  --build-cache --stacktrace
