#!/bin/sh
set -e
cd /app
# Build and install inside the container; caches live on named volumes (see docker-compose.dev.yml).
zig build
exec ./zig-out/bin/webserver-benchmark-zig
