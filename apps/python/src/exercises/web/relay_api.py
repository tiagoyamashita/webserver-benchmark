"""Flask routes for `/api/relay/<target>` outbound relays."""

from __future__ import annotations

import logging

from flask import Flask, Response, g, jsonify, request

from exercises.web.controller_logging import log_received, log_succeeded, log_warn
from exercises.web.relay_registry import get_relay_spec, list_relay_specs
from exercises.web.relay_service import relay_http

SOURCE = "src/exercises/web/relay_api.py"
_LOG = logging.getLogger(__name__)


def register_relay_routes(app: Flask) -> None:
    @app.get("/api/relay")
    def list_relays():
        log_received(_LOG, "list_relays", SOURCE, "GET", "/api/relay")
        payload = [
            {
                "id": spec.id,
                "path": f"/api/relay/{spec.id}",
                "methods": sorted(spec.methods),
                "relay_target": spec.relay_target,
                "downstream_path": spec.downstream_path,
                "description": spec.description,
            }
            for spec in list_relay_specs()
        ]
        log_succeeded(_LOG, "list_relays", SOURCE, count=len(payload))
        return jsonify(payload)

    @app.route("/api/relay/<target_id>", methods=["GET", "POST", "PUT", "PATCH", "DELETE"])
    def relay_to_target(target_id: str):
        spec = get_relay_spec(target_id)
        inbound_path = f"/api/relay/{target_id}"
        request_id = getattr(g, "request_id", None)
        log_received(
            _LOG,
            "relay_to_target",
            SOURCE,
            request.method,
            inbound_path,
            relay_id=target_id,
            relay_target=spec.relay_target if spec else None,
        )
        if spec is None:
            log_warn(
                _LOG,
                "relay_to_target",
                SOURCE,
                "relay_to_target unknown target",
                relay_id=target_id,
            )
            return jsonify(error=f"unknown relay target: {target_id}"), 404

        body = request.get_data() if request.method in {"POST", "PUT", "PATCH"} else None
        result = relay_http(
            spec,
            method=request.method,
            inbound_path=inbound_path,
            body=body,
            content_type=request.content_type,
            request_id=request_id,
        )
        status = int(result.get("status") or 502)
        if result.get("ok") is True:
            log_succeeded(
                _LOG,
                "relay_to_target",
                SOURCE,
                relay_id=spec.id,
                relay_target=spec.relay_target,
                downstream_url=result.get("downstream_url"),
                status=status,
            )
            downstream = result.get("downstream")
            if downstream is not None:
                return jsonify(downstream), status
            return Response(result.get("body") or "", status=status, mimetype="text/plain")

        log_warn(
            _LOG,
            "relay_to_target",
            SOURCE,
            "relay_to_target failed",
            relay_id=spec.id,
            relay_target=spec.relay_target,
            downstream_url=result.get("downstream_url"),
            status=status if status >= 400 else 502,
            error=result.get("error"),
        )
        if "error" in result and status < 400:
            return jsonify(result), 502
        return jsonify(result), status if status >= 400 else 502
