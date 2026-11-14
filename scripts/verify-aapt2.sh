#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <aapt2-path> <approved-sha256>" >&2
  exit 2
fi

AAPT2_PATH="$1"
APPROVED_SHA256="${2,,}"
if [[ ! -x "$AAPT2_PATH" ]]; then
  echo "AAPT2 is missing or not executable: $AAPT2_PATH" >&2
  exit 2
fi
if [[ ! "$APPROVED_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Approved AAPT2 SHA-256 is missing or malformed." >&2
  exit 2
fi

# Do not invoke the candidate until its bytes match the reviewed digest.
ACTUAL_SHA256="$(sha256sum "$AAPT2_PATH" | awk '{ print tolower($1) }')"
if [[ "$ACTUAL_SHA256" != "$APPROVED_SHA256" ]]; then
  echo "AAPT2 checksum mismatch: expected $APPROVED_SHA256, got $ACTUAL_SHA256." >&2
  echo "Rejected candidate was not executed." >&2
  exit 2
fi

if ! "$AAPT2_PATH" version >/dev/null 2>&1; then
  echo "Approved AAPT2 failed its version probe: $AAPT2_PATH" >&2
  exit 2
fi
printf '%s\n' "$ACTUAL_SHA256"
