#!/usr/bin/env bash
set -euo pipefail

readonly DEFAULT_IMAGE='redroid/redroid@sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb'
readonly OWNER_LABEL='com.multica.redroid.verify.owner'

usage() {
  cat <<'USAGE'
Usage: scripts/verify-redroid-arm64.sh [IMAGE@sha256:DIGEST]

Pulls a digest-pinned linux/arm64 image, performs two bounded cold boots,
and always removes only containers carrying this run's ownership label.

Environment:
  BOOT_TIMEOUT_SECONDS         Per-boot timeout, 30..900 (default: 240)
  COLD_BOOT_RUNS               Fresh-container boots, 1..5 (default: 2)
  DOCKER_TIMEOUT_SECONDS       Docker operation timeout, 1..300 (default: 30)
  DOCKER_PULL_TIMEOUT_SECONDS  Docker pull timeout, 1..3600 (default: 900)
  EVIDENCE_ROOT                Parent for mode-0700 evidence directory (default: pwd)

The script binds ADB to a Docker-assigned 127.0.0.1 port and uses a
privileged container. Run only on an isolated local validation host.
Evidence may contain device/package/network details; review before sharing.
USAGE
}

if [[ ${1:-} == '--help' || ${1:-} == '-h' ]]; then
  usage
  exit 0
