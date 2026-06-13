"""Tests for http.request access log filtering."""

from __future__ import annotations

from exercises.web.http_access_logging import request_pathname, should_log_http_access


def test_skips_get_metrics_on_200_or_unknown_status():
    assert should_log_http_access("GET", "/metrics", 200) is False
    assert should_log_http_access("GET", "/metrics") is False
    assert should_log_http_access("GET", "/metrics/", 200) is False


def test_logs_get_metrics_on_non_200():
    assert should_log_http_access("GET", "/metrics", 500) is True


def test_still_logs_other_routes():
    assert should_log_http_access("GET", "/api/items", 200) is True
    assert should_log_http_access("POST", "/metrics", 200) is True


def test_strips_query_string_from_path():
    assert request_pathname("/metrics?verbose=1") == "/metrics"
    assert should_log_http_access("GET", "/metrics?verbose=1", 200) is False
