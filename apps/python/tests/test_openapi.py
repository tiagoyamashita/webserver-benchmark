from __future__ import annotations


def test_openapi_json_spec(client):
    r = client.get("/api-docs/openapi.json")
    assert r.status_code == 200
    data = r.get_json()
    assert data["openapi"] == "3.0.3"
    assert data["info"]["title"] == "Exercises Python API"
    paths = data["paths"]
    assert "/api/items" in paths
    assert "get" in paths["/api/items"]
    assert "post" in paths["/api/items"]
    assert "/api/items/{item_id}" in paths
    assert "delete" in paths["/api/items/{item_id}"]


def test_swagger_ui_page(client):
    r = client.get("/swagger-ui")
    assert r.status_code == 200
    assert b"swagger-ui" in r.data.lower()
    assert b"/api-docs/openapi.json" in r.data
