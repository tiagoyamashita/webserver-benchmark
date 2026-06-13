#!/bin/sh
set -e
# Bind mount overwrites /app; re-install editable so templates + src come from the host.
cd /app
pip install -q -e . --no-deps
pip install -q kafka-python
exec exercises-web
