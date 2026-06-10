"""REST CRUD for shared Postgres `items` table (Flyway schema + seed from Java)."""

from __future__ import annotations

from flask import Flask, g, jsonify, request

from exercises.web.db import DatabaseNotConfiguredError, connection


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
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT id, name, created_at FROM items ORDER BY id")
                    rows = cur.fetchall()
            return jsonify([_row_to_json(row) for row in rows])
        except DatabaseNotConfiguredError as exc:
            return jsonify(error=str(exc)), 503

    @app.post("/api/items")
    def create_item():
        name = _read_name(request.get_json(silent=True))
        if not name:
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
            return jsonify(_row_to_json(row)), 201
        except DatabaseNotConfiguredError as exc:
            return jsonify(error=str(exc)), 503

    @app.get("/api/items/<int:item_id>")
    def get_item(item_id: int):
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT id, name, created_at FROM items WHERE id = %s",
                        (item_id,),
                    )
                    row = cur.fetchone()
            if row is None:
                return jsonify(error="not found"), 404
            return jsonify(_row_to_json(row))
        except DatabaseNotConfiguredError as exc:
            return jsonify(error=str(exc)), 503

    @app.put("/api/items/<int:item_id>")
    @app.patch("/api/items/<int:item_id>")
    def update_item(item_id: int):
        name = _read_name(request.get_json(silent=True))
        if not name:
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
                return jsonify(error="not found"), 404
            return jsonify(_row_to_json(row))
        except DatabaseNotConfiguredError as exc:
            return jsonify(error=str(exc)), 503

    @app.delete("/api/items/<int:item_id>")
    def delete_item(item_id: int):
        try:
            with connection(request_id=_request_id()) as conn:
                with conn.cursor() as cur:
                    cur.execute("DELETE FROM items WHERE id = %s RETURNING id", (item_id,))
                    row = cur.fetchone()
                conn.commit()
            if row is None:
                return jsonify(error="not found"), 404
            return "", 204
        except DatabaseNotConfiguredError as exc:
            return jsonify(error=str(exc)), 503
