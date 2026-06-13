#!/usr/bin/env python3
"""Build NDJSON bundle: Logs by tiago data view + Discover saved search."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
OUT = Path(__file__).resolve().parent / "exercises-kibana.ndjson"

DATA_VIEW_ID = "logs-by-tiago"
DISCOVER_SEARCH_ID = "logs-by-tiago-discover"


def load_wrapped(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    lines: list[str] = []

    index_pattern = load_wrapped(ROOT / "index-pattern.json")
    lines.append(
        json.dumps(
            {
                "id": DATA_VIEW_ID,
                "type": "index-pattern",
                "attributes": index_pattern["attributes"],
                "references": index_pattern.get("references", []),
            },
            separators=(",", ":"),
        )
    )

    discover = load_wrapped(ROOT / "logs-by-tiago-discover.json")
    lines.append(
        json.dumps(
            {
                "id": DISCOVER_SEARCH_ID,
                "type": "search",
                "attributes": discover["attributes"],
                "references": discover["references"],
            },
            separators=(",", ":"),
        )
    )

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(lines)} objects: {DATA_VIEW_ID}, {DISCOVER_SEARCH_ID})")


if __name__ == "__main__":
    main()
