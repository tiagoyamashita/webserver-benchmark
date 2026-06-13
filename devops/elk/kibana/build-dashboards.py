#!/usr/bin/env python3
"""Build Kibana dashboards that reference library saved searches (by-ref panels)."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "saved_objects"
VERSION = "8.15.5"


def by_ref_search_panel(
    panel_index: str,
    x: int,
    y: int,
    w: int,
    h: int,
    search_id: str,
) -> dict:
    ref_name = f"panel_{panel_index}"
    return {
        "version": VERSION,
        "type": "search",
        "gridData": {"x": x, "y": y, "w": w, "h": h, "i": panel_index},
        "panelIndex": panel_index,
        "panelRefName": ref_name,
        "embeddableConfig": {
            "savedObjectId": search_id,
            "enhancements": {},
        },
    }


def search_panel_reference(panel_index: str, search_id: str) -> dict:
    ref_name = f"panel_{panel_index}"
    return {
        "id": search_id,
        "name": f"{panel_index}:{ref_name}",
        "type": "search",
    }


def write_dashboard(
    path: Path,
    *,
    dashboard_id: str,
    title: str,
    description: str,
    panels: list[dict],
    panel_refs: list[dict],
    time_restore: bool,
    time_from: str | None = None,
    time_to: str | None = None,
    hide_panel_titles: bool = False,
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
                "hidePanelTitles": hide_panel_titles,
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

    payload = {"attributes": attributes, "references": panel_refs}
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path} ({dashboard_id}, {len(panels)} panels)")


def main() -> None:
    requests_dir = ROOT / "requests-logs"

    http_search_id = "requests-logs-http-postgres-dashboard"
    requests_panels = [by_ref_search_panel("1", 0, 0, 48, 28, http_search_id)]
    requests_refs = [search_panel_reference("1", http_search_id)]
    write_dashboard(
        requests_dir / "dashboard.json",
        dashboard_id="exercises-requests-logs-kibana",
        title="Exercises — HTTP & Postgres logs",
        description=(
            "All logs in the logstash-* data view (last 24h by default). "
            "session_id_resolved fills session from headers when needed; "
            "body and message show HTTP payload vs plain log text."
        ),
        panels=requests_panels,
        panel_refs=requests_refs,
        time_restore=True,
        time_from="now-24h",
        time_to="now",
        hide_panel_titles=True,
    )

    pipeline_specs = [
        ("1", 0, 0, 24, 20, "log-pipeline-all-search"),
        ("2", 24, 0, 24, 20, "log-pipeline-errors-search"),
    ]
    pipeline_panels = [
        by_ref_search_panel(panel_index, x, y, w, h, search_id)
        for panel_index, x, y, w, h, search_id in pipeline_specs
    ]
    pipeline_refs = [
        search_panel_reference(panel_index, search_id)
        for panel_index, _, _, _, _, search_id in pipeline_specs
    ]
    write_dashboard(
        ROOT / "dashboard.json",
        dashboard_id="exercises-log-pipeline-kibana",
        title="Exercises — Log pipeline (Kibana)",
        description=(
            "Ingest health for Filebeat → Logstash → Elasticsearch (logstash-* data view)."
        ),
        panels=pipeline_panels,
        panel_refs=pipeline_refs,
        time_restore=True,
        time_from="now-24h",
        time_to="now",
    )


if __name__ == "__main__":
    main()
