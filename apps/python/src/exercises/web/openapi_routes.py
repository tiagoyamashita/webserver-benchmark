"""Serve OpenAPI JSON and Swagger UI for the items API."""

from __future__ import annotations

from flask import Flask, jsonify, render_template

from exercises.web.openapi import build_openapi_spec


def register_openapi_routes(app: Flask) -> None:
    @app.get("/api-docs/openapi.json")
    def openapi_json():
        return jsonify(build_openapi_spec())

    @app.get("/swagger-ui")
    @app.get("/swagger-ui/")
    def swagger_ui():
        return render_template("swagger_ui.html")
