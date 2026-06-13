#!/usr/bin/env python3
"""Build Kibana dashboards with inline search panels and index-pattern references."""

from __future__ import annotations

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
DATA_VIEW_ID = "logstash-data-view"
DATA_VIEW_REF = "kibanaSavedObjectMeta.searchSourceJSON.index"
VERSION = "8.15.5"


def load_search_flat(path: Path) -> dict:
    """Flatten saved-search attributes, keeping indexRefName for dashboard references."""
    payload = json.loads(path.read_text(encoding="utf-8"))
    return copy.deepcopy(payload["attributes"])


def inline_search_panel(
    panel_index: str,
    x: int,
    y: int,
    w: int,
    h: int,
    flat_state: dict,
) -> dict:
    return {
        "version": VERSION,
        "type": "search",
        "gridData": {"x": x, "y": y, "w": w, "h": h, "i": panel_index},
        "panelIndex": panel_index,
        "embeddableConfig": flat_state,
    }


def dashboard_references(panels: list[dict]) -> list[dict]:
    refs: list[dict] = []
    for panel in panels:
        panel_index = panel["panelIndex"]
        refs.append(
            {
                "id": DATA_VIEW_ID,
                "name": f"{panel_index}:{DATA_VIEW_REF}",
                "type": "index-pattern",
            }
        )
    return refs


def write_dashboard(
    path: Path,
    *,
    dashboard_id: str,
    title: str,
    description: str,
    panels: list[dict],
    time_restore: bool,
    time_from: str | None = None,
    time_to: str | None = None,
) -> None:
    attributes = {
        "title": title,
        "description": description,
        "panelsJSON": json.dumps(panels, separators=(",", ":")),
        "optionsJSON": json.dumps(
            {
                "useMargins": True,
                "syncColors": False,
                "syncCursor": True,
                "syncTooltips": False,
                "hidePanelTitles": False,
            },
            separators=(",", ":"),
        ),
        "timeRestore": time_restore,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps(
                {"query": {"query": "", "language": "kuery"}, "filter": []},
                separators=(",", ":"),
            )
        },
    }
    if time_restore and time_from and time_to:
        attributes["timeFrom"] = time_from
        attributes["timeTo"] = time_to

    payload = {
        "attributes": attributes,
        "references": dashboard_references(panels),
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path} ({dashboard_id}, {len(panels)} panels)")


def main() -> None:
    requests_dir = ROOT / "requests-logs"

    requests_specs = [
        ("1", 0, 0, 24, 16, requests_dir / "search-http-requests.json"),
        ("2", 24, 0, 24, 16, requests_dir / "search-sql-crud.json"),
        ("3", 0, 16, 24, 16, requests_dir / "search-postgres-stream.json"),
        ("4", 24, 16, 24, 16, requests_dir / "search-http-handlers.json"),
    ]
    requests_panels = [
        inline_search_panel(panel_index, x, y, w, h, load_search_flat(path))
        for panel_index, x, y, w, h, path in requests_specs
    ]
    write_dashboard(
        requests_dir / "dashboard.json",
        dashboard_id="exercises-requests-logs-kibana",
        title="Exercises — HTTP & Postgres logs",
        description=(
            "Correlate HTTP requests with Postgres SQL. Uses the logstash-* data view. "
            "Click correlation.request_id in the SQL panel to drill into one request."
        ),
        panels=requests_panels,
        time_restore=True,
        time_from="now-24h",
        time_to="now",
    )

    pipeline_specs = [
        ("1", 0, 0, 24, 20, ROOT / "search-all.json"),
        ("2", 24, 0, 24, 20, ROOT / "search-errors.json"),
    ]
    pipeline_panels = [
        inline_search_panel(panel_index, x, y, w, h, load_search_flat(path))
        for panel_index, x, y, w, h, path in pipeline_specs
    ]
    write_dashboard(
        ROOT / "dashboard.json",
        dashboard_id="exercises-log-pipeline-kibana",
        title="Exercises — Log pipeline (Kibana)",
        description=(
            "Ingest health for Filebeat → Logstash → Elasticsearch (logstash-* data view)."
        ),
        panels=pipeline_panels,
        time_restore=True,
        time_from="now-24h",
        time_to="now",
    )


if __name__ == "__main__":
    main()
