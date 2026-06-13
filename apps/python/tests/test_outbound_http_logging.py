"""Unit tests for outbound HTTP relay logging helpers."""

from __future__ import annotations

from exercises.web.outbound_http_logging import resolve_response_error


def test_resolve_response_error_parses_json_error_field():
    assert resolve_response_error(400, '{"error":"name must not be blank"}') == "name must not be blank"


def test_resolve_response_error_returns_none_for_2xx():
    assert resolve_response_error(201, '{"id":1}') is None
