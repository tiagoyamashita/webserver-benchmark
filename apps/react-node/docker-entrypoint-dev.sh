#!/bin/sh
set -e
# Named volume react-node-node-modules can lag behind package-lock.json after dependency changes.
STAMP="/app/node_modules/.package-lock-stamp"
if [ ! -f "$STAMP" ] || [ "package-lock.json" -nt "$STAMP" ]; then
  echo "Syncing node_modules (package-lock.json changed or volume is empty)..."
  npm ci
  touch "$STAMP"
fi
mkdir -p "${LOG_PATH:-/app/logs}"
exec npm run dev
