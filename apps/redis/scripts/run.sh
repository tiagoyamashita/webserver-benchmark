#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-exercises-redis}"
IMAGE="${REDIS_IMAGE:-docker.io/library/redis:7-alpine}"
HOST_PORT="${HOST_PORT:-6379}"

if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
  echo "Container '$CONTAINER_NAME' already exists. Remove it first: ./scripts/stop.sh"
  exit 1
fi

podman volume create exercises-redis-data >/dev/null 2>&1 || true

podman run -d \
  --name "$CONTAINER_NAME" \
  -p "${HOST_PORT}:6379" \
  -v exercises-redis-data:/data \
  "$IMAGE" \
  redis-server --appendonly yes

echo "Redis listening on localhost:${HOST_PORT}."
echo "Stop with: ./scripts/stop.sh"
