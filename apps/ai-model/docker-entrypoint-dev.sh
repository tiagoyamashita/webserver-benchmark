#!/bin/sh
set -e
# Bind mount overwrites /app; re-install editable so src comes from the host.
cd /app
pip install -q -e . --no-deps
exec python -m uvicorn ai_model.app:app --host 0.0.0.0 --port 8095 --reload --reload-dir /app/src
