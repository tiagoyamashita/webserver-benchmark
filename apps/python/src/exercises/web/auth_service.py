"""Session ensure/login/logout (mirrors Java AuthService / Rust auth service)."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import timedelta

from exercises.web.db import DatabaseNotConfiguredError, connection, find_user_auth_by_email, find_user_by_email, find_user_by_id
from exercises.web.password_util import verify_password
from exercises.web.session_models import SessionConfig, SharedSession, utc_now
from exercises.web.session_repository import SessionRepository


@dataclass
class EnsureSessionResult:
    session: SharedSession
    created: bool


class AuthServiceError(Exception):
    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.message = message


def ensure_session(
    repo: SessionRepository,
    config: SessionConfig,
    client_session_id: str | None,
    request_session: SharedSession | None,
) -> EnsureSessionResult:
    now = utc_now()
    if request_session is not None and not request_session.is_expired(now):
        return EnsureSessionResult(request_session, created=False)
    if client_session_id and client_session_id.strip():
        stored = repo.find_by_id(client_session_id.strip())
        if stored is not None and not stored.is_expired(now):
            return EnsureSessionResult(stored, created=False)
        if stored is not None:
            repo.delete(stored.session_id)
    session = _create_anonymous_session(repo, config)
    return EnsureSessionResult(session, created=True)


def login(
    repo: SessionRepository,
    config: SessionConfig,
    *,
    email: str | None,
    user_id: int | None,
    password: str | None = None,
    request_id: str | None = None,
) -> SharedSession:
    user = _resolve_user(email=email, user_id=user_id, password=password, request_id=request_id)
    issued_at = utc_now()
    expires_at = issued_at + timedelta(seconds=config.ttl_secs)
    session = SharedSession(
        session_id=str(uuid.uuid4()),
        user_id=user[0],
        email=user[2],
        name=user[1],
        issued_at=issued_at,
        expires_at=expires_at,
        issuer="python",
    )
    repo.save(session)
    return session


def logout(repo: SessionRepository, session_id: str) -> None:
    repo.delete(session_id)


def logout_and_create_guest(
    repo: SessionRepository,
    config: SessionConfig,
    session_id: str | None,
) -> SharedSession:
    """Delete the cookie session id (if any) and issue a fresh guest session in Redis."""
    if session_id and session_id.strip():
        repo.delete(session_id.strip())
    return _create_anonymous_session(repo, config)


def refresh_session(
    repo: SessionRepository,
    config: SessionConfig,
    current: SharedSession | None,
) -> SharedSession:
    """Delete the current Redis session (if any) and issue a new session id."""
    if current is not None:
        repo.delete(current.session_id)
    issued_at = utc_now()
    expires_at = issued_at + timedelta(seconds=config.ttl_secs)
    if current is not None and current.user_id > 0:
        session = SharedSession(
            session_id=str(uuid.uuid4()),
            user_id=current.user_id,
            email=current.email,
            name=current.name,
            issued_at=issued_at,
            expires_at=expires_at,
            issuer="python",
        )
    else:
        session = SharedSession(
            session_id=str(uuid.uuid4()),
            user_id=0,
            email=None,
            name="Guest",
            issued_at=issued_at,
            expires_at=expires_at,
            issuer="python",
        )
    repo.save(session)
    return session


def _create_anonymous_session(repo: SessionRepository, config: SessionConfig) -> SharedSession:
    issued_at = utc_now()
    expires_at = issued_at + timedelta(seconds=config.ttl_secs)
    session = SharedSession(
        session_id=str(uuid.uuid4()),
        user_id=0,
        email=None,
        name="Guest",
        issued_at=issued_at,
        expires_at=expires_at,
        issuer="python",
    )
    repo.save(session)
    return session


def _resolve_user(
    *,
    email: str | None,
    user_id: int | None,
    password: str | None = None,
    request_id: str | None,
) -> tuple[int, str, str]:
    trimmed_email = email.strip() if email else ""
    if trimmed_email:
        try:
            with connection(request_id=request_id) as conn:
                auth_row = find_user_auth_by_email(conn, trimmed_email)
        except DatabaseNotConfiguredError as exc:
            raise AuthServiceError(503, str(exc)) from exc
        if auth_row is None:
            raise AuthServiceError(404, f"No user with email {trimmed_email}")
        if password:
            if not auth_row[3] or not verify_password(password, auth_row[3]):
                raise AuthServiceError(401, "Invalid email or password")
        return auth_row[0], auth_row[1], auth_row[2]
    if user_id is not None:
        try:
            with connection(request_id=request_id) as conn:
                row = find_user_by_id(conn, user_id)
        except DatabaseNotConfiguredError as exc:
            raise AuthServiceError(503, str(exc)) from exc
        if row is None:
            raise AuthServiceError(404, f"No user with id {user_id}")
        return row
    raise AuthServiceError(400, "email or userId is required")
