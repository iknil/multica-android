#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/scripts/gradle-verified.sh" \
  clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
