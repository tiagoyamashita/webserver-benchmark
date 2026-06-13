"""Structured controller logging (see .cursor/skills/controller-logging/)."""

from __future__ import annotations

import logging
from typing import Any

from exercises.web.correlation import CorrelationFilter, current_correlation, merge_correlation
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


def log_kafka_received(
    logger: logging.Logger,
    handler: str,
    source: str,
    topic: str,
    *,
    request_id: str | None = None,
    **params: Any,
) -> None:
    """Kafka/async handler entry (no HTTP access log); may include request_id for correlation."""
    fields = dict(params)
    if request_id:
        fields.setdefault("request_id", request_id)
    logger.info(
        f"{handler} kafka message received",
        extra=_extra(
            source,
            handler,
            method="KAFKA",
            path=f"kafka/{topic}",
            topic=topic,
            **fields,
        ),
    )


def http_access_session_fields() -> dict[str, str]:
    """Top-level correlation for http.request access logs."""
    return current_correlation()


def _extra(source: str, handler: str, **fields: Any) -> dict[str, Any]:
    extra: dict[str, Any] = {"source": source, "controller": handler}
    extra = merge_correlation(extra)
    for key, value in fields.items():
        if key in _RECORD_STANDARD:
            raise ValueError(f"reserved log record field: {key}")
        extra[key] = value
    return extra
