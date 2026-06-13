//! OpenAPI spec + Swagger UI (utoipa), `/api/items` only — mirrors Java springdoc scope.

use crate::items::{CreateItemRequest, CreateItemResponse, ItemResponse};
use serde::Serialize;
use utoipa::OpenApi;

#[derive(Serialize, utoipa::ToSchema)]
pub struct ApiError {
    pub error: String,
}

/// `GET /api/items` — list all rows from the shared `items` table.
#[allow(dead_code)]
#[utoipa::path(
    get,
    path = "/api/items",
    tag = "Items",
    params(
        ("X-Request-ID" = Option<String>, Header, description = "Correlation id for logs and Postgres trace; generated if omitted; echoed in response"),
        ("X-Request-Origin" = Option<String>, Header, description = "Upstream service when relayed (e.g. exercises-java); logged as request_origin for tracing")
    ),
    responses(
        (status = 200, description = "All items", body = [ItemResponse]),
        (status = 503, description = "Postgres not configured", body = ApiError),
        (status = 500, description = "Database error", body = ApiError)
    )
)]
fn items_list() {}

/// `POST /api/items` with JSON `{"name": "…"}` — insert a row into the shared `items` table.
#[allow(dead_code)]
#[utoipa::path(
    post,
    path = "/api/items",
    tag = "Items",
    request_body = CreateItemRequest,
    params(
        ("X-Request-ID" = Option<String>, Header, description = "Correlation id for logs and Postgres trace; generated if omitted; echoed in response"),
        ("X-Request-Origin" = Option<String>, Header, description = "Upstream service when relayed (e.g. exercises-java); logged as request_origin for tracing")
    ),
    responses(
        (status = 201, description = "Created", body = CreateItemResponse),
        (status = 400, description = "Blank name", body = CreateItemResponse),
        (status = 503, description = "Postgres not configured", body = CreateItemResponse),
        (status = 500, description = "Database error", body = CreateItemResponse)
    )
)]
fn items_create() {}

#[derive(OpenApi)]
#[openapi(
    paths(items_list, items_create),
    components(schemas(ItemResponse, CreateItemRequest, CreateItemResponse, ApiError)),
    tags(
        (name = "Items", description = "Shared PostgreSQL `items` table (Flyway schema from Java)")
    ),
    info(
        title = "Exercises Rust API",
        version = "1.0",
        description = "REST API under `/api/items`. Dashboard, observability, and stack-ping routes are excluded."
    )
)]
pub struct ApiDoc;
