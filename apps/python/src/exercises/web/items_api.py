"""REST CRUD for shared Postgres `items` table (Flyway schema + seed from Java)."""

from __future__ import annotations

import logging

from flask import Flask, g, jsonify, request

from exercises.web.controller_logging import log_error, log_received, log_succeeded, log_trace, log_warn
from exercises.web.db import DatabaseNotConfiguredError, connection

SOURCE = "src/exercises/web/items_api.py"
_LOG = logging.getLogger(__name__)


def _row_to_json(row: tuple) -> dict:
    item_id, name, created_at = row
    return {
        "id": item_id,
        "name": name,
        "createdAt": created_at.isoformat().replace("+00:00", "Z"),
    }


def _read_name(payload: dict | None) -> str | None:
    if not payload:
        return None
    name = payload.get("name")
    if not isinstance(name, str):
        return None
    trimmed = name.strip()
    return trimmed or None


def register_items_routes(app: Flask) -> None:
    def _request_id() -> str | None:
        return getattr(g, "request_id", None)

    @app.get("/api/items")
    def list_items():
        log_received(_LOG, "list_items", SOURCE, "GET", "/api/items")
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT id, name, created_at FROM items ORDER BY id")
                    rows = cur.fetchall()
            result = [_row_to_json(row) for row in rows]
            log_succeeded(_LOG, "list_items", SOURCE, count=len(result))
            log_trace(_LOG, "list_items", SOURCE, "list_items result", items=result)
            return jsonify(result)
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "list_items",
                SOURCE,
                "list_items database not configured",
                target_service="postgres",
                error=str(exc),
            )
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            log_error(
                _LOG,
                "list_items",
                SOURCE,
                "list_items failed",
                exc=exc,
                target_service="postgres",
                error=str(exc),
            )
            return jsonify(error="Internal server error"), 500

    @app.post("/api/items")
    def create_item():
        payload = request.get_json(silent=True)
        name = _read_name(payload)
        log_received(_LOG, "create_item", SOURCE, "POST", "/api/items", item_name=name)
        if not name:
            log_warn(
                _LOG,
                "create_item",
                SOURCE,
                "create_item validation failed",
                item_name=name,
                reason="blank-name",
            )
            return jsonify(error="name must not be blank"), 400
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO items (name, created_at) VALUES (%s, NOW()) "
                        "RETURNING id, name, created_at",
                        (name,),
                    )
                    row = cur.fetchone()
                conn.commit()
            body = _row_to_json(row)
            log_succeeded(_LOG, "create_item", SOURCE, item_id=body["id"], item_name=body["name"])
            return jsonify(body), 201
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "create_item",
                SOURCE,
                "create_item database not configured",
                target_service="postgres",
                item_name=name,
                error=str(exc),
            )
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            log_error(
                _LOG,
                "create_item",
                SOURCE,
                "create_item failed",
                exc=exc,
                target_service="postgres",
                item_name=name,
                error=str(exc),
            )
            return jsonify(error="Internal server error"), 500

    @app.get("/api/items/<int:item_id>")
    def get_item(item_id: int):
        log_received(
            _LOG,
            "get_item",
            SOURCE,
            "GET",
            "/api/items/{item_id}",
            item_id=item_id,
        )
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT id, name, created_at FROM items WHERE id = %s",
                        (item_id,),
                    )
                    row = cur.fetchone()
            if row is None:
                log_warn(
                    _LOG,
                    "get_item",
                    SOURCE,
                    "get_item not found",
                    item_id=item_id,
                )
                return jsonify(error="not found"), 404
            body = _row_to_json(row)
            log_succeeded(_LOG, "get_item", SOURCE, item_id=item_id, item_name=body["name"])
            return jsonify(body)
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "get_item",
                SOURCE,
                "get_item database not configured",
                target_service="postgres",
                item_id=item_id,
                error=str(exc),
            )
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            log_error(
                _LOG,
                "get_item",
                SOURCE,
                "get_item failed",
                exc=exc,
                target_service="postgres",
                item_id=item_id,
                error=str(exc),
            )
            return jsonify(error="Internal server error"), 500

    @app.put("/api/items/<int:item_id>")
    @app.patch("/api/items/<int:item_id>")
    def update_item(item_id: int):
        payload = request.get_json(silent=True)
        name = _read_name(payload)
        method = request.method
        log_received(
            _LOG,
            "update_item",
            SOURCE,
            method,
            "/api/items/{item_id}",
            item_id=item_id,
            item_name=name,
        )
        if not name:
            log_warn(
                _LOG,
                "update_item",
                SOURCE,
                "update_item validation failed",
                item_id=item_id,
                item_name=name,
                reason="blank-name",
            )
            return jsonify(error="name must not be blank"), 400
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "UPDATE items SET name = %s WHERE id = %s "
                        "RETURNING id, name, created_at",
                        (name, item_id),
                    )
                    row = cur.fetchone()
                conn.commit()
            if row is None:
                log_warn(
                    _LOG,
                    "update_item",
                    SOURCE,
                    "update_item not found",
                    item_id=item_id,
                    item_name=name,
                )
                return jsonify(error="not found"), 404
            body = _row_to_json(row)
            log_succeeded(_LOG, "update_item", SOURCE, item_id=item_id, item_name=body["name"])
            return jsonify(body)
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "update_item",
                SOURCE,
                "update_item database not configured",
                target_service="postgres",
                item_id=item_id,
                item_name=name,
                error=str(exc),
            )
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            log_error(
                _LOG,
                "update_item",
                SOURCE,
                "update_item failed",
                exc=exc,
                target_service="postgres",
                item_id=item_id,
                item_name=name,
                error=str(exc),
            )
            return jsonify(error="Internal server error"), 500

    @app.delete("/api/items/<int:item_id>")
    def delete_item(item_id: int):
        log_received(
            _LOG,
            "delete_item",
            SOURCE,
            "DELETE",
            "/api/items/{item_id}",
            item_id=item_id,
        )
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute("DELETE FROM items WHERE id = %s RETURNING id", (item_id,))
                    row = cur.fetchone()
                conn.commit()
            if row is None:
                log_warn(
                    _LOG,
                    "delete_item",
                    SOURCE,
                    "delete_item not found",
                    item_id=item_id,
                )
                return jsonify(error="not found"), 404
            log_succeeded(_LOG, "delete_item", SOURCE, item_id=item_id)
            return "", 204
        except DatabaseNotConfiguredError as exc:
            log_warn(
                _LOG,
                "delete_item",
                SOURCE,
                "delete_item database not configured",
                target_service="postgres",
                item_id=item_id,
                error=str(exc),
            )
            return jsonify(error=str(exc)), 503
        except Exception as exc:
            log_error(
                _LOG,
                "delete_item",
                SOURCE,
                "delete_item failed",
                exc=exc,
                target_service="postgres",
                item_id=item_id,
                error=str(exc),
            )
            return jsonify(error="Internal server error"), 500
