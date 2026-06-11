#!/usr/bin/env bash
# Provision Kibana saved objects for log pipeline monitoring.
# Run from repo root after docker-compose.observability.yml is up.

set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://127.0.0.1:5601}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR="${SCRIPT_DIR}/saved_objects"

import_object() {
  local type="$1"
  local id="$2"
  local file="$3"
  echo "POST ${type}/${id} ..."
  curl -sS -X POST "${KIBANA_URL}/api/saved_objects/${type}/${id}?overwrite=true" \
    -H "kbn-xsrf: true" \
    -H "Content-Type: application/json" \
    --data-binary "@${file}"
  echo
}

import_object "index-pattern" "logstash-data-view" "${DIR}/index-pattern.json"
import_object "search" "log-pipeline-all-search" "${DIR}/search-all.json"
import_object "search" "log-pipeline-errors-search" "${DIR}/search-errors.json"
import_object "dashboard" "exercises-log-pipeline-kibana" "${DIR}/dashboard.json"

echo "Open: ${KIBANA_URL}/app/dashboards#/view/exercises-log-pipeline-kibana"
echo "HTTP + Postgres correlation: devops/elk/kibana/import-requests-logs.sh"
