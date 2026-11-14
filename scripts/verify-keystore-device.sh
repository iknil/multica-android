#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ADB="${ANDROID_SDK:+$ANDROID_SDK/platform-tools/adb}"
if [[ -z "$ADB" || ! -x "$ADB" ]]; then
  echo "ANDROID_HOME must contain platform-tools/adb." >&2
  exit 2
fi
READY_DEVICES="$($ADB devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$READY_DEVICES" -lt 1 ]]; then
  echo "No ready Android device/emulator. KeyStore verification was NOT executed." >&2
  echo "Connect one authorized device with adb state 'device', then rerun this script." >&2
  exit 3
fi
exec "$ROOT/scripts/gradle-verified.sh" connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ai.multica.android.core.auth.AndroidKeystoreSessionStoreTest
