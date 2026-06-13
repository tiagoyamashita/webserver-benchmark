#!/usr/bin/env bash
# Provision Kibana saved objects for HTTP + Postgres log correlation.
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/provision-kibana.sh" "$@"
