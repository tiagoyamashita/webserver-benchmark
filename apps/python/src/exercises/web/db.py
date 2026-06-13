"""Postgres connection helpers (same env vars as Java / Rust Compose services)."""

from __future__ import annotations

import logging
import os
from contextlib import contextmanager
from typing import TYPE_CHECKING, Iterator

from exercises.web.controller_logging import log_error, log_warn
from exercises.web.request_id import postgres_application_name, resolve_postgres_request_id

if TYPE_CHECKING:
    import psycopg

SOURCE = "src/exercises/web/db.py"
_LOG = logging.getLogger(__name__)


class DatabaseNotConfiguredError(RuntimeError):
    pass


def database_url_from_env() -> str | None:
    host = os.environ.get("DB_HOST", "").strip()
    if not host:
        return None
    port = os.environ.get("DB_PORT", "5432").strip() or "5432"
    dbname = os.environ.get("DB_NAME", "demo").strip() or "demo"
    user = os.environ.get("DB_USERNAME", "postgres").strip() or "postgres"
    password = os.environ.get("DB_PASSWORD", "postgres")
    return f"postgresql://{user}:{password}@{host}:{port}/{dbname}"


def _postgres_target() -> dict[str, str]:
    host = os.environ.get("DB_HOST", "").strip()
    port = os.environ.get("DB_PORT", "5432").strip() or "5432"
    dbname = os.environ.get("DB_NAME", "demo").strip() or "demo"
    return {
        "target_service": "postgres",
        "host": host,
        "port": port,
        "dbname": dbname,
    }


@contextmanager
def connection(request_id: str | None = None) -> Iterator["psycopg.Connection"]:
    resolved_request_id = resolve_postgres_request_id(request_id)
    target = _postgres_target()
    url = database_url_from_env()
    if not url:
        log_warn(
            _LOG,
            "postgres_connect",
            SOURCE,
            "postgres not configured",
            request_id=resolved_request_id,
            reason="missing-db-host",
            **target,
        )
        raise DatabaseNotConfiguredError(
            "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)"
        )
    try:
        import psycopg
    except ModuleNotFoundError as exc:
        log_error(
            _LOG,
            "postgres_connect",
            SOURCE,
            "postgres client not installed",
            exc=exc,
            request_id=resolved_request_id,
            **target,
        )
        raise DatabaseNotConfiguredError(
            "psycopg is not installed; rebuild the python image "
            "(podman compose build python)"
        ) from exc

    app_name = (
        postgres_application_name("exercises-python", resolved_request_id)
        if resolved_request_id
        else "exercises-python"
    )
    try:
        conn = psycopg.connect(url, application_name=app_name)
    except Exception as exc:
        log_error(
            _LOG,
            "postgres_connect",
            SOURCE,
            "postgres connection failed",
            exc=exc,
            request_id=resolved_request_id,
            application_name=app_name,
            error=str(exc),
            **target,
        )
        raise
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT set_config('application_name', %s, false)",
                (app_name,),
            )
        _LOG.info(
            "postgres connection ready application_name=%s",
            app_name,
            extra={
                "source": SOURCE,
                "controller": "postgres_connect",
                "request_id": resolved_request_id,
                "application_name": app_name,
                **target,
            },
        )
        yield conn
    finally:
        conn.close()


def find_user_by_email(conn: "psycopg.Connection", email: str) -> tuple[int, str, str] | None:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, email FROM users WHERE LOWER(email) = LOWER(%s) LIMIT 1",
            (email,),
        )
        row = cur.fetchone()
    if row is None:
        return None
    return int(row[0]), str(row[1]), str(row[2])


def find_user_by_id(conn: "psycopg.Connection", user_id: int) -> tuple[int, str, str] | None:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, name, email FROM users WHERE id = %s LIMIT 1",
            (user_id,),
        )
        row = cur.fetchone()
    if row is None:
        return None
    return int(row[0]), str(row[1]), str(row[2])
