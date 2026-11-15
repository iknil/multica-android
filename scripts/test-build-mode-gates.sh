#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE=("$ROOT/scripts/gradle-verified.sh" help --offline)
PASSED_NEGATIVE=0
PASSED_POSITIVE=0

expect_failure() {
  local expected="$1"
  shift
  local output status
  set +e
  output="$("${GRADLE[@]}" "$@" 2>&1)"
  status=$?
  set -e
  if [[ $status -eq 0 || "$output" != *"$expected"* ]]; then
    printf 'Expected failure containing: %s\nActual status: %s\n%s\n' "$expected" "$status" "$output" >&2
    return 1
  fi
  PASSED_NEGATIVE=$((PASSED_NEGATIVE + 1))
}

expect_success() {
  local output status
  set +e
  output="$("${GRADLE[@]}" "$@" 2>&1)"
  status=$?
  set -e
  if [[ $status -ne 0 ]]; then
    printf 'Expected success, status: %s\n%s\n' "$status" "$output" >&2
    return 1
  fi
  PASSED_POSITIVE=$((PASSED_POSITIVE + 1))
}

expect_failure "multica.fixtureMode must be true or false" -Pmultica.fixtureMode=yes
expect_failure "Non-fixture builds cannot use a .invalid API host" -Pmultica.fixtureMode=false -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds require multica.googleServerClientId" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://test.example.com
expect_failure "Non-fixture builds require an absolute HTTPS" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=http://example.com -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds require an absolute HTTPS" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=http://127.0.0.1 -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds require an absolute HTTPS" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://127.1 -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds reject non-canonical IP address forms" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://0177.0.0.1 -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds reject non-canonical IP address forms" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://2130706433 -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds reject non-canonical IP address forms" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://0x7f000001 -Pmultica.googleServerClientId=public-client
expect_success -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://test.example.com -Pmultica.googleServerClientId=public-client

# Task selection is part of the release gate; failures occur during configuration, before compilation.
GRADLE=("$ROOT/scripts/gradle-verified.sh" assembleRelease --dry-run --offline)
expect_failure "Release builds must set -Pmultica.fixtureMode=false"
expect_failure "Non-fixture builds require an absolute HTTPS" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=http://test.example.com -Pmultica.googleServerClientId=public-client

# Syntactic checks cover all of 127/8 plus IPv6 loopback and IPv4-mapped/compatible forms.
# No hostname lookup or DNS result participates in this build-safety decision.
for url in \
  https://localhost \
  https://localhost. \
  https://127.0.0.1 \
  https://127.0.0.0 \
  https://127.0.0.2 \
  https://127.255.255.255 \
  'https://[::1]' \
  'https://[0:0:0:0:0:0:0:1]' \
  'https://[::ffff:127.0.0.1]' \
  'https://[::ffff:7f00:1]' \
  'https://[::127.0.0.1]'; do
  expect_failure "Release builds cannot target a loopback API host" \
    -Pmultica.fixtureMode=false \
    -Pmultica.apiBaseUrl="$url" \
    -Pmultica.googleServerClientId=public-client
done
expect_success -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://test.example.com -Pmultica.googleServerClientId=public-client

grep -q 'android:usesCleartextTraffic="false"' "$ROOT/app/src/main/AndroidManifest.xml"
printf 'Build-mode gates: PASS (%d negative, %d positive); manifest cleartext disabled\n' "$PASSED_NEGATIVE" "$PASSED_POSITIVE"
