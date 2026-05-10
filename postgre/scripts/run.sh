#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-exercises-postgre}"
IMAGE="${POSTGRES_IMAGE:-docker.io/library/postgres:16}"
PG_USER="${POSTGRES_USER:-postgres}"
PG_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
PG_DB="${POSTGRES_DB:-demo}"
HOST_PORT="${HOST_PORT:-5432}"

if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
  echo "Container '$CONTAINER_NAME' already exists. Remove it first: ./scripts/stop.sh"
  exit 1
fi

podman volume create exercises-postgre-data >/dev/null 2>&1 || true

podman run -d \
  --name "$CONTAINER_NAME" \
  -e "POSTGRES_USER=$PG_USER" \
  -e "POSTGRES_PASSWORD=$PG_PASSWORD" \
  -e "POSTGRES_DB=$PG_DB" \
  -p "${HOST_PORT}:5432" \
  -v exercises-postgre-data:/var/lib/postgresql/data \
  "$IMAGE"

echo "PostgreSQL listening on localhost:${HOST_PORT} (database: $PG_DB, user: $PG_USER)."
echo "Stop with: ./scripts/stop.sh"
