"""Tests for `/api/relay/<target>` outbound relays."""

from __future__ import annotations

import json
from unittest.mock import patch

RELAY_REACT = "/api/relay/react"


def test_list_relays_includes_react(client):
    response = client.get("/api/relay")
    assert response.status_code == 200
    payload = response.get_json()
    assert isinstance(payload, list)
    ids = {entry["id"] for entry in payload}
    assert "react" in ids


def test_relay_unknown_target_returns_404(client):
    response = client.get("/api/relay/unknown")
    assert response.status_code == 404
    assert "unknown relay target" in response.get_json()["error"]


def test_post_relay_react_forwards_json_body(client):
    downstream = {"id": 3, "name": "Relayed", "createdAt": "2026-01-03T00:00:00Z"}
    body_bytes = json.dumps(downstream).encode("utf-8")

    class FakeResponse:
        status = 201
        headers = {"Content-Type": "application/json"}

        def read(self):
            return body_bytes

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

    captured: dict[str, object] = {}

    def fake_urlopen(req, timeout=15):
        captured["url"] = req.full_url
        captured["method"] = req.method
        captured["data"] = req.data
        captured["headers"] = dict(req.header_items())
        return FakeResponse()

    with patch("exercises.web.relay_service.urllib.request.urlopen", fake_urlopen):
        response = client.post(
            RELAY_REACT,
            json={"name": "Relayed"},
            content_type="application/json",
        )

    assert response.status_code == 201
    assert response.get_json() == downstream
    assert captured["url"] == "http://127.0.0.1:5174/api/items"
    assert captured["method"] == "POST"
    assert json.loads(captured["data"].decode("utf-8")) == {"name": "Relayed"}
    headers = {k.lower(): v for k, v in captured["headers"].items()}
    assert headers["x-request-origin"] == "exercises-python"
    assert "x-request-id" in headers


def test_get_relay_react_forwards_to_downstream(client):
    downstream = [{"id": 1, "name": "Widget", "createdAt": "2026-01-01T00:00:00Z"}]
    body_bytes = json.dumps(downstream).encode("utf-8")

    class FakeResponse:
        status = 200
        headers = {"Content-Type": "application/json"}

        def read(self):
            return body_bytes

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

    with patch("exercises.web.relay_service.urllib.request.urlopen", lambda *args, **kwargs: FakeResponse()):
        response = client.get(RELAY_REACT)

    assert response.status_code == 200
    assert response.get_json() == downstream
