"""HTTP request id helpers (X-Request-ID header)."""

from __future__ import annotations

import re
import uuid
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from flask import Request

_SAFE_REQUEST_ID = re.compile(r"^[a-zA-Z0-9._-]{8,64}$")
_UUID_REQUEST_ID = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
_HEADER = "X-Request-ID"
_ORIGIN_HEADER = "X-Request-Origin"
_SERVICE = "exercises-python"


def is_acceptable_request_id(value: str) -> bool:
    """Match Java/Rust inbound id rules (safe token or UUID)."""
    trimmed = value.strip()
    if not trimmed:
        return False
    return bool(_SAFE_REQUEST_ID.fullmatch(trimmed) or _UUID_REQUEST_ID.fullmatch(trimmed))


def resolve_request_id(request: Request) -> str:
    incoming = (request.headers.get(_HEADER) or "").strip()
    if incoming and is_acceptable_request_id(incoming):
        return incoming
    return str(uuid.uuid4())


def resolve_kafka_request_id(
    message_request_id: str | None = None,
    header_request_id: str | None = None,
) -> str:
    """Restore correlation id from Kafka JSON body or header; generate when both missing."""
    if message_request_id is not None:
        trimmed = message_request_id.strip()
        if trimmed and is_acceptable_request_id(trimmed):
            return trimmed
    if header_request_id is not None:
        trimmed = header_request_id.strip()
        if trimmed and is_acceptable_request_id(trimmed):
            return trimmed
    return str(uuid.uuid4())


def resolve_outbound_request_id(current: str | None = None) -> str:
    """Reuse a valid inbound/current id; generate when missing (outbound HTTP)."""
    if current is not None:
        trimmed = current.strip()
        if trimmed and is_acceptable_request_id(trimmed):
            return trimmed
    resolved = resolve_postgres_request_id()
    if resolved is not None:
        return resolved
    return str(uuid.uuid4())


def resolve_postgres_request_id(explicit: str | None = None) -> str | None:
    """Request id for Postgres application_name (explicit arg, else Flask g.request_id)."""
    if explicit is not None:
        trimmed = explicit.strip()
        if trimmed and is_acceptable_request_id(trimmed):
            return trimmed
    try:
        from flask import g, has_request_context

        if not has_request_context():
            return None
        inbound = getattr(g, "request_id", None)
        if isinstance(inbound, str):
            trimmed = inbound.strip()
            if trimmed:
                return trimmed
    except RuntimeError:
        return None
    return None


def outbound_request_headers(current: str | None = None) -> dict[str, str]:
    return {
        _HEADER: resolve_outbound_request_id(current),
        _ORIGIN_HEADER: _SERVICE,
    }


def postgres_application_name(service: str, request_id: str) -> str:
    value = f"{service};req={request_id}"
    return value[:63]
