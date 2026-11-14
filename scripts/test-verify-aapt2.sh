#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH="$ROOT/.aapt2-verifier-test"
trap 'rm -rf "$SCRATCH"' EXIT
rm -rf "$SCRATCH"
mkdir -p "$SCRATCH"
MARKER="$SCRATCH/invocations"
FAKE="$SCRATCH/aapt2"
cat > "$FAKE" <<SCRIPT
#!/usr/bin/env bash
printf x >> "$MARKER"
exit 0
SCRIPT
chmod +x "$FAKE"

if "$ROOT/scripts/verify-aapt2.sh" "$FAKE" \
    0000000000000000000000000000000000000000000000000000000000000000 \
    >"$SCRATCH/stdout" 2>"$SCRATCH/stderr"; then
  echo "Expected an unapproved AAPT2 candidate to be rejected." >&2
  exit 1
fi
INVOCATIONS=0
if [[ -f "$MARKER" ]]; then
  INVOCATIONS="$(wc -c < "$MARKER" | tr -d ' ')"
fi
if [[ "$INVOCATIONS" != "0" ]]; then
  echo "Unapproved AAPT2 was invoked $INVOCATIONS time(s)." >&2
  exit 1
fi
printf 'AAPT2 rejection regression: unapproved binary invocations=%s\n' "$INVOCATIONS"
