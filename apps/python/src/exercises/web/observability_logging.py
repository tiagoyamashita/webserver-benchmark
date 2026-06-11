"""JSON file logging for Filebeat -> Logstash -> Elasticsearch (mirrors Java observability profile)."""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from logging.handlers import WatchedFileHandler
from pathlib import Path


class _JsonLineFormatter(logging.Formatter):
    def __init__(self, service: str) -> None:
        super().__init__()
        self._service = service

    _STANDARD_ATTRS = frozenset(logging.makeLogRecord({}).__dict__.keys()) | frozenset(
        {"message", "asctime"}
    )

    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": self._service,
        }
        for key, value in record.__dict__.items():
            if key not in self._STANDARD_ATTRS:
                payload[key] = value
        if record.exc_info:
            payload["error"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def observability_enabled() -> bool:
    return os.environ.get("EXERCISES_OBSERVABILITY", "").strip().lower() in (
        "1",
        "true",
        "yes",
    )


def configure_observability_logging() -> None:
    """Append JSON lines to ${LOG_PATH}/demo-app.json.log when observability is enabled."""
    if not observability_enabled():
        return

    log_dir = Path(os.environ.get("LOG_PATH", "logs"))
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / "demo-app.json.log"

    root = logging.getLogger()
    if any(
        isinstance(h, WatchedFileHandler) and getattr(h, "baseFilename", "") == str(log_file)
        for h in root.handlers
    ):
        return

    handler = WatchedFileHandler(log_file, encoding="utf-8")
    handler.setFormatter(_JsonLineFormatter(service="exercises-python"))
    handler.setLevel(logging.INFO)
    root.addHandler(handler)
    if root.level == logging.WARNING:
        root.setLevel(logging.INFO)

    # Flask dev server access lines (GET /metrics 200) duplicate http.request JSON logs.
    logging.getLogger("werkzeug").setLevel(logging.WARNING)
