#!/usr/bin/env bash
# Provision Kibana data view, saved searches, dashboards, and default landing route.
# Used by the kibana-setup Compose service and for manual runs after stack start.

set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://127.0.0.1:5601}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/saved_objects"
DEFAULT_ROUTE="${KIBANA_DEFAULT_ROUTE:-/app/dashboards#/view/exercises-requests-logs-kibana}"
DEFAULT_DATA_VIEW_ID="${KIBANA_DEFAULT_DATA_VIEW_ID:-logstash-data-view}"
WAIT_SECONDS="${KIBANA_PROVISION_WAIT_SECONDS:-180}"
NDJSON="${SCRIPT_DIR}/exercises-kibana.ndjson"

wait_for_kibana() {
  local deadline=$((SECONDS + WAIT_SECONDS))
  echo "Waiting for Kibana at ${KIBANA_URL} (up to ${WAIT_SECONDS}s) ..."
  while ((SECONDS < deadline)); do
    if curl -sf "${KIBANA_URL}/api/status" >/dev/null 2>&1; then
      local level
      level="$(curl -sf "${KIBANA_URL}/api/status" | sed -n 's/.*"level":"\([^"]*\)".*/\1/p' | head -n1)"
      if [[ "${level}" == "available" ]]; then
        echo "Kibana is available."
        return 0
      fi
    fi
    sleep 2
  done
  echo "Kibana did not become available within ${WAIT_SECONDS}s." >&2
  return 1
}

set_default_data_view() {
  echo "POST data_views/default (${DEFAULT_DATA_VIEW_ID}) ..."
  curl -sS -X POST "${KIBANA_URL}/api/data_views/default" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    --data-binary "@${SCRIPT_DIR}/saved_objects/default-data-view.json"
  echo
}

set_default_route() {
  local version
  version="$(curl -sf "${KIBANA_URL}/api/status" | sed -n 's/.*"number":"\([^"]*\)".*/\1/p' | head -n1)"
  if [[ -z "${version}" ]]; then
    version="8.15.5"
  fi
  echo "PUT config/${version} defaultRoute=${DEFAULT_ROUTE} ..."
  curl -sS -X PUT "${KIBANA_URL}/api/saved_objects/config/${version}?overwrite=true" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    --data-binary "@${SCRIPT_DIR}/saved_objects/config.json"
  echo
}

wait_for_kibana

python3 "${SCRIPT_DIR}/build-dashboards.py" || python "${SCRIPT_DIR}/build-dashboards.py"
python3 "${SCRIPT_DIR}/build-ndjson.py" || python "${SCRIPT_DIR}/build-ndjson.py"

echo "POST saved_objects/_import (compatibilityMode) ..."
curl -sS -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true&compatibilityMode=true" \
  -H "kbn-xsrf: true" \
  --form "file=@${NDJSON}"
echo

set_default_data_view
set_default_route

echo "Kibana preset ready: ${KIBANA_URL}${DEFAULT_ROUTE}"
