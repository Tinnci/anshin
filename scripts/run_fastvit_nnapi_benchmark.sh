#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_PATH="${1:-${ROOT_DIR}/seven_segment_ocr/exported_candidates/fastvit_t8_ctc_reparam.onnx}"
MODEL_ASSET="$(basename "${MODEL_PATH}")"
ANDROID_TEST_ASSETS="${ROOT_DIR}/app/src/androidTest/assets"
PROVIDER="${PROVIDER:-nnapi}"
WARMUP="${WARMUP:-10}"
RUNS="${RUNS:-100}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required for Android NNAPI benchmark." >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No Android device is connected. Connect a phone with USB debugging enabled." >&2
  exit 1
fi

if [[ ! -f "${MODEL_PATH}" ]]; then
  echo "FastViT ONNX model not found: ${MODEL_PATH}" >&2
  exit 1
fi

mkdir -p "${ANDROID_TEST_ASSETS}"
cp "${MODEL_PATH}" "${ANDROID_TEST_ASSETS}/${MODEL_ASSET}"

cd "${ROOT_DIR}"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:installDebug :app:installDebugAndroidTest

adb logcat -c
adb shell am instrument -w \
  -e class com.example.medlog.ocr.FastVitNnapiBenchmarkTest \
  -e provider "${PROVIDER}" \
  -e modelAsset "${MODEL_ASSET}" \
  -e warmup "${WARMUP}" \
  -e runs "${RUNS}" \
  com.example.medlog.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s FastVitBenchmark:I '*:S' | grep FASTVIT_BENCHMARK || true
