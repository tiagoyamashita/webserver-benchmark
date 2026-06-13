"""Redis read/write for shared sessions."""

from __future__ import annotations

import json
import logging
import os
from typing import TYPE_CHECKING

from exercises.web.controller_logging import log_error, log_warn
from exercises.web.session_models import SessionConfig, SharedSession

if TYPE_CHECKING:
    import redis

SOURCE = "src/exercises/web/session_repository.py"
_LOG = logging.getLogger(__name__)


class RedisNotConfiguredError(RuntimeError):
    pass


def redis_url_from_env() -> str | None:
    url = os.environ.get("REDIS_URL", "").strip()
    if url:
        return url
    host = os.environ.get("REDIS_HOST", "").strip()
    if not host:
        return None
    port = os.environ.get("REDIS_PORT", "6379").strip() or "6379"
    return f"redis://{host}:{port}"


def _redis_target() -> dict[str, str]:
    host = os.environ.get("REDIS_HOST", "").strip()
    port = os.environ.get("REDIS_PORT", "6379").strip() or "6379"
    return {"service": "redis", "host": host, "port": port}


def connect_redis() -> redis.Redis | None:
    url = redis_url_from_env()
    target = _redis_target()
    if not url:
        log_warn(
            _LOG,
            "redis_connect",
            SOURCE,
            "redis not configured",
            reason="missing-redis-host",
            **target,
        )
        return None
    try:
        import redis
    except ModuleNotFoundError as exc:
        log_error(
            _LOG,
            "redis_connect",
            SOURCE,
            "redis client not installed",
            exc=exc,
            **target,
        )
        raise RedisNotConfiguredError(
            "redis package is not installed; rebuild the python image"
        ) from exc
    try:
        client = redis.from_url(url, decode_responses=True)
        client.ping()
    except Exception as exc:
        log_error(
            _LOG,
            "redis_connect",
            SOURCE,
            "redis connection failed",
            exc=exc,
            error=str(exc),
            **target,
        )
        raise
    return client


class SessionRepository:
    def __init__(self, client: redis.Redis, config: SessionConfig) -> None:
        self._client = client
        self._config = config

    def find_by_id(self, session_id: str) -> SharedSession | None:
        if not session_id or not session_id.strip():
            return None
        raw = self._client.get(self._config.redis_key(session_id.strip()))
        if not raw:
            return None
        return SharedSession.from_json_dict(json.loads(raw))

    def save(self, session: SharedSession) -> None:
        key = self._config.redis_key(session.session_id)
        self._client.setex(key, self._config.ttl_secs, json.dumps(session.to_json_dict()))

    def delete(self, session_id: str) -> None:
        if not session_id or not session_id.strip():
            return
        self._client.delete(self._config.redis_key(session_id.strip()))
