"""
Placeholder readiness checks for a future /api/orders REST API.
These assert successful HTTP responses; they fail until routes are implemented.

One test per HTTP method, one file dedicated to orders.
"""

from __future__ import annotations

BASE = "/api/orders"


def test_get_orders_collection_is_reachable(client):
    r = client.get(BASE)
    assert r.status_code == 200, "GET /api/orders should return 200 once the API exists"


def test_post_orders_collection_is_reachable(client):
    r = client.post(BASE, json={"item_id": 1, "qty": 2}, content_type="application/json")
    assert r.status_code in (200, 201), "POST /api/orders should succeed once the API exists"


def test_get_order_by_id_is_reachable(client):
    r = client.get(f"{BASE}/100")
    assert r.status_code == 200, "GET /api/orders/100 should return 200 once the API exists"


def test_put_order_by_id_is_reachable(client):
    r = client.put(f"{BASE}/100", json={"status": "shipped"}, content_type="application/json")
    assert r.status_code in (200, 204), "PUT /api/orders/100 should succeed once the API exists"


def test_patch_order_by_id_is_reachable(client):
    r = client.patch(f"{BASE}/100", json={"status": "shipped"}, content_type="application/json")
    assert r.status_code in (200, 204), "PATCH /api/orders/100 should succeed once the API exists"


def test_delete_order_by_id_is_reachable(client):
    r = client.delete(f"{BASE}/100")
    assert r.status_code in (200, 204), "DELETE /api/orders/100 should succeed once the API exists"
