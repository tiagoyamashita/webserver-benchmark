"""
Placeholder readiness checks for a future /api/users REST API.
These assert successful HTTP responses; they fail until routes are implemented.

One test per HTTP method, one file dedicated to users.
"""

from __future__ import annotations

BASE = "/api/users"


def test_get_users_collection_is_reachable(client):
    r = client.get(BASE)
    assert r.status_code == 200, "GET /api/users should return 200 once the API exists"


def test_post_users_collection_is_reachable(client):
    r = client.post(BASE, json={"email": "u@example.com"}, content_type="application/json")
    assert r.status_code in (200, 201), "POST /api/users should succeed once the API exists"


def test_get_user_by_id_is_reachable(client):
    r = client.get(f"{BASE}/1")
    assert r.status_code == 200, "GET /api/users/1 should return 200 once the API exists"


def test_put_user_by_id_is_reachable(client):
    r = client.put(f"{BASE}/1", json={"name": "Ada"}, content_type="application/json")
    assert r.status_code in (200, 204), "PUT /api/users/1 should succeed once the API exists"


def test_patch_user_by_id_is_reachable(client):
    r = client.patch(f"{BASE}/1", json={"name": "Ada"}, content_type="application/json")
    assert r.status_code in (200, 204), "PATCH /api/users/1 should succeed once the API exists"


def test_delete_user_by_id_is_reachable(client):
    r = client.delete(f"{BASE}/1")
    assert r.status_code in (200, 204), "DELETE /api/users/1 should succeed once the API exists"
