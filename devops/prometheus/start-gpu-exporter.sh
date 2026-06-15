#!/bin/sh
# NVIDIA GPU metrics per Podman container (exercises-*). Requires nvidia-smi on the Podman host VM.
set -eu

PODMAN="${PODMAN:-podman}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
EXPORTER_DIR="${SCRIPT_DIR}/exercises-gpu-exporter"

if ! command -v nvidia-smi >/dev/null 2>&1; then
  echo "nvidia-smi not found — GPU exporter needs an NVIDIA driver on the Podman host." >&2
  exit 1
fi

if ! $PODMAN network exists exercises 2>/dev/null; then
  echo "Network 'exercises' not found — start the stack first: podman compose up -d" >&2
  exit 1
fi

SOCKET="$($PODMAN info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null || true)"
if [ -z "$SOCKET" ] || [ ! -S "$SOCKET" ]; then
  if [ -S /run/podman/podman.sock ]; then
    SOCKET=/run/podman/podman.sock
  elif [ -S /run/user/1000/podman/podman.sock ]; then
    SOCKET=/run/user/1000/podman/podman.sock
  else
    echo "Podman socket not found." >&2
    exit 1
  fi
fi

python3 -m pip install -q -r "${EXPORTER_DIR}/requirements.txt" 2>/dev/null || true

$PODMAN rm -f exercises-gpu-exporter 2>/dev/null || true

echo "Using Podman socket: $SOCKET"

$PODMAN run -d --replace --name exercises-gpu-exporter --network exercises \
  -p 9066:9066 \
  -e "PODMAN_SOCKET=/run/podman/podman.sock" \
  -v "${SOCKET}:/run/podman/podman.sock" \
  -v "${EXPORTER_DIR}:/app:ro" \
  -v /proc:/proc:ro \
  --device nvidia.com/gpu=all \
  --security-opt label=type:container_runtime_t \
  docker.io/nvidia/cuda:12.0.0-base-ubuntu22.04 \
  bash -c "apt-get update -qq && apt-get install -y -qq python3 python3-pip >/dev/null && pip3 install -q -r /app/requirements.txt && exec python3 /app/exporter.py --listen 0.0.0.0:9066 --podman-socket /run/podman/podman.sock --proc-root /proc"

echo "exercises-gpu-exporter on http://127.0.0.1:9066/metrics (Prometheus job exercises-gpu)."
echo "Reload Prometheus: curl -X POST http://127.0.0.1:9090/-/reload"
