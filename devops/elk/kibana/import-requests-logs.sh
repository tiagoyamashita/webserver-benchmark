#!/usr/bin/env bash
# Provision Kibana saved objects for HTTP + Postgres log correlation.
# Run from repo root after docker-compose.observability.yml is up.

set -euo pipefail

KIBANA_URL="${KIBANA_URL:-http://127.0.0.1:5601}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/saved_objects"
BUNDLE="${ROOT}/requests-logs"

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

import_object "index-pattern" "logstash-data-view" "${ROOT}/index-pattern.json"
import_object "search" "requests-logs-http-search" "${BUNDLE}/search-http-requests.json"
import_object "search" "requests-logs-sql-search" "${BUNDLE}/search-sql-crud.json"
import_object "search" "requests-logs-postgres-stream-search" "${BUNDLE}/search-postgres-stream.json"
import_object "search" "requests-logs-http-handlers" "${BUNDLE}/search-http-handlers.json"
import_object "dashboard" "exercises-requests-logs-kibana" "${BUNDLE}/dashboard.json"

echo "Open: ${KIBANA_URL}/app/dashboards#/view/exercises-requests-logs-kibana"
