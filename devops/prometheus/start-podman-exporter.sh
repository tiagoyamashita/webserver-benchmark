#!/bin/sh
# Run prometheus-podman-exporter on the Podman host (same podman as compose — usually rootless).
# Joins network "exercises" as "podman-exporter" so Prometheus scrapes podman-exporter:9882.
set -eu

PODMAN="${PODMAN:-podman}"

if ! $PODMAN network exists exercises 2>/dev/null; then
  echo "Network 'exercises' not found — start the stack first: podman compose up -d" >&2
  exit 1
fi

SOCKET="$($PODMAN info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null || true)"
if [ -z "$SOCKET" ] || [ ! -S "$SOCKET" ]; then
  if [ -S /run/podman/podman.sock ]; then
    SOCKET=/run/podman/podman.sock
  elif [ -n "${XDG_RUNTIME_DIR:-}" ] && [ -S "${XDG_RUNTIME_DIR}/podman/podman.sock" ]; then
    SOCKET="${XDG_RUNTIME_DIR}/podman/podman.sock"
  elif [ -S /run/user/1000/podman/podman.sock ]; then
    SOCKET=/run/user/1000/podman/podman.sock
  else
    echo "Podman socket not found. Start the socket service or run: podman info --format '{{.Host.RemoteSocket.Path}}'" >&2
    exit 1
  fi
fi

echo "Using Podman socket: $SOCKET"

$PODMAN rm -f podman-exporter 2>/dev/null || true

# Rootless Podman Machine: keep-id + runtime label (see prometheus-podman-exporter install.md).
$PODMAN run -d --replace --name podman-exporter --network exercises \
  -e "CONTAINER_HOST=unix:///run/podman/podman.sock" \
  -v "${SOCKET}:/run/podman/podman.sock" \
  -p 9882:9882 \
  --userns keep-id:uid=65534 \
  --security-opt label=type:container_runtime_t \
  quay.io/navidys/prometheus-podman-exporter:v1.14.0 \
  --collector.enable-all --web.listen-address=:9882

echo "podman-exporter running on exercises network (http://127.0.0.1:9882/metrics on host)."
echo "Reload Prometheus: curl -X POST http://127.0.0.1:9090/-/reload"
