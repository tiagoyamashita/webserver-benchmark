"""Postgres connection helpers (same env vars as Java / Rust Compose services)."""

from __future__ import annotations

import os
from contextlib import contextmanager
from typing import TYPE_CHECKING, Iterator

if TYPE_CHECKING:
    import psycopg


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


@contextmanager
def connection(request_id: str | None = None) -> Iterator["psycopg.Connection"]:
    url = database_url_from_env()
    if not url:
        raise DatabaseNotConfiguredError(
            "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)"
        )
    try:
        import psycopg
    except ModuleNotFoundError as exc:
        raise DatabaseNotConfiguredError(
            "psycopg is not installed; rebuild the python image "
            "(podman compose build python)"
        ) from exc
    with psycopg.connect(url) as conn:
        from exercises.web.request_id import postgres_application_name

        app_name = (
            postgres_application_name("exercises-python", request_id)
            if request_id
            else "exercises-python"
        )
        # SET does not accept $1 placeholders; set_config does (same as Rust sqlx).
        with conn.cursor() as cur:
            cur.execute(
                "SELECT set_config('application_name', %s, false)",
                (app_name,),
            )
        yield conn
