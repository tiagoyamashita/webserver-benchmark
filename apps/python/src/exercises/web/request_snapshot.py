"""Safe HTTP request headers and body maps for structured access logs."""

from __future__ import annotations

from typing import Any

from flask import Request

_HEADER_ALLOW = frozenset(
    {
        "x-request-id",
        "x-request-origin",
        "x-dashboard-page",
        "x-session-id",
        "content-type",
        "accept",
        "user-agent",
        "host",
    }
)


def request_headers(req: Request) -> dict[str, str]:
    out: dict[str, str] = {}
    for key, value in req.headers.items():
        lowered = key.lower()
        if lowered in _HEADER_ALLOW:
            out[lowered] = value
    return out


def request_url_params(req: Request) -> dict[str, Any]:
    args = req.args.to_dict(flat=False)
    if not args:
        return {}
    return {
        key: values[0] if len(values) == 1 else values
        for key, values in args.items()
    }


def request_body(req: Request) -> dict[str, Any]:
    if req.method in {"GET", "HEAD", "DELETE", "OPTIONS"}:
        return {}

    data = req.get_json(silent=True)
    if isinstance(data, dict):
        return data
    if isinstance(data, list):
        return {"_json": data}

    form = req.form.to_dict(flat=False)
    if form:
        return {
            key: values[0] if len(values) == 1 else values
            for key, values in form.items()
        }

    return {}
