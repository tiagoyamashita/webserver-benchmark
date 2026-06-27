"""Shared Redis session types (same JSON contract as Java / Rust)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def format_instant(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_instant(raw: str) -> datetime:
    text = raw.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    return datetime.fromisoformat(text).astimezone(timezone.utc)


@dataclass
class SharedSession:
    session_id: str
    user_id: int
    email: str | None
    name: str
    issued_at: datetime
    expires_at: datetime
    issuer: str

    def is_expired(self, now: datetime | None = None) -> bool:
        current = now or utc_now()
        return not self.expires_at > current

    def to_json_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "sessionId": self.session_id,
            "userId": self.user_id,
            "name": self.name,
            "issuedAt": format_instant(self.issued_at),
            "expiresAt": format_instant(self.expires_at),
            "issuer": self.issuer,
        }
        if self.email is not None:
            payload["email"] = self.email
        return payload

    @classmethod
    def from_json_dict(cls, data: dict[str, Any]) -> SharedSession:
        email = data.get("email")
        return cls(
            session_id=str(data["sessionId"]),
            user_id=int(data["userId"]),
            email=str(email) if email is not None else None,
            name=str(data["name"]),
            issued_at=parse_instant(str(data["issuedAt"])),
            expires_at=parse_instant(str(data["expiresAt"])),
            issuer=str(data["issuer"]),
        )


@dataclass(frozen=True)
class SessionConfig:
    redis_key_prefix: str
    ttl_secs: int
    cookie_name: str

    @classmethod
    def from_env(cls) -> SessionConfig:
        import os

        prefix = os.environ.get("WEBSERVER_BENCHMARK_SESSION_REDIS_PREFIX", "webserver-benchmark:session:").strip()
        cookie = os.environ.get("WEBSERVER_BENCHMARK_SESSION_COOKIE", "webserver_benchmark_session").strip()
        return cls(
            redis_key_prefix=prefix or "webserver-benchmark:session:",
            ttl_secs=86_400,
            cookie_name=cookie or "webserver_benchmark_session",
        )

    def redis_key(self, session_id: str) -> str:
        return f"{self.redis_key_prefix}{session_id}"


def session_response(session: SharedSession, redis_key: str) -> dict[str, Any]:
    payload = session.to_json_dict()
    payload["redisKey"] = redis_key
    return payload
