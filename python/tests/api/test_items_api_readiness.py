"""
Items API readiness checks — mirrors Java `/api/items` (shared Postgres `items` table).
Without DB_* env vars the API returns 503; tests below mock Postgres access.
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
from unittest.mock import patch

BASE = "/api/items"
SAMPLE_ROW = (1, "Widget", datetime(2026, 1, 1, tzinfo=timezone.utc))


@contextmanager
def _mock_db():
    class FakeCursor:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def execute(self, *_args, **_kwargs):
            return None

        def fetchall(self):
            return [SAMPLE_ROW]

        def fetchone(self):
            return SAMPLE_ROW

    class FakeConn:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def cursor(self):
            return FakeCursor()

        def commit(self):
            return None

    @contextmanager
    def fake_connection():
        yield FakeConn()

    with patch("exercises.web.items_api.connection", fake_connection):
        yield


def test_get_items_collection_without_db_returns_503(client):
    r = client.get(BASE)
    assert r.status_code == 503


def test_get_items_collection_is_reachable(client):
    with _mock_db():
        r = client.get(BASE)
    assert r.status_code == 200
    assert r.get_json() == [
        {"id": 1, "name": "Widget", "createdAt": "2026-01-01T00:00:00Z"},
    ]


def test_post_items_collection_is_reachable(client):
    with _mock_db():
        r = client.post(BASE, json={"name": "Widget"}, content_type="application/json")
    assert r.status_code == 201


def test_get_item_by_id_is_reachable(client):
    with _mock_db():
        r = client.get(f"{BASE}/1")
    assert r.status_code == 200


def test_put_item_by_id_is_reachable(client):
    with _mock_db():
        r = client.put(f"{BASE}/1", json={"name": "Gadget"}, content_type="application/json")
    assert r.status_code == 200


def test_patch_item_by_id_is_reachable(client):
    with _mock_db():
        r = client.patch(f"{BASE}/1", json={"name": "Gadget"}, content_type="application/json")
    assert r.status_code == 200


def test_delete_item_by_id_is_reachable(client):
    with _mock_db():
        r = client.delete(f"{BASE}/1")
    assert r.status_code == 204
