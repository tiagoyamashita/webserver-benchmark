"""Skip noisy http.request access lines for routine probe/scrape traffic (log failures only)."""

from __future__ import annotations

QUIET_GET_PATHS = frozenset({"/metrics"})
QUIET_POST_STATUSES: dict[str, frozenset[int]] = {
    "/api/auth/ensure": frozenset({200}),
}


def request_pathname(path: str) -> str:
    pathname = path.split("?", 1)[0]
    if len(pathname) > 1 and pathname.endswith("/"):
        pathname = pathname.rstrip("/")
    return pathname


def should_log_http_access(method: str, path: str, status: int | None = None) -> bool:
    """When False, skip http.request received/completed lines for this request."""
    pathname = request_pathname(path)
    upper = method.upper()

    if upper == "GET" and pathname in QUIET_GET_PATHS:
        if status is None:
            return False
        return status != 200

    quiet_statuses = QUIET_POST_STATUSES.get(pathname)
    if upper == "POST" and quiet_statuses is not None:
        if status is None:
            return False
        return status not in quiet_statuses

    return True
