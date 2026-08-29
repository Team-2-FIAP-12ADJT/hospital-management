#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
CONNECTORS_DIR="${CONNECTORS_DIR:-/connectors}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"

log() {
  local level="$1"
  shift
  printf '%s %s\n' "[$level]" "$*"
}

log_error() {
  log "ERROR" "$*" >&2
}

wait_for_connect() {
  local deadline=$(( $(date +%s) + MAX_WAIT_SECONDS ))

  while true; do
    if curl --silent --show-error --fail --max-time 5 "${CONNECT_URL}/connectors" > /dev/null 2>&1; then
      log "SUCCESS" "Debezium Connect is reachable at ${CONNECT_URL}/connectors"
      return 0
    fi

    local now
    now=$(date +%s)
    if [ "$now" -ge "$deadline" ]; then
      log_error "Timeout waiting for Debezium Connect at ${CONNECT_URL}/connectors after ${MAX_WAIT_SECONDS}s; endpoint is unreachable."
      exit 1
    fi

    log "INFO" "Waiting for Debezium Connect at ${CONNECT_URL}/connectors (${now} / ${deadline}s)"
    sleep 2
  done
}

process_connectors() {
  shopt -s nullglob
  local files=("${CONNECTORS_DIR}"/*.json)

  if [ "${#files[@]}" -eq 0 ]; then
    log "INFO" "No connector configurations found in ${CONNECTORS_DIR}; Registrar finished successfully."
    exit 0
  fi

  local processed_count=0
  local failed_count=0
  local failed_names=()

  for file in "${files[@]}"; do
    local file_name connector_name payload http_status response_body
    file_name=$(basename "$file")
    processed_count=$(( processed_count + 1 ))

    if ! jq empty "$file" > /dev/null 2>&1; then
      failed_count=$(( failed_count + 1 ))
      failed_names+=("$file_name")
      log_error "Invalid JSON in ${file_name}; jq validation failed."
      continue
    fi

    connector_name="$(jq -r '.name // empty' "$file")"
    if [ -z "${connector_name}" ]; then
      connector_name="${file_name%.json}"
    fi

    payload="$(jq -c 'if has("config") then .config else . end' "$file")"

    # set -e exits the whole script on a curl transport failure (DNS, reset,
    # timeout) unless the call sits in an if-condition; without this guard a
    # transport error skips this connector's own log line, the failure
    # counter, and every connector still left in the loop.
    if ! http_status="$(curl --silent --show-error --output /tmp/debezium_connector_response_body --write-out '%{http_code}' --header 'Content-Type: application/json' --request PUT --data "${payload}" "${CONNECT_URL}/connectors/${connector_name}/config")"; then
      failed_count=$(( failed_count + 1 ))
      failed_names+=("${connector_name}")
      log_error "Connector '${connector_name}' request failed (curl transport error, no HTTP response received)."
      continue
    fi

    response_body="$(cat /tmp/debezium_connector_response_body)"

    if [ "${http_status}" = "200" ] || [ "${http_status}" = "201" ]; then
      log "SUCCESS" "Connector '${connector_name}' registered successfully (HTTP ${http_status})."
    else
      failed_count=$(( failed_count + 1 ))
      failed_names+=("${connector_name}")
      log_error "Connector '${connector_name}' failed with HTTP ${http_status}; response body: ${response_body}"
    fi
  done

  if [ "${failed_count}" -gt 0 ]; then
    log_error "Connector registration completed with failures: ${failed_count}/${processed_count} connector(s) failed (${failed_names[*]})."
    exit 1
  fi

  log "SUCCESS" "Processed ${processed_count} connector(s) successfully."
  exit 0
}

main() {
  log "INFO" "Starting Debezium connector registrar."
  log "INFO" "Using CONNECT_URL=${CONNECT_URL} and CONNECTORS_DIR=${CONNECTORS_DIR}."
  wait_for_connect
  process_connectors
}

main "$@"
