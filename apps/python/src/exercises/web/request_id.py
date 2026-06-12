"""HTTP request id helpers (X-Request-ID header)."""

from __future__ import annotations

import re
import uuid
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from flask import Request

_SAFE_REQUEST_ID = re.compile(r"^[a-zA-Z0-9._-]{8,64}$")
_HEADER = "X-Request-ID"
_ORIGIN_HEADER = "X-Request-Origin"
_SERVICE = "exercises-python"


def resolve_request_id(request: Request) -> str:
    incoming = (request.headers.get(_HEADER) or "").strip()
    if incoming and _SAFE_REQUEST_ID.fullmatch(incoming):
        return incoming
    return str(uuid.uuid4())


def resolve_outbound_request_id(current: str | None = None) -> str:
    """Reuse a valid inbound/current id; generate when missing (outbound HTTP)."""
    if current is not None:
        trimmed = current.strip()
        if trimmed and _SAFE_REQUEST_ID.fullmatch(trimmed):
            return trimmed
    try:
        from flask import g

        inbound = getattr(g, "request_id", None)
        if isinstance(inbound, str):
            trimmed = inbound.strip()
            if trimmed and _SAFE_REQUEST_ID.fullmatch(trimmed):
                return trimmed
    except RuntimeError:
        pass
    return str(uuid.uuid4())


def outbound_request_headers(current: str | None = None) -> dict[str, str]:
    return {
        _HEADER: resolve_outbound_request_id(current),
        _ORIGIN_HEADER: _SERVICE,
    }


def postgres_application_name(service: str, request_id: str) -> str:
    value = f"{service};req={request_id}"
    return value[:63]
