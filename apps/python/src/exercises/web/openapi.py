"""OpenAPI 3 spec for `/api/items` CRUD (dashboard routes excluded)."""

from __future__ import annotations

_ITEM_SCHEMA = {
    "type": "object",
    "required": ["id", "name", "createdAt"],
    "properties": {
        "id": {"type": "integer", "format": "int64", "example": 1},
        "name": {"type": "string", "example": "Widget"},
        "createdAt": {
            "type": "string",
            "format": "date-time",
            "example": "2026-01-01T00:00:00Z",
        },
    },
}

_ITEM_REQUEST_SCHEMA = {
    "type": "object",
    "required": ["name"],
    "properties": {
        "name": {"type": "string", "example": "Widget"},
    },
}

_ERROR_SCHEMA = {
    "type": "object",
    "required": ["error"],
    "properties": {
        "error": {"type": "string", "example": "name must not be blank"},
    },
}

_ITEMS_TAG = "Items"


def _request_id_header() -> dict:
    return {
        "name": "X-Request-ID",
        "in": "header",
        "required": False,
        "description": (
            "Correlation id for logs and Postgres trace; generated if omitted; echoed in response."
        ),
        "schema": {"type": "string"},
    }


def build_openapi_spec() -> dict:
    """Return the OpenAPI document served at `/api-docs/openapi.json`."""
    item_responses = {
        "200": {
            "description": "Item",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/Item"}}},
        },
        "404": {
            "description": "Not found",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}},
        },
        "503": {
            "description": "Postgres not configured",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}},
        },
        "500": {
            "description": "Database error",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}},
        },
    }
    return {
        "openapi": "3.0.3",
        "info": {
            "title": "WebServer BenchMark Python API",
            "version": "1.0",
            "description": (
                "REST CRUD for `/api/items`. Dashboard, observability, and stack-ping "
                "routes are excluded."
            ),
        },
        "tags": [
            {
                "name": _ITEMS_TAG,
                "description": "Shared PostgreSQL `items` table (Flyway schema + seed from Java)",
            }
        ],
        "paths": {
            "/api/items": {
                "get": {
                    "tags": [_ITEMS_TAG],
                    "summary": "List items",
                    "operationId": "listItems",
                    "parameters": [_request_id_header()],
                    "responses": {
                        "200": {
                            "description": "All items",
                            "content": {
                                "application/json": {
                                    "schema": {
                                        "type": "array",
                                        "items": {"$ref": "#/components/schemas/Item"},
                                    }
                                }
                            },
                        },
                        "503": item_responses["503"],
                        "500": item_responses["500"],
                    },
                },
                "post": {
                    "tags": [_ITEMS_TAG],
                    "summary": "Create item",
                    "operationId": "createItem",
                    "parameters": [_request_id_header()],
                    "requestBody": {
                        "required": True,
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/CreateItemRequest"}
                            }
                        },
                    },
                    "responses": {
                        "201": {
                            "description": "Created",
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/Item"}
                                }
                            },
                        },
                        "400": {
                            "description": "Blank name",
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/ApiError"}
                                }
                            },
                        },
                        "503": item_responses["503"],
                        "500": item_responses["500"],
                    },
                },
            },
            "/api/items/{item_id}": {
                "get": {
                    "tags": [_ITEMS_TAG],
                    "summary": "Get item by id",
                    "operationId": "getItem",
                    "parameters": [_request_id_header(), _item_id_param()],
                    "responses": item_responses,
                },
                "put": _update_item_operation("updateItem", "Replace item name"),
                "patch": _update_item_operation("patchItem", "Patch item name"),
                "delete": {
                    "tags": [_ITEMS_TAG],
                    "summary": "Delete item",
                    "operationId": "deleteItem",
                    "parameters": [_request_id_header(), _item_id_param()],
                    "responses": {
                        "204": {"description": "Deleted"},
                        "404": item_responses["404"],
                        "503": item_responses["503"],
                        "500": item_responses["500"],
                    },
                },
            },
        },
        "components": {
            "schemas": {
                "Item": _ITEM_SCHEMA,
                "CreateItemRequest": _ITEM_REQUEST_SCHEMA,
                "UpdateItemRequest": _ITEM_REQUEST_SCHEMA,
                "ApiError": _ERROR_SCHEMA,
            }
        },
    }


def _update_item_operation(operation_id: str, summary: str) -> dict:
    return {
        "tags": [_ITEMS_TAG],
        "summary": summary,
        "operationId": operation_id,
        "parameters": [_request_id_header(), _item_id_param()],
        "requestBody": {
            "required": True,
            "content": {
                "application/json": {
                    "schema": {"$ref": "#/components/schemas/UpdateItemRequest"}
                }
            },
        },
        "responses": {
            "200": {
                "description": "Updated item",
                "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/Item"}}
                },
            },
            "400": {
                "description": "Blank name",
                "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}
                },
            },
            "404": {
                "description": "Not found",
                "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}
                },
            },
            "503": {
                "description": "Postgres not configured",
                "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}
                },
            },
            "500": {
                "description": "Database error",
                "content": {
                    "application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}
                },
            },
        },
    }


def _item_id_param() -> dict:
    return {
        "name": "item_id",
        "in": "path",
        "required": True,
        "schema": {"type": "integer", "format": "int64"},
        "example": 1,
    }
