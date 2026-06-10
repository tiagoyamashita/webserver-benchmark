"""Structured controller logging (see .cursor/skills/controller-logging/)."""

from __future__ import annotations

import logging
from typing import Any

_RECORD_STANDARD = frozenset(logging.makeLogRecord({}).__dict__.keys()) | frozenset(
    {"message", "asctime"}
)


def log_received(
    logger: logging.Logger,
    handler: str,
    source: str,
    method: str,
    path: str,
    **params: Any,
) -> None:
    logger.info(
        f"{handler} request received",
        extra=_extra(source, handler, method=method, path=path, **params),
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


def _extra(source: str, handler: str, **fields: Any) -> dict[str, Any]:
    extra: dict[str, Any] = {"source": source, "controller": handler}
    for key, value in fields.items():
        if key in _RECORD_STANDARD:
            raise ValueError(f"reserved log record field: {key}")
        extra[key] = value
    return extra
