"""Server-side GET probes for other stack services (same idea as Java `StackPingService`)."""

from __future__ import annotations

import os
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any


def _read_env(key: str, default: str) -> str:
    value = os.environ.get(key, "").strip()
    return value or default


def _normalize_root(base_url: str) -> str:
    t = base_url.strip()
    if not t:
        return "http://127.0.0.1/"
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

    def ping(self, target: str) -> dict[str, Any]:
        key = target.strip().lower()
        dispatch = {
            "java": ("java", self.java_base_url),
            "rust": ("rust", self.rust_base_url),
            "prometheus": ("prometheus", self.prometheus_base_url),
            "grafana": ("grafana", self.grafana_base_url),
            "elasticsearch": ("elasticsearch", self.elasticsearch_base_url),
            "kibana": ("kibana", self.kibana_base_url),
            "react-node": ("react-node", self.react_node_base_url),
        }
        if key not in dispatch:
            return {
                "stack": target,
                "url": "",
                "ok": False,
                "error": "unknown stack target",
            }
        stack, base = dispatch[key]
        return _empty_get(stack, base)


def _empty_get(stack: str, base_url: str) -> dict[str, Any]:
    url = _normalize_root(base_url)
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=15) as resp:
            status = resp.status
            return {
                "stack": stack,
                "url": url,
                "ok": 200 <= status < 300,
                "status": status,
            }
    except urllib.error.HTTPError as e:
        return {
            "stack": stack,
            "url": url,
            "ok": False,
            "status": e.code,
            "error": e.reason or str(e),
        }
    except urllib.error.URLError as e:
        return {
            "stack": stack,
            "url": url,
            "ok": False,
            "error": (
                "Cannot connect (is the container running on the Compose network?). "
                f"{e.reason}"
            ),
        }
