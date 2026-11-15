#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
readonly VERIFY="$SCRIPT_DIR/verify-redroid-arm64.sh"
readonly IMAGE='redroid/redroid@sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb'
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"

cat >"$work/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$MOCK_STATE/docker.calls"
cid=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
case ${1:-} in
  info) [[ $MOCK_CASE != info_block ]] || { exec /bin/sleep 10; }; exit 0 ;;
  pull) exit 0 ;;
  image)
    if [[ ${4:-} == --format ]]; then printf 'arm64\n'; else printf '{}\n'; fi
    ;;
  run)
    cidfile=''
    owner=''
    name=''
    while (($#)); do
      case $1 in
        --cidfile) cidfile=$2; shift 2 ;;
        --label) owner=${2#*=}; shift 2 ;;
        --name) name=$2; shift 2 ;;
        *) shift ;;
      esac
    done
    printf '%s' "$owner" >"$MOCK_STATE/owner"
    printf '%s' "$name" >"$MOCK_STATE/name"
    if [[ $MOCK_CASE != run_block_no_cidfile ]]; then
      printf '%s\n' "$cid" >"$cidfile"
    fi
    : >"$MOCK_STATE/exists"
    if [[ $MOCK_CASE == run_block || $MOCK_CASE == run_block_no_cidfile ]]; then exec /bin/sleep 10; fi
    printf '%s\n' "$cid"
    ;;
  container)
    owner=$(cat "$MOCK_STATE/owner")
    printf '%s %s\n' "$cid" "$owner"
    ;;
  inspect)
    [[ -e $MOCK_STATE/exists ]] || exit 1
    if [[ ${2:-} == --format ]]; then cat "$MOCK_STATE/owner"; printf '\n'; else printf '{}\n'; fi
    ;;
  port) printf '127.0.0.1:43210\n' ;;
  logs|exec) printf 'mock evidence\n' ;;
  rm)
    [[ ${2:-} == -f ]]
    printf '%s\n' "${3:-}" >>"$MOCK_STATE/removed"
    rm -f "$MOCK_STATE/exists"
    ;;
  *) echo "unexpected docker invocation: $*" >&2; exit 97 ;;
esac
MOCK

cat >"$work/bin/adb" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
args=("$@")
if [[ ${args[0]:-} == -s ]]; then args=("${args[@]:2}"); fi
case ${args[0]:-} in
  connect|disconnect) exit 0 ;;
  get-state) printf 'device\n' ;;
  logcat) printf 'mock logcat\n' ;;
  shell)
    case "${args[*]:1}" in
      'getprop sys.boot_completed') printf '1\n' ;;
      'getprop init.svc.bootanim') printf 'stopped\n' ;;
      'getprop ro.zygote') printf 'zygote64\n' ;;
      'getprop ro.product.cpu.abilist') printf 'arm64-v8a\n' ;;
      'uname -m') printf 'aarch64\n' ;;
      'pm list packages') [[ $MOCK_CASE != pm_fail ]] ;;
      'pidof system_server') [[ $MOCK_CASE != system_server_fail ]] && printf '123\n' ;;
      'getprop') printf '[sys.boot_completed]: [1]\n' ;;
      *) echo "unexpected adb shell: ${args[*]:1}" >&2; exit 96 ;;
    esac
    ;;
  *) echo "unexpected adb invocation: ${args[*]}" >&2; exit 95 ;;
esac
MOCK

cat >"$work/bin/date" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} == +%s ]]; then
  n=1000
  [[ ! -e $MOCK_STATE/clock ]] || n=$(cat "$MOCK_STATE/clock")
  printf '%s\n' "$((n + 10))" >"$MOCK_STATE/clock"
  printf '%s\n' "$n"
else
  printf '2026-11-15T00:00:00+00:00\n'
fi
MOCK
cat >"$work/bin/sleep" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
chmod +x "$work/bin/"*

run_negative() {
  local scenario=$1 expected=$2 state
  state="$work/$scenario"
  mkdir "$state"
  set +e
  PATH="$work/bin:$PATH" MOCK_STATE="$state" MOCK_CASE="$scenario" \
    BOOT_TIMEOUT_SECONDS=30 COLD_BOOT_RUNS=1 DOCKER_TIMEOUT_SECONDS=1 \
    DOCKER_PULL_TIMEOUT_SECONDS=1 EVIDENCE_ROOT="$state" \
    "$VERIFY" "$IMAGE" >"$state/stdout" 2>"$state/stderr"
  rc=$?
  set -e
  (( rc != 0 )) || { echo "FAIL: $scenario unexpectedly passed" >&2; exit 1; }
  ! grep -q '^PASS:' "$state/stdout" || { echo "FAIL: $scenario emitted PASS" >&2; exit 1; }
  grep -q "$expected" "$state/stderr" || { cat "$state/stderr" >&2; echo "FAIL: $scenario missed expected error" >&2; exit 1; }
  [[ $(wc -l <"$state/removed") -eq 1 ]] || { echo "FAIL: $scenario did not remove exactly one owned container" >&2; exit 1; }
  grep -q '^a\{64\}$' "$state/removed" || { echo "FAIL: $scenario removed an unexpected cid" >&2; exit 1; }
  printf 'PASS negative: %s exits nonzero, emits no PASS, removes owned cid once\n' "$scenario"
}

run_negative pm_fail 'Package Manager is unavailable'
run_negative system_server_fail 'system_server is unavailable'
run_negative run_block 'docker run failed or timed out'
run_negative run_block_no_cidfile 'docker run failed or timed out'

cidfile=$(find "$work/run_block" -path '*/cold-boot-1/docker.cid' -type f -print -quit)
[[ -n $cidfile && -s $cidfile ]] || {
  echo 'FAIL: timed-out run did not preserve daemon-assigned cid' >&2
  exit 1
}
grep -q '^inspect --format ' "$work/run_block/docker.calls" || {
  echo 'FAIL: timed-out run did not verify the recovered cid ownership label' >&2
  exit 1
}
grep -q '^container inspect ' "$work/run_block_no_cidfile/docker.calls" || {
  echo 'FAIL: timed-out run without cidfile did not recover by unique name and label' >&2
  exit 1
}
printf 'PASS regression: timed-out docker run recovered cidfile or name/label cid and performed bounded cleanup\n'

state="$work/info_block"
mkdir "$state"
start=$SECONDS
set +e
PATH="$work/bin:$PATH" MOCK_STATE="$state" MOCK_CASE=info_block \
  DOCKER_TIMEOUT_SECONDS=1 DOCKER_PULL_TIMEOUT_SECONDS=1 EVIDENCE_ROOT="$state" \
  "$VERIFY" "$IMAGE" >"$state/stdout" 2>"$state/stderr"
rc=$?
set -e
(( rc == 124 )) || { echo "FAIL: blocked docker info returned $rc, expected timeout 124" >&2; exit 1; }
(( SECONDS - start < 4 )) || { echo 'FAIL: blocked docker info was not bounded' >&2; exit 1; }
printf 'PASS regression: blocked docker info was terminated by configurable timeout\n'
