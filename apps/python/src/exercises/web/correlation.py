"""Request correlation fields for all Python logs (mirrors Java ObservabilityJsonProvider)."""

from __future__ import annotations

import logging
from typing import Any


def current_correlation() -> dict[str, str]:
    """Return request_id and session_id from the active Flask request, when present."""
    try:
        from flask import g
    except RuntimeError:
        return {}

    fields: dict[str, str] = {}
    request_id = getattr(g, "request_id", None)
    if isinstance(request_id, str) and request_id.strip():
        fields["request_id"] = request_id.strip()

    session = getattr(g, "shared_session", None)
    if session is not None:
        session_id = getattr(session, "session_id", None)
        if isinstance(session_id, str) and session_id.strip():
            fields["session_id"] = session_id.strip()
    return fields


def apply_correlation(record: logging.LogRecord) -> None:
    """Attach correlation fields to a log record without overwriting explicit values."""
    for key, value in current_correlation().items():
        if not getattr(record, key, None):
            setattr(record, key, value)


class CorrelationFilter(logging.Filter):
    """Inject request_id and session_id into every log line inside a Flask request."""

    def filter(self, record: logging.LogRecord) -> bool:
        apply_correlation(record)
        return True


def merge_correlation(extra: dict[str, Any]) -> dict[str, Any]:
    """Merge correlation into controller/service extra fields (explicit values win)."""
    merged = dict(current_correlation())
    merged.update(extra)
    return merged
