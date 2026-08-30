#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
CONNECTORS_DIR="${CONNECTORS_DIR:-/connectors}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-60}"
TASK_STATUS_MAX_WAIT_SECONDS="${TASK_STATUS_MAX_WAIT_SECONDS:-30}"

# Kafka Connect topic creation is lazy and only applies when a topic is created on
# first use. If a topic already exists with stale retention, the operator must
# reset the local Compose volumes (docker compose down -v) before changing the
# topic-creation defaults; the registrar intentionally does not add Kafka admin
# tooling just to inspect/alter topic config.

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

wait_for_connector_task_running() {
  local connector_name="$1"
  local connector_file="$2"
  local deadline=$(( $(date +%s) + TASK_STATUS_MAX_WAIT_SECONDS ))
  local status_file="/tmp/${connector_name}_status.json"
  rm -f "${status_file}"

  while true; do
    if curl --silent --show-error --fail --max-time 5 "${CONNECT_URL}/connectors/${connector_name}/status" > "${status_file}" 2>/dev/null; then
      local connector_state task_states
      connector_state="$(jq -r '.connector.state // empty' "${status_file}")"
      task_states="$(jq -r '.tasks // [] | map(.state) | join(",")' "${status_file}")"

      if echo "${task_states}" | tr ',' '\n' | grep -Eq '^(FAILED|DESTROYED)$'; then
        if [ -f "${status_file}" ]; then
          local trace_lines
          trace_lines="$(jq -r '.tasks[]? | select(.state == "FAILED" or .state == "DESTROYED") | (.trace // "<no trace>")' "${status_file}" 2>/dev/null || true)"
          if [ -n "${trace_lines}" ]; then
            log_error "Connector '${connector_name}' task entered terminal failure state. Debezium task trace:"
            while IFS= read -r line; do
              [ -n "${line}" ] && log_error "${line}"
            done <<< "${trace_lines}"
          else
            log_error "Connector '${connector_name}' task entered terminal failure state; status payload: $(jq -c '.' "${status_file}" 2>/dev/null || cat "${status_file}")"
          fi
        fi

        local slot_name
        slot_name="$(jq -r '.config["slot.name"] // empty' "${connector_file}")"
        local slot_desc="${slot_name:-its}"
        local delete_http_status
        delete_http_status="$(curl --silent --show-error --output /tmp/debezium_connector_delete_response_body --write-out '%{http_code}' --request DELETE "${CONNECT_URL}/connectors/${connector_name}" 2>/tmp/debezium_connector_delete_error || true)"

        if [ "${delete_http_status}" = "404" ]; then
          log "INFO" "Connector '${connector_name}' was already absent when cleanup ran; no connector registration remained."
        elif [ -n "${delete_http_status}" ] && [ "${delete_http_status}" != "000" ] && [ "${delete_http_status}" -ge 200 ] && [ "${delete_http_status}" -lt 300 ]; then
          log "WARN" "Deleted failed connector '${connector_name}' from Debezium Connect to avoid leaving a broken registration behind."
        else
          log_error "DELETE request for connector '${connector_name}' could not confirm registration state; registration state could not be confirmed."
        fi

        log_error "WARNING: connector '${connector_name}' failed and its replication slot '${slot_desc}' may still exist in Postgres and requires manual/operator cleanup."
        return 1
      fi

      if [ "${connector_state}" = "RUNNING" ] && [ -n "${task_states}" ] && ! echo "${task_states}" | grep -q "FAILED" && ! echo "${task_states}" | grep -q "UNASSIGNED" && ! echo "${task_states}" | grep -q "PAUSED" && ! echo "${task_states}" | grep -q "DESTROYED"; then
        local task_count
        task_count="$(jq '.tasks | length' "${status_file}")"
        local running_tasks
        running_tasks="$(jq '[.tasks[]? | select(.state == "RUNNING")] | length' "${status_file}")"
        if [ "${task_count}" -gt 0 ] && [ "${running_tasks}" = "${task_count}" ]; then
          log "SUCCESS" "Connector '${connector_name}' task reached RUNNING state."
          return 0
        fi
      fi
    fi

    local now
    now=$(date +%s)
    if [ "${now}" -ge "${deadline}" ]; then
      if [ -f "${status_file}" ]; then
        local trace_lines
        trace_lines="$(jq -r '.tasks[]? | select(.state != "RUNNING") | (.trace // "<no trace>")' "${status_file}" 2>/dev/null || true)"
        if [ -n "${trace_lines}" ]; then
          log_error "Connector '${connector_name}' did not reach RUNNING within ${TASK_STATUS_MAX_WAIT_SECONDS}s. Debezium task trace:"
          while IFS= read -r line; do
            [ -n "${line}" ] && log_error "${line}"
          done <<< "${trace_lines}"
        else
          log_error "Connector '${connector_name}' did not reach RUNNING within ${TASK_STATUS_MAX_WAIT_SECONDS}s. Status payload: $(jq -c '.' "${status_file}" 2>/dev/null || cat "${status_file}")"
        fi
      else
        log_error "Connector '${connector_name}' did not reach RUNNING within ${TASK_STATUS_MAX_WAIT_SECONDS}s and no current status was obtained."
      fi

      local slot_name
      slot_name="$(jq -r '.config["slot.name"] // empty' "${connector_file}")"
      local slot_desc="${slot_name:-its}"
      local delete_http_status
      delete_http_status="$(curl --silent --show-error --output /tmp/debezium_connector_delete_response_body --write-out '%{http_code}' --request DELETE "${CONNECT_URL}/connectors/${connector_name}" 2>/tmp/debezium_connector_delete_error || true)"

      if [ "${delete_http_status}" = "404" ]; then
        log "INFO" "Connector '${connector_name}' was already absent when cleanup ran; no connector registration remained."
      elif [ -n "${delete_http_status}" ] && [ "${delete_http_status}" != "000" ] && [ "${delete_http_status}" -ge 200 ] && [ "${delete_http_status}" -lt 300 ]; then
        log "WARN" "Deleted failed connector '${connector_name}' from Debezium Connect to avoid leaving a broken registration behind."
      else
        log_error "DELETE request for connector '${connector_name}' could not confirm registration state; registration state could not be confirmed."
      fi

      log_error "WARNING: connector '${connector_name}' failed and its replication slot '${slot_desc}' may still exist in Postgres and requires manual/operator cleanup."
      return 1
    fi

    log "INFO" "Waiting for connector '${connector_name}' task to reach RUNNING (${now} / ${deadline}s)"
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
      if ! wait_for_connector_task_running "${connector_name}" "$file"; then
        failed_count=$(( failed_count + 1 ))
        failed_names+=("${connector_name}")
        log_error "Connector '${connector_name}' was registered but did not reach RUNNING; treated as failed."
        continue
      fi
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
