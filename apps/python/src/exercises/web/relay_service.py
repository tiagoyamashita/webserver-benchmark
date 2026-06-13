"""Execute outbound HTTP relays to other stack services."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from typing import Any

from exercises.web.outbound_http_logging import (
    log_outbound_failure,
    log_outbound_request,
    log_outbound_response,
    resolve_response_error,
)
from exercises.web.relay_registry import RelaySpec, resolve_base_url
from exercises.web.request_id import outbound_request_headers

SOURCE = "src/exercises/web/relay_service.py"


def _parse_json_body(raw: str) -> Any | None:
    if not raw.strip():
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return None


def relay_http(
    spec: RelaySpec,
    *,
    method: str,
    inbound_path: str,
    body: bytes | None,
    content_type: str | None,
    request_id: str | None,
) -> dict[str, Any]:
    """Call the configured downstream endpoint and return a relay result envelope."""
    method_upper = method.upper()
    if method_upper not in spec.methods:
        return {
            "ok": False,
            "request_id": request_id or "",
            "relay_target": spec.relay_target,
            "error": f"method {method_upper} not allowed for relay {spec.id}",
        }

    try:
        base = resolve_base_url(spec)
    except ValueError as exc:
        return {
            "ok": False,
            "request_id": request_id or "",
            "relay_target": spec.relay_target,
            "error": str(exc),
        }

    downstream_url = f"{base}{spec.downstream_path}"
    headers = outbound_request_headers(request_id)
    request_body: dict[str, Any] | None = None
    if body:
        if content_type:
            headers["Content-Type"] = content_type
        elif method_upper in {"POST", "PUT", "PATCH"}:
            headers["Content-Type"] = "application/json"
        try:
            parsed = json.loads(body.decode("utf-8"))
            if isinstance(parsed, dict):
                request_body = parsed
        except (UnicodeDecodeError, json.JSONDecodeError):
            request_body = {"_raw": body.decode("utf-8", errors="replace")}

    log_outbound_request(
        method_upper,
        downstream_url,
        spec.relay_target,
        request_body=request_body,
        origin_method=method_upper,
        origin_path=inbound_path,
    )
    start = time.perf_counter()
    try:
        req = urllib.request.Request(
            downstream_url,
            data=body if body else None,
            method=method_upper,
            headers=headers,
        )
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read().decode("utf-8", errors="replace")
            status = response.status
            response_headers = dict(response.headers.items())
        ms = int((time.perf_counter() - start) * 1000)
        log_outbound_response(
            method_upper,
            downstream_url,
            status,
            ms,
            spec.relay_target,
            response_headers,
            raw,
            origin_method=method_upper,
            origin_path=inbound_path,
        )
        parsed = _parse_json_body(raw)
        ok = 200 <= status < 300
        result: dict[str, Any] = {
            "ok": ok,
            "request_id": request_id or "",
            "relay_target": spec.relay_target,
            "relay_id": spec.id,
            "downstream_url": downstream_url,
            "status": status,
            "body": raw,
        }
        if parsed is not None:
            result["downstream"] = parsed
        if not ok:
            result["error"] = resolve_response_error(status, raw)
        return result
    except urllib.error.HTTPError as exc:
        ms = int((time.perf_counter() - start) * 1000)
        raw = exc.read().decode("utf-8", errors="replace")
        response_headers = dict(exc.headers.items()) if exc.headers else {}
        log_outbound_response(
            method_upper,
            downstream_url,
            exc.code,
            ms,
            spec.relay_target,
            response_headers,
            raw,
            origin_method=method_upper,
            origin_path=inbound_path,
        )
        parsed = _parse_json_body(raw)
        error = resolve_response_error(exc.code, raw) or str(exc)
        result = {
            "ok": False,
            "request_id": request_id or "",
            "relay_target": spec.relay_target,
            "relay_id": spec.id,
            "downstream_url": downstream_url,
            "status": exc.code,
            "body": raw,
            "error": error,
        }
        if parsed is not None:
            result["downstream"] = parsed
        return result
    except urllib.error.URLError as exc:
        ms = int((time.perf_counter() - start) * 1000)
        error = str(exc.reason) if exc.reason else str(exc)
        log_outbound_failure(
            method_upper,
            downstream_url,
            ms,
            spec.relay_target,
            error,
            origin_method=method_upper,
            origin_path=inbound_path,
        )
        return {
            "ok": False,
            "request_id": request_id or "",
            "relay_target": spec.relay_target,
            "relay_id": spec.id,
            "downstream_url": downstream_url,
            "error": error,
        }
