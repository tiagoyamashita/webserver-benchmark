#!/usr/bin/env python3
"""Build index-pattern.json with a valid runtimeFieldMap JSON string."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
RUNTIME = ROOT / "runtime-fields.json"
OUT = ROOT / "index-pattern.json"

FIELD_FORMAT_MAP = {
    "correlation.request_id": {
        "id": "url",
        "params": {
            "type": "a",
            "urlTemplate": (
                "/app/discover#/view/requests-logs-http-handlers"
                "?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-6h,to:now))"
                "&_a=(columns:!('@timestamp',service,controller,method,path,message),filters:!(),"
                "index:logs-by-tiago,interval:auto,"
                "query:(language:kuery,query:'request_id:%22{{value}}%22%20and%20_exists_:controller'),"
                "sort:!(!('@timestamp',asc)))"
            ),
            "labelTemplate": "{{value}}",
        },
    }
}

# Custom column labels in Discover / dashboards (field name in ES stays url_params).
FIELD_ATTRS = {
    "url_params": {"customLabel": "Query string (?params)"},
    "body": {"customLabel": "POST body (JSON/form)"},
    "url_param_name": {"customLabel": "Query: name"},
    "url_param_email": {"customLabel": "Query: email"},
    "url_params.name": {"customLabel": "Query: name (runtime)"},
    "url_params.email": {"customLabel": "Query: email (runtime)"},
    "log_timestamp": {"customLabel": "App log time (raw)"},
    "log_body": {"customLabel": "Body or message (runtime)"},
}


def main() -> None:
    runtime = json.loads(RUNTIME.read_text(encoding="utf-8"))
    # Validate round-trip (catches bad escapes before Kibana import).
    json.loads(json.dumps(runtime, separators=(",", ":")))

    payload = {
        "attributes": {
            "title": "logstash-*",
            "name": "Logs by tiago",
            "timeFieldName": "@timestamp",
            "runtimeFieldMap": json.dumps(runtime, separators=(",", ":")),
            "fieldFormatMap": json.dumps(FIELD_FORMAT_MAP, separators=(",", ":")),
            "fieldAttrs": json.dumps(FIELD_ATTRS, separators=(",", ":")),
        }
    }
    OUT.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(runtime)} runtime fields)")


if __name__ == "__main__":
    main()
