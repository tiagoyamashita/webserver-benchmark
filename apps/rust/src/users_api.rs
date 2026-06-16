//! Public user registration (`POST /api/users`).

use axum::extract::{Extension, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::Deserialize;
use serde::Serialize;

use crate::app::AppState;
use crate::auth::password;
use crate::request_id::RequestId;

const SOURCE: &str = "src/users_api.rs";

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateUserBody {
    pub name: String,
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateUserResponse {
    pub id: i64,
    pub name: String,
    pub email: String,
    pub created_at: String,
}

pub async fn create_user(
    State(state): State<AppState>,
    Extension(request_id): Extension<RequestId>,
    Json(body): Json<CreateUserBody>,
) -> Response {
    tracing::info!(
        source = SOURCE,
        controller = "create_user",
        method = "POST",
        path = "/api/users",
        request_id = %request_id.0,
        "create_user request received"
    );
    let name = body.name.trim();
    let email = body.email.trim();
    if name.is_empty() || email.is_empty() || body.password.len() < 8 {
        return (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({ "error": "name, email, and password (min 8 chars) are required" })),
        )
            .into_response();
    }
    let Some(pool) = state.pg_pool.as_ref() else {
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({ "error": "Postgres not configured" })),
        )
            .into_response();
    };
    let password_hash = match password::hash_password(&body.password) {
        Ok(hash) => hash,
        Err(err) => {
            tracing::warn!(source = SOURCE, error = %err, "password hash failed");
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({ "error": "password hash failed" })),
            )
                .into_response();
        }
    };
    match crate::db::insert_user_with_password(
        pool,
        name,
        email,
        Some(password_hash.as_str()),
        Some(&request_id.0),
    )
    .await
    {
        Ok(user) => {
            tracing::info!(
                source = SOURCE,
                controller = "create_user",
                id = user.id,
                email = %user.email,
                "create_user succeeded"
            );
            (
                StatusCode::CREATED,
                Json(CreateUserResponse {
                    id: user.id,
                    name: user.name,
                    email: user.email,
                    created_at: user.created_at,
                }),
            )
                .into_response()
        }
        Err(err) => {
            let message = err.to_string();
            let status = if message.contains("duplicate") || message.contains("unique") {
                StatusCode::CONFLICT
            } else {
                StatusCode::INTERNAL_SERVER_ERROR
            };
            tracing::warn!(source = SOURCE, error = %message, "create_user failed");
            (
                status,
                Json(serde_json::json!({ "error": if status == StatusCode::CONFLICT { "Email already registered" } else { message.as_str() } })),
            )
                .into_response()
        }
    }
}
