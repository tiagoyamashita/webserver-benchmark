"""REST registration for shared Postgres `users` table."""

from __future__ import annotations

import logging

from flask import Flask, g, jsonify, request

from exercises.web.controller_logging import log_error, log_received, log_succeeded, log_warn
from exercises.web.db import DatabaseNotConfiguredError, connection
from exercises.web.password_util import hash_password

SOURCE = "src/exercises/web/users_api.py"
_LOG = logging.getLogger(__name__)


def _request_id() -> str | None:
    return getattr(g, "request_id", None)


def register_users_routes(app: Flask) -> None:
    @app.post("/api/users")
    def create_user():
        log_received(_LOG, "create_user", SOURCE, "POST", "/api/users")
        body = request.get_json(silent=True) or {}
        name = body.get("name") if isinstance(body, dict) else None
        email = body.get("email") if isinstance(body, dict) else None
        password = body.get("password") if isinstance(body, dict) else None
        if not isinstance(name, str) or not name.strip():
            return jsonify(error="name is required"), 400
        if not isinstance(email, str) or not email.strip():
            return jsonify(error="email is required"), 400
        if not isinstance(password, str) or len(password) < 8:
            return jsonify(error="password must be at least 8 characters"), 400
        trimmed_name = name.strip()
        trimmed_email = email.strip()
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO users (name, email, password_hash, created_at) "
                        "VALUES (%s, %s, %s, NOW()) "
                        "RETURNING id, name, email, created_at",
                        (trimmed_name, trimmed_email, hash_password(password)),
                    )
                    row = cur.fetchone()
                conn.commit()
        except DatabaseNotConfiguredError as exc:
            log_warn(_LOG, "create_user", SOURCE, "database not configured", error=str(exc))
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            message = str(exc)
            if "unique" in message.lower() or "duplicate" in message.lower():
                log_warn(_LOG, "create_user", SOURCE, "duplicate email", email=trimmed_email)
                return jsonify(error="Email already registered"), 409
            log_error(_LOG, "create_user", SOURCE, "create_user failed", error=message)
            return jsonify(error=message), 500
        if row is None:
            return jsonify(error="insert failed"), 500
        user_id, saved_name, saved_email, created_at = row
        payload = {
            "id": int(user_id),
            "name": str(saved_name),
            "email": str(saved_email),
            "createdAt": created_at.isoformat().replace("+00:00", "Z"),
        }
        log_succeeded(_LOG, "create_user", SOURCE, user_id=payload["id"], email=saved_email)
        return jsonify(payload), 201
