"""BCrypt password hashing (compatible with Java $2a$ hashes)."""

from __future__ import annotations

import bcrypt


def hash_password(raw_password: str) -> str:
    return bcrypt.hashpw(raw_password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(raw_password: str, password_hash: str | None) -> bool:
    if not raw_password or not password_hash:
        return False
    try:
        return bcrypt.checkpw(raw_password.encode("utf-8"), password_hash.encode("utf-8"))
    except ValueError:
        return False
