#!/usr/bin/env bash
# Provision Kibana data view "Logs by tiago" and default Discover route.

set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://127.0.0.1:5601}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/saved_objects"
DEFAULT_ROUTE="${KIBANA_DEFAULT_ROUTE:-/app/discover#/view/logs-by-tiago-discover?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-24h,to:now))}"
DEFAULT_DATA_VIEW_ID="${KIBANA_DEFAULT_DATA_VIEW_ID:-logs-by-tiago}"
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
  echo "PUT config/${version} defaultRoute=Discover (Logs by tiago) ..."
  curl -sS -X PUT "${KIBANA_URL}/api/saved_objects/config/${version}?overwrite=true" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    --data-binary "@${SCRIPT_DIR}/saved_objects/config.json"
  echo
}

wait_for_kibana

python3 "${SCRIPT_DIR}/build-index-pattern.py" || python "${SCRIPT_DIR}/build-index-pattern.py"
python3 "${SCRIPT_DIR}/build-ndjson.py" || python "${SCRIPT_DIR}/build-ndjson.py"

echo "DELETE legacy data views (if present) ..."
curl -sS -X DELETE "${KIBANA_URL}/api/data_views/data_view/logstash-data-view" \
  -H "kbn-xsrf: true" || true
curl -sS -X DELETE "${KIBANA_URL}/api/data_views/data_view/57d8bc58-117d-4a14-af02-4a8e4369a633" \
  -H "kbn-xsrf: true" || true
echo

echo "POST saved_objects/_import (compatibilityMode) ..."
curl -sS -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true&compatibilityMode=true" \
  -H "kbn-xsrf: true" \
  --form "file=@${NDJSON}"
echo

echo "VERIFY data view ${DEFAULT_DATA_VIEW_ID} ..."
curl -sS -f "${KIBANA_URL}/api/data_views/data_view/${DEFAULT_DATA_VIEW_ID}" \
  -H "kbn-xsrf: true" | python3 -c "import sys,json; d=json.load(sys.stdin); print('data view OK:', d['data_view']['name'], d['data_view']['title'])" \
  || python -c "import sys,json; d=json.load(sys.stdin); print('data view OK:', d['data_view']['name'], d['data_view']['title'])"
echo

echo "VERIFY saved search logs-by-tiago-discover ..."
curl -sS -f "${KIBANA_URL}/api/saved_objects/search/logs-by-tiago-discover" \
  -H "kbn-xsrf: true" | python3 -c "import sys,json; d=json.load(sys.stdin); print('search OK:', d['attributes']['title'], 'columns:', d['attributes']['columns'])" \
  || python -c "import sys,json; d=json.load(sys.stdin); print('search OK:', d['attributes']['title'], 'columns:', d['attributes']['columns'])"
echo

echo "VERIFY dashboard exercises-kafka-logs-kibana ..."
curl -sS -f "${KIBANA_URL}/api/saved_objects/dashboard/exercises-kafka-logs-kibana" \
  -H "kbn-xsrf: true" | python3 -c "import sys,json; d=json.load(sys.stdin); print('dashboard OK:', d['attributes']['title'])" \
  || python -c "import sys,json; d=json.load(sys.stdin); print('dashboard OK:', d['attributes']['title'])"
echo

set_default_data_view
set_default_route

echo "Kibana preset ready: ${KIBANA_URL}/"
echo "Data view: Logs by tiago (${DEFAULT_DATA_VIEW_ID}) - all logs, last 24h"
echo "Kafka dashboard: ${KIBANA_URL}/app/dashboards#/view/exercises-kafka-logs-kibana"