fi
if (( $# > 1 )); then
  usage >&2
  exit 64
fi

IMAGE=${1:-$DEFAULT_IMAGE}
BOOT_TIMEOUT_SECONDS=${BOOT_TIMEOUT_SECONDS:-240}
COLD_BOOT_RUNS=${COLD_BOOT_RUNS:-2}
DOCKER_TIMEOUT_SECONDS=${DOCKER_TIMEOUT_SECONDS:-30}
DOCKER_PULL_TIMEOUT_SECONDS=${DOCKER_PULL_TIMEOUT_SECONDS:-900}
EVIDENCE_ROOT=${EVIDENCE_ROOT:-$PWD}

[[ $IMAGE =~ ^redroid/redroid@sha256:[0-9a-f]{64}$ ]] || {
  echo 'ERROR: image must be redroid/redroid pinned by a full sha256 digest' >&2
  exit 64
}
[[ $BOOT_TIMEOUT_SECONDS =~ ^[0-9]+$ ]] &&
  (( BOOT_TIMEOUT_SECONDS >= 30 && BOOT_TIMEOUT_SECONDS <= 900 )) || {
  echo 'ERROR: BOOT_TIMEOUT_SECONDS must be an integer from 30 to 900' >&2
  exit 64
}
[[ $COLD_BOOT_RUNS =~ ^[0-9]+$ ]] &&
  (( COLD_BOOT_RUNS >= 1 && COLD_BOOT_RUNS <= 5 )) || {
  echo 'ERROR: COLD_BOOT_RUNS must be an integer from 1 to 5' >&2
  exit 64
}
[[ $DOCKER_TIMEOUT_SECONDS =~ ^[0-9]+$ ]] &&
  (( DOCKER_TIMEOUT_SECONDS >= 1 && DOCKER_TIMEOUT_SECONDS <= 300 )) || {
  echo 'ERROR: DOCKER_TIMEOUT_SECONDS must be an integer from 1 to 300' >&2
  exit 64
}
[[ $DOCKER_PULL_TIMEOUT_SECONDS =~ ^[0-9]+$ ]] &&
  (( DOCKER_PULL_TIMEOUT_SECONDS >= 1 && DOCKER_PULL_TIMEOUT_SECONDS <= 3600 )) || {
  echo 'ERROR: DOCKER_PULL_TIMEOUT_SECONDS must be an integer from 1 to 3600' >&2
  exit 64
}
[[ -d $EVIDENCE_ROOT && -w $EVIDENCE_ROOT ]] || {
  echo "ERROR: EVIDENCE_ROOT is not a writable directory: $EVIDENCE_ROOT" >&2
  exit 73
}

for command in adb docker timeout; do
  command -v "$command" >/dev/null || {
    echo "ERROR: required command not found: $command" >&2
    exit 69
  }
done

docker_cmd() {
  timeout --foreground "${DOCKER_TIMEOUT_SECONDS}s" docker "$@"
}

docker_pull_cmd() {
  timeout --foreground "${DOCKER_PULL_TIMEOUT_SECONDS}s" docker "$@"
}

docker_cmd info >/dev/null

random_id=$(tr -d '-' </proc/sys/kernel/random/uuid)
[[ $random_id =~ ^[0-9a-f]{32}$ ]] || {
  echo 'ERROR: failed to obtain a high-entropy run id' >&2
  exit 70
}
readonly RUN_ID=$random_id
readonly OWNER_VALUE="redroid-arm64-$RUN_ID"
readonly EVIDENCE_DIR="$EVIDENCE_ROOT/redroid-evidence-$RUN_ID"
mkdir -m 0700 "$EVIDENCE_DIR"
printf '%s\n' "$IMAGE" >"$EVIDENCE_DIR/requested-image.txt"

cid=''
container_name=''
serial=''

owns_container() {
  local actual_owner
  [[ -n $cid ]] || return 1
  actual_owner=$(docker_cmd inspect --format "{{ index .Config.Labels \"$OWNER_LABEL\" }}" "$cid" 2>/dev/null) || return 1
  [[ $actual_owner == "$OWNER_VALUE" ]]
}

cleanup_container() {
  if [[ -n $serial ]]; then
    timeout 5s adb disconnect "$serial" >/dev/null 2>&1 || true
    serial=''
  fi
  if [[ -n $cid ]]; then
    if ! docker_cmd inspect "$cid" >/dev/null 2>&1; then
      echo "WARNING: could not inspect container $cid within the Docker timeout; refusing unverified cleanup" >&2
      return 1
    fi
    if ! owns_container; then
      echo "WARNING: refusing to remove container $cid: ownership label mismatch or inspection timeout" >&2
      return 1
    fi
    if ! docker_cmd rm -f "$cid" >/dev/null; then
      echo "WARNING: timed out or failed while removing owned container $cid" >&2
      return 1
    fi
  fi
  cid=''
  container_name=''
}

on_exit() {
  local rc=$?
  trap - EXIT INT TERM
  if ! cleanup_container; then
    echo 'WARNING: automatic cleanup was refused; inspect the cid above manually' >&2
    (( rc == 0 )) && rc=1
  fi
  exit "$rc"
}

on_signal() {
  local rc=$1
  trap - EXIT INT TERM
  cleanup_container || echo 'WARNING: signal cleanup was refused; inspect the cid above manually' >&2
  exit "$rc"
}

trap on_exit EXIT
trap 'on_signal 130' INT
trap 'on_signal 143' TERM

capture_evidence() {
  local run_dir=$1
  mkdir -p "$run_dir"
  date --iso-8601=seconds >"$run_dir/captured-at.txt" 2>/dev/null || true
  if [[ -n $cid ]] && docker_cmd inspect "$cid" >/dev/null 2>&1; then
    docker_cmd inspect "$cid" >"$run_dir/docker-inspect.json" 2>"$run_dir/docker-inspect.stderr" || true
    docker_cmd logs --timestamps "$cid" >"$run_dir/docker.log" 2>&1 || true
    docker_cmd exec "$cid" getprop >"$run_dir/docker-getprop.txt" 2>&1 || true
    docker_cmd exec "$cid" logcat -b all -d -v threadtime >"$run_dir/docker-logcat.txt" 2>&1 || true
    docker_cmd exec "$cid" dmesg >"$run_dir/docker-dmesg.txt" 2>&1 || true
    docker_cmd exec "$cid" ps -A >"$run_dir/docker-ps.txt" 2>&1 || true
  fi
  if [[ -n $serial ]]; then
    timeout 5s adb -s "$serial" get-state >"$run_dir/adb-state.txt" 2>&1 || true
    timeout 15s adb -s "$serial" shell getprop >"$run_dir/adb-getprop.txt" 2>&1 || true
    timeout 20s adb -s "$serial" logcat -b all -d -v threadtime >"$run_dir/adb-logcat.txt" 2>&1 || true
  fi
  # Host dmesg commonly requires extra permission; preserve the error as evidence.
  timeout 10s dmesg --ctime >"$run_dir/host-dmesg.txt" 2>&1 || true
}

verify_properties() {
  local bootanim zygote abilist machine
  bootanim=$(timeout 5s adb -s "$serial" shell getprop init.svc.bootanim | tr -d '\r')
  zygote=$(timeout 5s adb -s "$serial" shell getprop ro.zygote | tr -d '\r')
  abilist=$(timeout 5s adb -s "$serial" shell getprop ro.product.cpu.abilist | tr -d '\r')
  machine=$(timeout 5s adb -s "$serial" shell uname -m | tr -d '\r')

  [[ $zygote == zygote64 ]] || { echo "ERROR: ro.zygote=$zygote" >&2; return 1; }
  [[ $abilist == arm64-v8a ]] || { echo "ERROR: ro.product.cpu.abilist=$abilist" >&2; return 1; }
  [[ $machine == aarch64 ]] || { echo "ERROR: uname -m=$machine" >&2; return 1; }
  # Empty is valid when boot animation is disabled; any active/non-stopped value is not.
  [[ -z $bootanim || $bootanim == stopped ]] || {
    echo "ERROR: init.svc.bootanim=$bootanim" >&2
    return 1
  }
  if ! timeout 15s adb -s "$serial" shell pm list packages >/dev/null; then
    echo 'ERROR: Package Manager is unavailable' >&2
    return 1
  fi
  if ! timeout 5s adb -s "$serial" shell pidof system_server >/dev/null; then
    echo 'ERROR: system_server is unavailable' >&2
    return 1
  fi
}

wait_for_boot() {
  local deadline state boot_completed
  deadline=$(( $(date +%s) + BOOT_TIMEOUT_SECONDS ))
  while (( $(date +%s) < deadline )); do
    state=$(timeout 5s adb -s "$serial" get-state 2>/dev/null || true)
    if [[ $state == device ]]; then
      boot_completed=$(timeout 5s adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
      if [[ $boot_completed == 1 ]] && verify_properties; then
        return 0
      fi
    fi
    sleep 2
  done
  echo "ERROR: boot did not satisfy all gates within ${BOOT_TIMEOUT_SECONDS}s (last ADB state: ${state:-unavailable})" >&2
  return 1
}

echo "Pulling unverified candidate by digest: $IMAGE"
docker_pull_cmd pull --platform linux/arm64 "$IMAGE"
architecture=$(docker_cmd image inspect "$IMAGE" --format '{{.Architecture}}')
[[ $architecture == arm64 ]] || {
  echo "ERROR: pulled image architecture is $architecture, expected arm64" >&2
  exit 1
}
docker_cmd image inspect "$IMAGE" >"$EVIDENCE_DIR/image-inspect.json"

for (( run=1; run<=COLD_BOOT_RUNS; run++ )); do
  run_dir="$EVIDENCE_DIR/cold-boot-$run"
  mkdir -m 0700 "$run_dir"
  container_name="redroid-arm64-${RUN_ID:0:20}-$run"

  cidfile="$run_dir/docker.cid"
  run_output=''
  run_rc=0
  run_output=$(docker_cmd run -d --name "$container_name" \
    --cidfile "$cidfile" \
    --label "$OWNER_LABEL=$OWNER_VALUE" \
    --privileged \
    -p 127.0.0.1::5555 \
    "$IMAGE" androidboot.use_memfd=true) || run_rc=$?
  cid=$run_output
  if [[ ! $cid =~ ^[0-9a-f]{12,64}$ ]] && [[ -r $cidfile ]]; then
    read -r cid <"$cidfile" || true
  fi
  if [[ ! $cid =~ ^[0-9a-f]{12,64}$ ]]; then
    # docker run may time out after the daemon created the container but before
    # stdout reached us. Recover only this run's high-entropy name + label.
    recovered=$(docker_cmd container inspect \
      --format "{{.Id}} {{index .Config.Labels \"$OWNER_LABEL\"}}" \
      "$container_name" 2>/dev/null || true)
    recovered_cid=${recovered%% *}
    recovered_owner=${recovered#* }
    if [[ $recovered_cid =~ ^[0-9a-f]{12,64}$ ]] && [[ $recovered_owner == "$OWNER_VALUE" ]]; then
      cid=$recovered_cid
    fi
  fi
  if (( run_rc != 0 )) || [[ ! $cid =~ ^[0-9a-f]{12,64}$ ]]; then
    echo "ERROR: docker run failed or timed out (status $run_rc); recovered cid: ${cid:-none}" >&2
    capture_evidence "$run_dir"
    exit 1
  fi
  owns_container || {
    echo "ERROR: ownership label missing from container $cid" >&2
    capture_evidence "$run_dir"
    exit 1
  }

  port=''
  for _ in {1..15}; do
    port=$(docker_cmd port "$cid" 5555/tcp 2>/dev/null | awk -F: '$1 == "127.0.0.1" {print $NF; exit}')
    [[ $port =~ ^[0-9]+$ ]] && (( port >= 1 && port <= 65535 )) && break
    port=''
    sleep 1
  done
  [[ $port =~ ^[0-9]+$ ]] && (( port >= 1 && port <= 65535 )) || {
    echo 'ERROR: Docker did not publish a valid loopback ADB port' >&2
    capture_evidence "$run_dir"
    exit 1
  }
  serial="127.0.0.1:$port"
  printf '%s\n' "$cid" >"$run_dir/cid.txt"
  printf '%s\n' "$serial" >"$run_dir/adb-serial.txt"

  timeout 10s adb connect "$serial" >"$run_dir/adb-connect.txt" 2>&1 || true
  if ! wait_for_boot; then
    capture_evidence "$run_dir"
    echo "Evidence captured in: $run_dir" >&2
    exit 1
  fi

  capture_evidence "$run_dir"
  printf 'Cold boot %d/%d passed all automated gates.\n' "$run" "$COLD_BOOT_RUNS"
  cleanup_container
  sleep 2
done

echo "PASS: $COLD_BOOT_RUNS fresh-container cold boots passed."
echo "Evidence: $EVIDENCE_DIR"
echo 'This validates only the redroid software runtime; it does not validate product behavior, hardware-backed KeyStore, TEE, or StrongBox.'
