#!/usr/bin/env bash
set -euo pipefail

readonly AVD_NAME="${ANDROID_VOCAB_CI_AVD_NAME:-android-vocab-api30}"
readonly DEVICE_PROFILE="${ANDROID_VOCAB_CI_DEVICE_PROFILE:-pixel_2}"
readonly SYSTEM_IMAGE="${ANDROID_VOCAB_CI_SYSTEM_IMAGE:-system-images;android-30;aosp_atd;x86}"
readonly REPORT_DIR="build/reports/ci-emulator"
readonly EMULATOR_LOG="$REPORT_DIR/emulator.log"

mkdir -p "$REPORT_DIR"

cleanup() {
  adb emu kill >/dev/null 2>&1 || true
}

wait_for_boot() {
  local boot_completed=""
  timeout 180 adb wait-for-device
  for _ in {1..60}; do
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$boot_completed" == "1" ]]; then
      return 0
    fi
    sleep 5
  done
  echo "emulator did not finish booting" >&2
  adb devices -l >&2 || true
  tail -200 "$EMULATOR_LOG" >&2 || true
  return 1
}

disable_animations() {
  adb shell settings put global window_animation_scale 0 || true
  adb shell settings put global transition_animation_scale 0 || true
  adb shell settings put global animator_duration_scale 0 || true
}

trap cleanup EXIT

sdkmanager "$SYSTEM_IMAGE"
echo "no" | avdmanager create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE" \
  --device "$DEVICE_PROFILE"

"$ANDROID_HOME/emulator/emulator" \
  -avd "$AVD_NAME" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -accel on \
  >"$EMULATOR_LOG" 2>&1 &

wait_for_boot
disable_animations
./gradlew --no-daemon --console=plain connectedDebugAndroidTest
