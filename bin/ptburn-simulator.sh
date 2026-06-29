#!/usr/bin/env bash
set -euo pipefail

# Tiny PTBurn simulator:
# Watches a jobs folder for *.JRQ/*.jrq and moves jobs through PTBurn-like
# states: .QRJ -> .INP -> .DON (or .ERR).
# Optionally writes PTBurn-style status files so PTPublish can validate them.
# This is useful when PTBurn/Primera software is not installed.

JOBS_DIR="${1:-./ptburn/jobs}"
STATUS_DIR="${STATUS_DIR:-./ptburn/status}"
POLL_SECONDS="${POLL_SECONDS:-1}"
COMPLETE_DELAY_SECONDS="${COMPLETE_DELAY_SECONDS:-2}"
TRANSITION_DELAY_SECONDS="${TRANSITION_DELAY_SECONDS:-$COMPLETE_DELAY_SECONDS}"
VERBOSE="${VERBOSE:-1}"
RESPONSE_MODE="${RESPONSE_MODE:-don}"
CREATE_STATUS_FILES="${CREATE_STATUS_FILES:-1}"
ROBOT_NAME="${ROBOT_NAME:-Disc Publisher XRP}"
SYSTEM_STATUS_FILE="${SYSTEM_STATUS_FILE:-SystemStatus.txt}"
IN_PROGRESS_STATE="${IN_PROGRESS_STATE:-5}"
COMPLETE_STATE="${COMPLETE_STATE:-10}"
FAILED_STATE="${FAILED_STATE:-15}"
ERROR_NUMBER="${ERROR_NUMBER:-9001}"
ERROR_TEXT="${ERROR_TEXT:-Simulated PTBurn error}"

RESPONSE_MODE="$(echo "$RESPONSE_MODE" | tr '[:upper:]' '[:lower:]')"
if [[ "$RESPONSE_MODE" != "don" && "$RESPONSE_MODE" != "err" ]]; then
  echo "ERROR: RESPONSE_MODE must be 'don' or 'err' (got '$RESPONSE_MODE')" >&2
  exit 1
fi

if [[ ! -d "$JOBS_DIR" ]]; then
  echo "ERROR: jobs directory not found: $JOBS_DIR" >&2
  exit 1
fi

mkdir -p "$STATUS_DIR"

log() {
  if [[ "$VERBOSE" == "1" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  fi
}

log "PTBurn simulator started"
log "Watching: $JOBS_DIR"
log "Status dir: $STATUS_DIR"
log "Poll interval: ${POLL_SECONDS}s, transition delay: ${TRANSITION_DELAY_SECONDS}s"
log "Response mode: $RESPONSE_MODE"

write_system_status() {
  cat > "$STATUS_DIR/$SYSTEM_STATUS_FILE" <<EOF
[RobotList]
$ROBOT_NAME=Ready
EOF
}

write_robot_status() {
  local job_id="$1"
  local state="$2"
  local error_number="$3"
  local error_text="$4"

  cat > "$STATUS_DIR/$ROBOT_NAME.txt" <<EOF
[$job_id]
CurrentStatusState=$state
JobErrorNumber=$error_number
JobErrorString=$error_text
EOF
}

if [[ "$CREATE_STATUS_FILES" == "1" ]]; then
  write_system_status
fi

sleep_transition_delay() {
  if [[ "$TRANSITION_DELAY_SECONDS" != "0" ]]; then
    sleep "$TRANSITION_DELAY_SECONDS"
  fi
}

transition_file() {
  local from="$1"
  local to="$2"
  local state_label="$3"

  if [[ ! -f "$from" ]]; then
    log "Skipping transition to ${state_label}: source missing ($(basename "$from"))"
    return 1
  fi

  mv "$from" "$to"
  log "State ${state_label}: $(basename "$to")"
}

while true; do
  shopt -s nullglob
  for jrq in "$JOBS_DIR"/*.JRQ "$JOBS_DIR"/*.jrq; do
    filename="$(basename "$jrq")"
    job_name="${filename%.*}"
    qrj="$JOBS_DIR/${job_name}.QRJ"
    inp="$JOBS_DIR/${job_name}.INP"
    don="$JOBS_DIR/${job_name}.DON"
    err="$JOBS_DIR/${job_name}.ERR"

    # Skip if already terminal.
    if [[ -f "$don" || -f "$err" ]]; then
      continue
    fi

    log "Detected JRQ: $filename"

    transition_file "$jrq" "$qrj" "QRJ" || continue
    sleep_transition_delay

    transition_file "$qrj" "$inp" "INP" || continue
    if [[ "$CREATE_STATUS_FILES" == "1" ]]; then
      write_robot_status "$job_name" "$IN_PROGRESS_STATE" "" ""
    fi
    sleep_transition_delay

    if [[ "$CREATE_STATUS_FILES" == "1" ]]; then
      if [[ "$RESPONSE_MODE" == "err" ]]; then
        write_robot_status "$job_name" "$FAILED_STATE" "$ERROR_NUMBER" "$ERROR_TEXT"
      else
        write_robot_status "$job_name" "$COMPLETE_STATE" "" ""
      fi
    fi

    if [[ "$RESPONSE_MODE" == "err" ]]; then
      transition_file "$inp" "$err" "ERR" || continue
    else
      transition_file "$inp" "$don" "DON" || continue
    fi
  done
  shopt -u nullglob

  sleep "$POLL_SECONDS"
done
