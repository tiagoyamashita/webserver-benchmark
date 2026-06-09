from __future__ import annotations


def test_metrics_endpoint_exposes_prometheus_format(client):
    r = client.get("/metrics")
    assert r.status_code == 200
    body = r.get_data(as_text=True)
    assert "exercises_http_requests_total" in body
    assert "# HELP" in body or "# TYPE" in body
