#!/usr/bin/env python3
"""Build Kibana dashboards that reference library saved searches."""

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
        },
    }


def panel_reference(panel_index: str, search_id: str) -> dict:
    return {
        "id": search_id,
        "name": f"panel_{panel_index}",
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

    payload = {"attributes": attributes, "references": panel_refs}
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path} ({dashboard_id}, {len(panels)} panels)")


def main() -> None:
    requests_dir = ROOT / "requests-logs"

    requests_specs = [
        ("1", 0, 0, 24, 16, "requests-logs-http-search"),
        ("2", 24, 0, 24, 16, "requests-logs-sql-search"),
        ("3", 0, 16, 24, 16, "requests-logs-postgres-stream-search"),
        ("4", 24, 16, 24, 16, "requests-logs-http-handlers"),
    ]
    requests_panels = [
        by_ref_search_panel(panel_index, x, y, w, h, search_id)
        for panel_index, x, y, w, h, search_id in requests_specs
    ]
    requests_refs = [
        panel_reference(panel_index, search_id)
        for panel_index, _, _, _, _, search_id in requests_specs
    ]
    write_dashboard(
        requests_dir / "dashboard.json",
        dashboard_id="exercises-requests-logs-kibana",
        title="Exercises — HTTP & Postgres logs",
        description=(
            "Correlate HTTP requests with Postgres SQL. PostgreSQL — SQL CRUD by origin "
            "(top right): application_name is parsed into correlation.origin and "
            "correlation.request_id. Click correlation.request_id to open All logs for "
            "request for that id across Java, Rust, Python, React Node, and Postgres. "
            "For the full click-through layout, use Grafana Exercises HTTP requests & "
            "SQL logs on port 3000."
        ),
        panels=requests_panels,
        panel_refs=requests_refs,
        time_restore=True,
        time_from="now-6h",
        time_to="now",
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
        panel_reference(panel_index, search_id)
        for panel_index, _, _, _, _, search_id in pipeline_specs
    ]
    write_dashboard(
        ROOT / "dashboard.json",
        dashboard_id="exercises-log-pipeline-kibana",
        title="Exercises — Log pipeline (Kibana)",
        description=(
            "Ingest health for Filebeat → Logstash → Elasticsearch. If Grafana alerted "
            "or ingest flatlined: note the alert start time, inspect apps/*/logs/*.json.log "
            "and postgres/logs/postgresql-* on disk for that window, compare with this "
            "dashboard, then check filebeat/logstash container logs."
        ),
        panels=pipeline_panels,
        panel_refs=pipeline_refs,
        time_restore=True,
        time_from="now-6h",
        time_to="now",
    )


if __name__ == "__main__":
    main()
