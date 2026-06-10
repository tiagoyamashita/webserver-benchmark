use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use serde::{Deserialize, Serialize};
use sqlx::PgPool;

const SOURCE: &str = "src/items.rs";

#[derive(Deserialize)]
pub struct CreateItemQuery {
    pub name: String,
}

#[derive(Debug, Serialize)]
pub struct ItemResponse {
    pub id: i64,
    pub name: String,
    #[serde(rename = "createdAt")]
    pub created_at: String,
}

#[derive(Serialize)]
pub struct CreateItemResponse {
    pub ok: bool,
    pub name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub created_at: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// `GET /api/items` — lists rows from Postgres `items` (Flyway schema + seed from Java).
pub async fn list_items(pool: PgPool, request_id: Option<&str>) -> impl IntoResponse {
    tracing::info!(
        source = SOURCE,
        controller = "list_items",
        method = "GET",
        path = "/api/items",
        request_id = ?request_id,
        "list_items request received"
    );
    match crate::db::list_items(&pool, request_id).await {
        Ok(rows) => {
            let count = rows.len();
            let responses: Vec<ItemResponse> = rows
                .into_iter()
                .map(|row| ItemResponse {
                    id: row.id,
                    name: row.name,
                    created_at: row.created_at,
                })
                .collect();
            tracing::info!(
                source = SOURCE,
                controller = "list_items",
                count = count,
                "list_items succeeded"
            );
            tracing::trace!(
                source = SOURCE,
                controller = "list_items",
                items = ?responses,
                "list_items result"
            );
            (StatusCode::OK, Json(responses)).into_response()
        }
        Err(e) => {
            tracing::error!(
                source = SOURCE,
                controller = "list_items",
                error = %e,
                "list_items failed"
            );
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({ "error": e.to_string() })),
            )
                .into_response()
        }
    }
}

/// `POST /api/items?name=...` — inserts into Postgres `items` (Flyway schema from Java).
pub async fn create_item(pool: PgPool, query: CreateItemQuery, request_id: Option<&str>) -> impl IntoResponse {
    let name = query.name.trim().to_string();
    tracing::info!(
        source = SOURCE,
        controller = "create_item",
        method = "POST",
        path = "/api/items",
        name = %name,
        request_id = ?request_id,
        "create_item request received"
    );
    if name.is_empty() {
        tracing::warn!(
            source = SOURCE,
            controller = "create_item",
            name = %query.name,
            reason = "blank-name",
            "create_item validation failed"
        );
        return (
            StatusCode::BAD_REQUEST,
            Json(CreateItemResponse {
                ok: false,
                name: String::new(),
                id: None,
                created_at: None,
                error: Some("name must not be blank".into()),
            }),
        )
            .into_response();
    }

    match crate::db::insert_item(&pool, &name, request_id).await {
        Ok(row) => {
            tracing::info!(
                source = SOURCE,
                controller = "create_item",
                id = row.id,
                name = %row.name,
                "create_item succeeded"
            );
            (
                StatusCode::CREATED,
                Json(CreateItemResponse {
                    ok: true,
                    name: row.name,
                    id: Some(row.id),
                    created_at: Some(row.created_at),
                    error: None,
                }),
            )
                .into_response()
        }
        Err(e) => {
            tracing::error!(
                source = SOURCE,
                controller = "create_item",
                name = %name,
                error = %e,
                "create_item failed"
            );
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(CreateItemResponse {
                    ok: false,
                    name,
                    id: None,
                    created_at: None,
                    error: Some(e.to_string()),
                }),
            )
                .into_response()
        }
    }
}
