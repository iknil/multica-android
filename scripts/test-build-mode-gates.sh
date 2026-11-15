#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE=("$ROOT/scripts/gradle-verified.sh" help --offline)

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
}

expect_failure "multica.fixtureMode must be true or false" -Pmultica.fixtureMode=yes
expect_failure "Non-fixture builds cannot use a .invalid API host" -Pmultica.fixtureMode=false -Pmultica.googleServerClientId=public-client
expect_failure "Non-fixture builds require multica.googleServerClientId" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://test.example.com

# Task selection is part of the release gate; failures occur during configuration, before compilation.
GRADLE=("$ROOT/scripts/gradle-verified.sh" assembleRelease --offline)
expect_failure "Release builds must set -Pmultica.fixtureMode=false"
expect_failure "Release builds require an HTTPS multica.apiBaseUrl" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=http://test.example.com -Pmultica.googleServerClientId=public-client
expect_failure "Release builds cannot target a loopback API host" -Pmultica.fixtureMode=false -Pmultica.apiBaseUrl=https://localhost -Pmultica.googleServerClientId=public-client

printf 'Build-mode negative gates: PASS\n'
