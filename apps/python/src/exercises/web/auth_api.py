"""REST endpoints for shared Redis sessions (/api/auth/*)."""

from __future__ import annotations

import logging

from flask import Flask, g, jsonify, make_response, request

from exercises.web.auth_service import AuthServiceError, ensure_session, login, logout
from exercises.web.controller_logging import log_error, log_received, log_succeeded, log_warn
from exercises.web.session_models import session_response
from exercises.web.session_auth import (
    AuthState,
    append_clear_session_cookie,
    append_session_cookie,
)

SOURCE = "src/exercises/web/auth_api.py"
_LOG = logging.getLogger(__name__)


def _auth_state() -> AuthState | None:
    return getattr(g, "auth_state", None)


def _request_id() -> str | None:
    return getattr(g, "request_id", None)


def _shared_session():
    return getattr(g, "shared_session", None)


def register_auth_routes(app: Flask) -> None:
    @app.post("/api/auth/ensure")
    def auth_ensure():
        log_received(_LOG, "auth_ensure", SOURCE, "POST", "/api/auth/ensure")
        auth = _auth_state()
        if auth is None:
            log_warn(_LOG, "auth_ensure", SOURCE, "redis not configured")
            return jsonify(error="Redis session store not configured"), 503
        body = request.get_json(silent=True) or {}
        client_id = body.get("sessionId") if isinstance(body, dict) else None
        if client_id is not None and not isinstance(client_id, str):
            client_id = None
        try:
            result = ensure_session(
                auth.repo,
                auth.config,
                client_id,
                _shared_session(),
            )
        except Exception as exc:
            log_error(_LOG, "auth_ensure", SOURCE, "auth_ensure failed", error=str(exc))
            return jsonify(error=str(exc)), 503
        payload = session_response(result.session, auth.config.redis_key(result.session.session_id))
        status = 201 if result.created else 200
        response = jsonify(payload)
        response.status_code = status
        append_session_cookie(response, auth.config, result.session.session_id)
        log_succeeded(
            _LOG,
            "auth_ensure",
            SOURCE,
            session_id=result.session.session_id,
            session_created=result.created,
            user_id=result.session.user_id,
        )
        return response

    @app.post("/api/auth/login")
    def auth_login():
        log_received(_LOG, "auth_login", SOURCE, "POST", "/api/auth/login")
        auth = _auth_state()
        if auth is None:
            return jsonify(error="Redis session store not configured"), 503
        body = request.get_json(silent=True) or {}
        email = body.get("email") if isinstance(body, dict) else None
        user_id = body.get("userId") if isinstance(body, dict) else None
        if email is not None and not isinstance(email, str):
            email = None
        if user_id is not None:
            try:
                user_id = int(user_id)
            except (TypeError, ValueError):
                user_id = None
        try:
            session = login(
                auth.repo,
                auth.config,
                email=email,
                user_id=user_id,
                request_id=_request_id(),
            )
        except AuthServiceError as exc:
            log_warn(_LOG, "auth_login", SOURCE, "auth_login rejected", error=exc.message)
            return jsonify(error=exc.message), exc.status
        except Exception as exc:
            log_error(_LOG, "auth_login", SOURCE, "auth_login failed", error=str(exc))
            return jsonify(error=str(exc)), 503
        payload = session_response(session, auth.config.redis_key(session.session_id))
        response = jsonify(payload)
        response.status_code = 201
        append_session_cookie(response, auth.config, session.session_id)
        log_succeeded(
            _LOG,
            "auth_login",
            SOURCE,
            session_id=session.session_id,
            user_id=session.user_id,
        )
        return response

    @app.post("/api/auth/logout")
    def auth_logout():
        log_received(_LOG, "auth_logout", SOURCE, "POST", "/api/auth/logout")
        auth = _auth_state()
        if auth is None:
            return jsonify(error="Redis session store not configured"), 503
        session = _shared_session()
        if session is None:
            return jsonify(error="No active session"), 401
        try:
            logout(auth.repo, session.session_id)
        except Exception as exc:
            log_warn(
                _LOG,
                "auth_logout",
                SOURCE,
                "auth_logout redis delete failed",
                error=str(exc),
            )
        response = make_response("", 204)
        append_clear_session_cookie(response, auth.config)
        log_succeeded(_LOG, "auth_logout", SOURCE, session_id=session.session_id)
        return response
    def auth_session():
        log_received(_LOG, "auth_session", SOURCE, "GET", "/api/auth/session")
        auth = _auth_state()
        if auth is None:
            return jsonify(error="Redis session store not configured"), 503
        session = _shared_session()
        if session is None:
            return jsonify(error="No active session"), 401
        from exercises.web.session_models import utc_now

        if session.is_expired(utc_now()):
            return jsonify(error="Session expired"), 401
        payload = session_response(session, auth.config.redis_key(session.session_id))
        log_succeeded(
            _LOG,
            "auth_session",
            SOURCE,
            session_id=session.session_id,
            user_id=session.user_id,
        )
        return jsonify(payload)
