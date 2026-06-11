"""Serve OpenAPI JSON and Swagger UI for the items API."""

from __future__ import annotations

import logging

from flask import Flask, jsonify, render_template

from exercises.web.controller_logging import log_received, log_succeeded
from exercises.web.openapi import build_openapi_spec

SOURCE = "src/exercises/web/openapi_routes.py"
_LOG = logging.getLogger(__name__)


def register_openapi_routes(app: Flask) -> None:
    @app.get("/api-docs/openapi.json")
    def openapi_json():
        log_received(_LOG, "openapi_json", SOURCE, "GET", "/api-docs/openapi.json")
        spec = build_openapi_spec()
        log_succeeded(_LOG, "openapi_json", SOURCE, path_count=len(spec.get("paths", {})))
        return jsonify(spec)

    @app.get("/swagger-ui")
    @app.get("/swagger-ui/")
    def swagger_ui():
        log_received(_LOG, "swagger_ui", SOURCE, "GET", "/swagger-ui")
        page = render_template("swagger_ui.html")
        log_succeeded(_LOG, "swagger_ui", SOURCE, template="swagger_ui.html")
        return page
