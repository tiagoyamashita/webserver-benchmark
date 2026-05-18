use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use serde::{Deserialize, Serialize};
use sqlx::PgPool;

#[derive(Deserialize)]
pub struct CreateItemQuery {
    pub name: String,
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

/// `POST /api/items?name=...` — inserts into Postgres `items` (Flyway schema from Java).
pub async fn create_item(pool: PgPool, query: CreateItemQuery) -> impl IntoResponse {
    let name = query.name.trim().to_string();
    if name.is_empty() {
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

    match crate::db::insert_item(&pool, &name).await {
        Ok(row) => (
            StatusCode::CREATED,
            Json(CreateItemResponse {
                ok: true,
                name: row.name,
                id: Some(row.id),
                created_at: Some(row.created_at),
                error: None,
            }),
        )
            .into_response(),
        Err(e) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(CreateItemResponse {
                ok: false,
                name,
                id: None,
                created_at: None,
                error: Some(e.to_string()),
            }),
        )
            .into_response(),
    }
}
