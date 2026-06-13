"""Request correlation fields for all Python logs (mirrors Java ObservabilityJsonProvider)."""

from __future__ import annotations

import contextvars
import logging
from contextlib import contextmanager
from collections.abc import Iterator
from typing import Any

_kafka_request_id: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "kafka_request_id", default=None
)


@contextmanager
def kafka_request_id_scope(request_id: str | None) -> Iterator[None]:
    """Bind a Kafka message request id for background consumer logs."""
    token = _kafka_request_id.set(request_id)
    try:
        yield
    finally:
        _kafka_request_id.reset(token)


def current_correlation() -> dict[str, str]:
    """Return request_id and session_id from Flask or Kafka consumer context."""
    fields: dict[str, str] = {}
    kafka_id = _kafka_request_id.get()
    if isinstance(kafka_id, str) and kafka_id.strip():
        fields["request_id"] = kafka_id.strip()

    try:
        from flask import g, has_request_context

        if not has_request_context():
            return fields
    except RuntimeError:
        return fields

    try:
        request_id = getattr(g, "request_id", None)
        if isinstance(request_id, str) and request_id.strip():
            fields.setdefault("request_id", request_id.strip())

        session = getattr(g, "shared_session", None)
        if session is not None:
            session_id = getattr(session, "session_id", None)
            if isinstance(session_id, str) and session_id.strip():
                fields["session_id"] = session_id.strip()
    except RuntimeError:
        return fields
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
