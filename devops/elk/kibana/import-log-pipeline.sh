#!/usr/bin/env bash
# Provision Kibana saved objects for log pipeline monitoring.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/provision-kibana.sh" "$@"
