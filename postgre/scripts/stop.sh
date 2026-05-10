#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-exercises-postgre}"

if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
  podman rm -f "$CONTAINER_NAME"
  echo "Removed container '$CONTAINER_NAME'."
else
  echo "No container named '$CONTAINER_NAME'."
fi
