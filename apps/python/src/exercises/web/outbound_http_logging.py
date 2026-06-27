"""Structured outbound HTTP logging (http.client) for stack relays."""

from __future__ import annotations

import json
import logging
from typing import Any
from urllib.parse import urlparse

from exercises.web.correlation import merge_correlation

_LOG = logging.getLogger("http.client")
_RELAY_ORIGIN = "webserver-benchmark-python"
_HEADER_ALLOW = frozenset(
    {
        "x-request-id",
        "x-request-origin",
        "content-type",
        "content-length",
        "server",
        "date",
        "location",
    }
)


def _path_with_query(url: str) -> str:
    parsed = urlparse(url)
    pathname = parsed.path or "/"
    return f"{pathname}{parsed.query}" if parsed.query else pathname


def _parse_body(raw: str | None) -> dict[str, Any]:
    if not raw or not raw.strip():
        return {}
    text = raw.strip()
    try:
        if text.startswith("{"):
            parsed = json.loads(text)
            if isinstance(parsed, dict):
                return parsed
        if text.startswith("["):
            return {"_json": json.loads(text)}
    except json.JSONDecodeError:
        pass
    return {"_raw": text}


def resolve_response_error(status: int, response_body: str | None) -> str | None:
    if 200 <= status < 300:
        return None
    parsed = _parse_body(response_body)
    for key in ("error", "message"):
        value = parsed.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    raw = parsed.get("_raw")
    if isinstance(raw, str) and raw.strip():
        return raw.strip()
    return f"HTTP {status}"


def _filter_headers(headers: dict[str, str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for key, value in headers.items():
        if key.lower() in _HEADER_ALLOW:
            out[key.lower()] = value
    return out


def _extra(**fields: Any) -> dict[str, Any]:
    extra = merge_correlation(fields)
    extra.setdefault("relay_origin", _RELAY_ORIGIN)
    return extra


def log_outbound_request(
    method: str,
    url: str,
    relay_target: str,
    *,
    request_body: dict[str, Any] | None = None,
    origin_method: str | None = None,
    origin_path: str | None = None,
) -> None:
    fields = _extra(
        method=method,
        path=_path_with_query(url),
        relay_target=relay_target,
        phase="outbound_request",
        origin_method=origin_method,
        origin_path=origin_path,
    )
    if request_body is not None:
        fields["body"] = request_body
    _LOG.info(
        "%s %s outbound request origin=%s %s",
        method,
        _path_with_query(url),
        origin_method or "",
        origin_path or "",
        extra=fields,
    )


def log_outbound_response(
    method: str,
    url: str,
    status: int,
    ms: int,
    relay_target: str,
    response_headers: dict[str, str],
    response_body: str | None,
    *,
    origin_method: str | None = None,
    origin_path: str | None = None,
) -> None:
    ok = 200 <= status < 300
    error = resolve_response_error(status, response_body)
    fields = _extra(
        method=method,
        path=_path_with_query(url),
        status=status,
        ms=ms,
        relay_target=relay_target,
        phase="outbound_response",
        origin_method=origin_method,
        origin_path=origin_path,
        response_headers=_filter_headers(response_headers),
        response_body=_parse_body(response_body),
    )
    if error:
        fields["error"] = error
    message = (
        f"{method} {_path_with_query(url)} {status} outbound response"
        if ok
        else f"{method} {_path_with_query(url)} {status} outbound response error={error or ''}"
    )
    if ok:
        _LOG.info(message, extra=fields)
    else:
        _LOG.warning(message, extra=fields)


def log_outbound_failure(
    method: str,
    url: str,
    ms: int,
    relay_target: str,
    error: str,
    *,
    origin_method: str | None = None,
    origin_path: str | None = None,
) -> None:
    _LOG.warning(
        "%s %s outbound failed",
        method,
        _path_with_query(url),
        extra=_extra(
            method=method,
            path=_path_with_query(url),
            ms=ms,
            relay_target=relay_target,
            phase="outbound_failed",
            error=error,
            origin_method=origin_method,
            origin_path=origin_path,
        ),
    )
