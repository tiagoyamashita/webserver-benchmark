"""HTTP request id helpers (X-Request-ID header)."""

from __future__ import annotations

import re
import uuid
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from flask import Request

_SAFE_REQUEST_ID = re.compile(r"^[a-zA-Z0-9._-]{8,64}$")
_HEADER = "X-Request-ID"


def resolve_request_id(request: Request) -> str:
    incoming = (request.headers.get(_HEADER) or "").strip()
    if incoming and _SAFE_REQUEST_ID.fullmatch(incoming):
        return incoming
    return str(uuid.uuid4())


def postgres_application_name(service: str, request_id: str) -> str:
    value = f"{service};req={request_id}"
    return value[:63]
