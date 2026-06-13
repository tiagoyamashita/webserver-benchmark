#!/usr/bin/env python3
"""Build a single NDJSON bundle for Kibana saved_objects/_import."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
REQUESTS = ROOT / "requests-logs"
CONFIG = ROOT / "config.json"
DEFAULT_DATA_VIEW = ROOT / "default-data-view.json"
OUT = Path(__file__).resolve().parent / "exercises-kibana.ndjson"

OBJECTS: list[tuple[str, str, Path]] = [
    ("index-pattern", "logstash-data-view", ROOT / "index-pattern.json"),
    ("search", "requests-logs-http-search", REQUESTS / "search-http-requests.json"),
    ("search", "requests-logs-sql-search", REQUESTS / "search-sql-crud.json"),
    ("search", "requests-logs-postgres-stream-search", REQUESTS / "search-postgres-stream.json"),
    ("search", "requests-logs-http-handlers", REQUESTS / "search-http-handlers.json"),
    ("search", "requests-logs-by-session-request", REQUESTS / "search-by-session-or-request.json"),
    ("search", "log-pipeline-all-search", ROOT / "search-all.json"),
    ("search", "log-pipeline-errors-search", ROOT / "search-errors.json"),
    ("dashboard", "exercises-requests-logs-kibana", REQUESTS / "dashboard.json"),
    ("dashboard", "exercises-log-pipeline-kibana", ROOT / "dashboard.json"),
]


def load_object(path: Path) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {
        "attributes": payload["attributes"],
        "references": payload.get("references", []),
    }


def main() -> None:
    lines: list[str] = []
    for obj_type, obj_id, path in OBJECTS:
        body = load_object(path)
        lines.append(
            json.dumps(
                {
                    "id": obj_id,
                    "type": obj_type,
                    "attributes": body["attributes"],
                    "references": body["references"],
                },
                separators=(",", ":"),
            )
        )

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} objects)")


if __name__ == "__main__":
    main()
