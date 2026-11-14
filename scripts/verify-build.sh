#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/test-verify-aapt2.sh"
exec "$ROOT/scripts/gradle-verified.sh" \
  clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
