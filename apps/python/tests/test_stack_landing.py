from __future__ import annotations


def test_stack_landing_page(client):
    r = client.get("/")
    assert r.status_code == 200
    assert b"Hello Python" in r.data
    assert b"stack-services" in r.data


def test_tests_dashboard_route(client):
    r = client.get("/tests")
    assert r.status_code == 200
    assert b"Pytest results" in r.data


def test_stack_ping_unknown_target(client):
    r = client.get("/stack-ping/not-a-service")
    assert r.status_code == 200
    data = r.get_json()
    assert data["ok"] is False
    assert data.get("error")
