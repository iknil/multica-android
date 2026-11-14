#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_SDK" || ! -d "$ANDROID_SDK" ]]; then
  echo "ANDROID_HOME (or ANDROID_SDK_ROOT) must point to an installed Android SDK." >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo "JDK 17 is required but java was not found." >&2
  exit 2
fi
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [[ "$JAVA_MAJOR" != "17" ]]; then
  echo "JDK 17 is required; detected major version ${JAVA_MAJOR:-unknown}." >&2
  exit 2
fi

HOST_OS="$(uname -s)"
HOST_ARCH="$(uname -m)"
GRADLE_ARGS=(--no-daemon --no-build-cache --console=plain)
if [[ "$HOST_OS" == "Linux" && ( "$HOST_ARCH" == "aarch64" || "$HOST_ARCH" == "arm64" ) ]]; then
  AAPT2="${MULTICA_AAPT2_PATH:-}"
  if [[ -z "$AAPT2" ]]; then
    mapfile -t AAPT2_CANDIDATES < <(find "$ANDROID_SDK/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 -perm -u+x 2>/dev/null | sort -Vr)
    AAPT2="${AAPT2_CANDIDATES[0]:-}"
  fi
  if [[ -z "$AAPT2" || ! -x "$AAPT2" ]]; then
    cat >&2 <<MSG
Linux aarch64 requires a host-native AAPT2 binary.
Install one under ANDROID_HOME/build-tools or set MULTICA_AAPT2_PATH to its executable path.
MSG
    exit 2
  fi
  EXPECTED_AAPT2_SHA256="${MULTICA_AAPT2_SHA256:-$(awk '!/^#/ && NF { print $1; exit }' "$ROOT/config/aapt2-linux-aarch64.sha256")}"
  ACTUAL_AAPT2_SHA256="$("$ROOT/scripts/verify-aapt2.sh" "$AAPT2" "$EXPECTED_AAPT2_SHA256")"
  GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
fi

export ANDROID_HOME="$ANDROID_SDK"
export GRADLE_USER_HOME="${MULTICA_GRADLE_USER_HOME:-$ROOT/.gradle-verification}"
mkdir -p "$GRADLE_USER_HOME"
printf 'Verified Gradle path: host=%s/%s java=%s gradle_home=%s\n' \
  "$HOST_OS" "$HOST_ARCH" "$JAVA_MAJOR" "$GRADLE_USER_HOME"
if [[ ${AAPT2:-} ]]; then
  printf 'Verified native AAPT2: %s sha256=%s\n' "$AAPT2" "$ACTUAL_AAPT2_SHA256"
fi
exec "$ROOT/gradlew" "${GRADLE_ARGS[@]}" "$@"
