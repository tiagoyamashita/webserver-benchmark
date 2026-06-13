"""Server-side GET probes for other stack services (same idea as Java `StackPingService`)."""

from __future__ import annotations

import logging
import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse

from exercises.web.controller_logging import log_warn
from exercises.web.request_id import outbound_request_headers

SOURCE = "src/exercises/web/stack_ping.py"
_LOG = logging.getLogger(__name__)


def _read_env(key: str, default: str) -> str:
    value = os.environ.get(key, "").strip()
    return value or default


def _normalize_root(base_url: str) -> str:
    t = base_url.strip()
    if not t:
        return "http://127.0.0.1/"
    path = urlparse(t).path
    if path and path != "/":
        return t
    return t if t.endswith("/") else f"{t}/"


@dataclass(frozen=True)
class StackLinksView:
    java_browser_url: str
    rust_browser_url: str
    prometheus_browser_url: str
    grafana_browser_url: str
    elasticsearch_browser_url: str
    kibana_browser_url: str
    react_node_browser_url: str


@dataclass
class StackLinks:
    java_browser_url: str
    rust_browser_url: str
    prometheus_browser_url: str
    grafana_browser_url: str
    elasticsearch_browser_url: str
    kibana_browser_url: str
    react_node_browser_url: str
    java_base_url: str
    rust_base_url: str
    prometheus_base_url: str
    grafana_base_url: str
    elasticsearch_base_url: str
    kibana_base_url: str
    react_node_base_url: str

    @classmethod
    def from_env(cls) -> StackLinks:
        return cls(
            java_browser_url=_read_env("APP_STACK_JAVA_BROWSER_URL", "http://127.0.0.1:8080/"),
            rust_browser_url=_read_env("APP_STACK_RUST_BROWSER_URL", "http://127.0.0.1:8082/"),
            prometheus_browser_url=_read_env(
                "APP_STACK_PROMETHEUS_BROWSER_URL", "http://127.0.0.1:9090/"
            ),
            grafana_browser_url=_read_env("APP_STACK_GRAFANA_BROWSER_URL", "http://127.0.0.1:3000/"),
            elasticsearch_browser_url=_read_env(
                "APP_STACK_ELASTICSEARCH_BROWSER_URL", "http://127.0.0.1:9200/"
            ),
            kibana_browser_url=_read_env("APP_STACK_KIBANA_BROWSER_URL", "http://127.0.0.1:5601/"),
            react_node_browser_url=_read_env(
                "APP_STACK_REACT_NODE_BROWSER_URL", "http://127.0.0.1:5174/"
            ),
            java_base_url=_read_env("APP_STACK_JAVA_BASE_URL", "http://127.0.0.1:8080"),
            rust_base_url=_read_env("APP_STACK_RUST_BASE_URL", "http://127.0.0.1:8082"),
            prometheus_base_url=_read_env("APP_STACK_PROMETHEUS_BASE_URL", "http://127.0.0.1:9090"),
            grafana_base_url=_read_env("APP_STACK_GRAFANA_BASE_URL", "http://127.0.0.1:3000"),
            elasticsearch_base_url=_read_env(
                "APP_STACK_ELASTICSEARCH_BASE_URL", "http://127.0.0.1:9200"
            ),
            kibana_base_url=_read_env("APP_STACK_KIBANA_BASE_URL", "http://127.0.0.1:5601"),
            react_node_base_url=_read_env("APP_STACK_REACT_NODE_BASE_URL", "http://127.0.0.1:5174"),
        )

    def browser_view(self) -> StackLinksView:
        return StackLinksView(
            java_browser_url=self.java_browser_url,
            rust_browser_url=self.rust_browser_url,
            prometheus_browser_url=self.prometheus_browser_url,
            grafana_browser_url=self.grafana_browser_url,
            elasticsearch_browser_url=self.elasticsearch_browser_url,
            kibana_browser_url=self.kibana_browser_url,
            react_node_browser_url=self.react_node_browser_url,
        )

    def ping(self, target: str, request_id: str | None = None) -> dict[str, Any]:
        key = target.strip().lower()
        if key == "postgres":
            return _ping_postgres(request_id=request_id)
        if key == "redis":
            return _ping_redis()
        dispatch = {
            "java": ("java", self.java_base_url),
            "rust": ("rust", self.rust_base_url),
            "prometheus": ("prometheus", self.prometheus_base_url),
            "grafana": ("grafana", self.grafana_base_url),
            "elasticsearch": ("elasticsearch", self.elasticsearch_base_url),
            "kibana": ("kibana", self.kibana_base_url),
            "react-node": (
                "react-node",
                f"{self.react_node_base_url.rstrip('/')}/api/health",
            ),
        }
        if key not in dispatch:
            return {
                "stack": target,
                "url": "",
                "ok": False,
                "error": "unknown stack target",
            }
        stack, base = dispatch[key]
        return _empty_get(stack, base, request_id=request_id)


