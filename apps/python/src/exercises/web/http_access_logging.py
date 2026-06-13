"""Skip noisy http.request access lines for routine probe/scrape traffic (log failures only)."""

from __future__ import annotations

QUIET_GET_PATHS = frozenset({"/metrics"})


def request_pathname(path: str) -> str:
    pathname = path.split("?", 1)[0]
    if len(pathname) > 1 and pathname.endswith("/"):
        pathname = pathname.rstrip("/")
    return pathname


def should_log_http_access(method: str, path: str, status: int | None = None) -> bool:
    """When False, skip http.request received/completed lines for this request."""
    if method.upper() != "GET":
        return True
    if request_pathname(path) not in QUIET_GET_PATHS:
        return True
    if status is None:
        return False
    return status != 200
