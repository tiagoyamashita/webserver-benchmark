#!/usr/bin/env python3
"""Build NDJSON bundle: data view, Discover search, Kafka dashboard + saved searches."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
OUT = Path(__file__).resolve().parent / "exercises-kibana.ndjson"

DATA_VIEW_ID = "logs-by-tiago"
DISCOVER_SEARCH_ID = "logs-by-tiago-discover"

# id -> relative path under saved_objects (type inferred from path)
BUNDLE: list[tuple[str, str, str]] = [
    (DATA_VIEW_ID, "index-pattern", "index-pattern.json"),
    (DISCOVER_SEARCH_ID, "search", "logs-by-tiago-discover.json"),
    ("kafka-logs-broker-search", "search", "kafka-logs/search-broker.json"),
    ("kafka-logs-app-events-search", "search", "kafka-logs/search-app-events.json"),
    ("kafka-logs-correlate-request-search", "search", "kafka-logs/search-correlate-request.json"),
    ("exercises-kafka-logs-kibana", "dashboard", "kafka-logs/dashboard.json"),
]


def load_wrapped(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def append_object(lines: list[str], obj_id: str, obj_type: str, wrapped: dict) -> None:
    lines.append(
        json.dumps(
            {
                "id": obj_id,
                "type": obj_type,
                "attributes": wrapped["attributes"],
                "references": wrapped.get("references", []),
            },
            separators=(",", ":"),
        )
    )


def main() -> None:
    dashboards_script = Path(__file__).resolve().parent / "build-dashboards.py"
    subprocess.run([sys.executable, str(dashboards_script)], check=True)

    lines: list[str] = []
    for obj_id, obj_type, rel_path in BUNDLE:
        append_object(lines, obj_id, obj_type, load_wrapped(ROOT / rel_path))

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    ids = ", ".join(obj_id for obj_id, _, _ in BUNDLE)
    print(f"wrote {OUT} ({len(lines)} objects: {ids})")


if __name__ == "__main__":
    main()