def _ping_postgres(*, request_id: str | None = None) -> dict[str, Any]:
    host = _read_env("DB_HOST", "")
    port = _read_env("DB_PORT", "5432")
    url = f"postgres://{host}:{port}" if host else ""
    if not host:
        log_warn(
            _LOG,
            "postgres_ping",
            SOURCE,
            "postgres not configured",
            service="postgres",
            reason="missing-db-host",
        )
        return {
            "stack": "postgres",
            "url": "",
            "ok": False,
            "error": "Postgres not configured (set DB_HOST)",
        }
    try:
        from exercises.web.db import connection

        with connection(request_id=request_id) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()
        return {"stack": "postgres", "url": url, "ok": True}
    except Exception as e:
        log_warn(
            _LOG,
            "postgres_ping",
            SOURCE,
            "postgres ping failed",
            service="postgres",
            url=url,
            error=str(e),
        )
        return {
            "stack": "postgres",
            "url": url,
            "ok": False,
            "error": f"Cannot connect to Postgres. {e}",
        }


def _ping_redis() -> dict[str, Any]:
    host = _read_env("REDIS_HOST", "")
    port = _read_env("REDIS_PORT", "6379")
    url = _read_env("REDIS_URL", f"redis://{host}:{port}" if host else "")
    if not host and not url:
        log_warn(
            _LOG,
            "redis_ping",
            SOURCE,
            "redis not configured",
            service="redis",
            reason="missing-redis-host",
        )
        return {
            "stack": "redis",
            "url": "",
            "ok": False,
            "error": "Redis not configured (set REDIS_HOST or REDIS_URL)",
        }
    try:
        import redis

        client = redis.from_url(url, decode_responses=True)
        pong = client.ping()
        return {
            "stack": "redis",
            "url": url,
            "ok": pong is True,
            "status": 200 if pong is True else None,
        }
    except Exception as e:
        log_warn(
            _LOG,
            "redis_ping",
            SOURCE,
            "redis ping failed",
            service="redis",
            url=url,
            error=str(e),
        )
        return {
            "stack": "redis",
            "url": url,
            "ok": False,
            "error": f"Cannot connect to Redis. {e}",
        }


def _empty_get(stack: str, base_url: str, *, request_id: str | None = None) -> dict[str, Any]:
    url = _normalize_root(base_url)
    try:
        req = urllib.request.Request(url, method="GET", headers=outbound_request_headers(request_id))
        with urllib.request.urlopen(req, timeout=15) as resp:
            status = resp.status
            return {
                "stack": stack,
                "url": url,
                "ok": 200 <= status < 300,
                "status": status,
            }
    except urllib.error.HTTPError as e:
        error = e.reason or str(e)
        log_warn(
            _LOG,
            "stack_http_ping",
            SOURCE,
            "stack http ping failed",
            service=stack,
            url=url,
            status=e.code,
            error=error,
        )
        return {
            "stack": stack,
            "url": url,
            "ok": False,
            "status": e.code,
            "error": error,
        }
    except urllib.error.URLError as e:
        error = (
            "Cannot connect (is the container running on the Compose network?). "
            f"{e.reason}"
        )
        log_warn(
            _LOG,
            "stack_http_ping",
            SOURCE,
            "stack http ping unreachable",
            service=stack,
            url=url,
            error=error,
        )
        return {
            "stack": stack,
            "url": url,
            "ok": False,
            "error": error,
        }
