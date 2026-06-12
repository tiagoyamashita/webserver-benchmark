"""Structured controller logging (see .cursor/skills/controller-logging/)."""

from __future__ import annotations

import logging
from typing import Any

from exercises.web.request_snapshot import request_body, request_headers, request_url_params

_RECORD_STANDARD = frozenset(logging.makeLogRecord({}).__dict__.keys()) | frozenset(
    {"message", "asctime"}
)


def http_request_fields() -> dict[str, Any]:
    """Headers, query params, and JSON/form body for the current Flask request."""
    try:
        from flask import request as flask_request
    except RuntimeError:
        return {}
    return {
        "headers": request_headers(flask_request),
        "url_params": request_url_params(flask_request),
        "body": request_body(flask_request),
    }


def log_received(
    logger: logging.Logger,
    handler: str,
    source: str,
    method: str,
    path: str,
    *,
    include_http_request: bool = True,
    **params: Any,
) -> None:
    fields = dict(params)
    if include_http_request:
        for key, value in http_request_fields().items():
            fields.setdefault(key, value)
    logger.info(
        f"{handler} request received",
        extra=_extra(source, handler, method=method, path=path, **fields),
    )


def log_succeeded(
    logger: logging.Logger,
    handler: str,
    source: str,
    **params: Any,
) -> None:
    logger.info(
        f"{handler} succeeded",
        extra=_extra(source, handler, **params),
    )


def log_warn(
    logger: logging.Logger,
    handler: str,
    source: str,
    message: str,
    **params: Any,
) -> None:
    logger.warning(
        message,
        extra=_extra(source, handler, **params),
    )


def log_error(
    logger: logging.Logger,
    handler: str,
    source: str,
    message: str,
    *,
    exc: BaseException | None = None,
    **params: Any,
) -> None:
    logger.error(
        message,
        extra=_extra(source, handler, **params),
        exc_info=exc is not None,
    )


def log_trace(
    logger: logging.Logger,
    handler: str,
    source: str,
    message: str,
    **params: Any,
) -> None:
    logger.log(
        logging.DEBUG,
        message,
        extra=_extra(source, handler, **params),
    )


def _current_session_id() -> str | None:
    try:
        from flask import g

        session = getattr(g, "shared_session", None)
        if session is not None:
            session_id = getattr(session, "session_id", None)
            return session_id if isinstance(session_id, str) and session_id else None
    except RuntimeError:
        return None
    return None


def _extra(source: str, handler: str, **fields: Any) -> dict[str, Any]:
    extra: dict[str, Any] = {"source": source, "controller": handler}
    if "session_id" not in fields:
        session_id = _current_session_id()
        if session_id is not None:
            extra["session_id"] = session_id
    for key, value in fields.items():
        if key in _RECORD_STANDARD:
            raise ValueError(f"reserved log record field: {key}")
        extra[key] = value
    return extra
