"""Resolve shared Redis sessions from headers/cookies; bootstrap guest on GET /."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from flask import Flask, Response, g, jsonify, request

from exercises.web.auth_service import ensure_session
from exercises.web.session_models import SharedSession, utc_now
from exercises.web.session_repository import SessionRepository

if TYPE_CHECKING:
    from redis import Redis

    from exercises.web.session_models import SessionConfig


@dataclass
class AuthState:
    client: Redis
    repo: SessionRepository
    config: SessionConfig


def session_cookie_value(config: SessionConfig, session_id: str) -> str:
    return (
        f"{config.cookie_name}={session_id}; HttpOnly; Path=/; "
        f"Max-Age={config.ttl_secs}; SameSite=Lax"
    )


def clear_session_cookie_value(config: SessionConfig) -> str:
    return f"{config.cookie_name}=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax"


def append_session_cookie(response: Response, config: SessionConfig, session_id: str) -> None:
    response.headers.add("Set-Cookie", session_cookie_value(config, session_id))


def append_clear_session_cookie(response: Response, config: SessionConfig) -> None:
    response.headers.add("Set-Cookie", clear_session_cookie_value(config))


def session_id_candidates(cookie_name: str) -> list[str]:
    candidates: list[str] = []
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        token = auth[7:].strip()
        if token:
            candidates.append(token)
    header_id = request.headers.get("X-Session-ID", "").strip()
    if header_id:
        candidates.append(header_id)
    cookie = request.cookies.get(cookie_name, "").strip()
    if cookie:
        candidates.append(cookie)
    return candidates


def resolve_shared_session(auth: AuthState | None) -> SharedSession | None:
    if auth is None:
        return None
    now = utc_now()
    for session_id in session_id_candidates(auth.config.cookie_name):
        try:
            session = auth.repo.find_by_id(session_id)
        except Exception:
            continue
        if session is None:
            continue
        if session.is_expired(now):
            auth.repo.delete(session.session_id)
            continue
        return session
    return None


def register_session_middleware(app: Flask, auth: AuthState | None) -> None:
    @app.before_request
    def _resolve_shared_session() -> None:
        g.auth_state = auth
        g.shared_session = resolve_shared_session(auth)
        g.bootstrap_session = None
        if (
            auth is not None
            and request.method == "GET"
            and request.path == "/"
            and g.shared_session is None
        ):
            try:
                result = ensure_session(auth.repo, auth.config, None, None)
                g.shared_session = result.session
                g.bootstrap_session = result.session
            except Exception:
                g.bootstrap_session = None

    @app.after_request
    def _bootstrap_landing_cookie(response: Response) -> Response:
        if auth is None:
            return response
        bootstrap = getattr(g, "bootstrap_session", None)
        if bootstrap is not None and 200 <= response.status_code < 300:
            append_session_cookie(response, auth.config, bootstrap.session_id)
        return response


def _is_public_request() -> bool:
    path = request.path
    method = request.method
    if method == "GET" and path in {"/", "/health", "/metrics"}:
        return True
    if path.startswith("/api/auth/"):
        return True
    if method == "POST" and path == "/api/users":
        return True
    if path.startswith("/static/"):
        return True
    if path.endswith((".js", ".css", ".html", ".ico")):
        return True
    if path.startswith("/api-docs") or path.startswith("/swagger-ui"):
        return True
    return False


def register_auth_guard(app: Flask) -> None:
    @app.before_request
    def _require_logged_in_user() -> Response | None:
        if _is_public_request():
            return None
        session = getattr(g, "shared_session", None)
        if session is not None and session.user_id > 0 and session.email:
            return None
        return jsonify(error="Sign in required"), 401
