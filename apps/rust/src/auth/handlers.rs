use axum::extract::{Extension, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use chrono::Utc;

use crate::app::AppState;
use crate::request_id::RequestHttpSnapshot;

use super::cookies::{append_clear_session_cookie, append_session_cookie};
use super::service::{self, AuthServiceError};
use super::session::{EnsureSessionRequest, LoginRequest, SessionResponse};
use super::session::SharedSession;
use super::CurrentSession;

const SOURCE: &str = "src/auth/handlers.rs";

fn optional_shared_session(
    current: &Option<Extension<CurrentSession>>,
) -> Option<&SharedSession> {
    current.as_ref().map(|ext| &ext.0.0)
}

fn log_controller_received(
    controller: &str,
    method: &str,
    path: &str,
    http: &RequestHttpSnapshot,
) {
    crate::obs_log::log_controller_received(
        SOURCE,
        controller,
        method,
        path,
        &http.headers,
        &http.url_params,
        &http.body,
    );
}

pub async fn ensure_session(
    State(state): State<AppState>,
    Extension(http): Extension<RequestHttpSnapshot>,
    current: Option<Extension<CurrentSession>>,
    body: Option<Json<EnsureSessionRequest>>,
) -> Response {
    let Some(auth) = state.auth.as_ref() else {
        log_controller_received("ensure_session", "POST", "/api/auth/ensure", &http);
        return auth_unavailable();
    };
    let client_id = body.and_then(|Json(b)| b.session_id);
    let mut conn = auth.redis.clone();
    let request_session = optional_shared_session(&current);
    match service::ensure_session(
        &mut conn,
        &auth.config,
        client_id.as_deref(),
        request_session,
    )
    .await
    {
        Ok(result) => {
            let redis_key = auth.config.redis_key(&result.session.session_id);
            let payload = SessionResponse::from_session(&result.session, redis_key);
            let status = if result.created {
                StatusCode::CREATED
            } else {
                StatusCode::OK
            };
            if status != StatusCode::OK {
                log_controller_received("ensure_session", "POST", "/api/auth/ensure", &http);
                tracing::info!(
                    source = SOURCE,
                    controller = "ensure_session",
                    session_id = %result.session.session_id,
                    session_created = result.created,
                    user_id = result.session.user_id,
                    "ensure_session succeeded"
                );
            }
            let mut res = (status, Json(payload)).into_response();
            append_session_cookie(res.headers_mut(), &auth.config, &result.session.session_id);
            res
        }
        Err(err) => {
            log_controller_received("ensure_session", "POST", "/api/auth/ensure", &http);
            tracing::warn!(
                source = SOURCE,
                controller = "ensure_session",
                error = %err,
                "ensure_session failed"
            );
            (
                StatusCode::SERVICE_UNAVAILABLE,
                Json(serde_json::json!({ "error": err.to_string() })),
            )
                .into_response()
        }
    }
}

pub async fn login(
    State(state): State<AppState>,
    Extension(http): Extension<RequestHttpSnapshot>,
    Json(body): Json<LoginRequest>,
) -> Response {
    log_controller_received("login", "POST", "/api/auth/login", &http);
    let Some(auth) = state.auth.as_ref() else {
        return auth_unavailable();
    };
    let Some(pool) = state.pg_pool.as_ref() else {
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({ "error": "Postgres not configured" })),
        )
            .into_response();
    };
    let mut conn = auth.redis.clone();
    match service::login(&mut conn, &auth.config, pool, &body).await {
        Ok(session) => {
            let redis_key = auth.config.redis_key(&session.session_id);
            let payload = SessionResponse::from_session(&session, redis_key);
            tracing::info!(
                source = SOURCE,
                controller = "login",
                session_id = %session.session_id,
                user_id = session.user_id,
                "login succeeded"
            );
            let mut res = (StatusCode::CREATED, Json(payload)).into_response();
            append_session_cookie(res.headers_mut(), &auth.config, &session.session_id);
            res
        }
        Err(err) => map_auth_error(err, "login"),
    }
}

pub async fn logout(
    State(state): State<AppState>,
    Extension(http): Extension<RequestHttpSnapshot>,
    current: Option<Extension<CurrentSession>>,
) -> Response {
    log_controller_received("logout", "POST", "/api/auth/logout", &http);
    let Some(auth) = state.auth.as_ref() else {
        return auth_unavailable();
    };
    let Some(Extension(CurrentSession(session))) = current else {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "No active session" })),
        )
            .into_response();
    };
    let mut conn = auth.redis.clone();
    if let Err(err) = service::logout(&mut conn, &auth.config, &session.session_id).await {
        tracing::warn!(
            source = SOURCE,
            controller = "logout",
            error = %err,
            "logout redis delete failed"
        );
    }
    tracing::info!(
        source = SOURCE,
        controller = "logout",
        session_id = %session.session_id,
        "logout succeeded"
    );
    let mut res = StatusCode::NO_CONTENT.into_response();
    append_clear_session_cookie(res.headers_mut(), &auth.config);
    res
}

pub async fn refresh_session(
    State(state): State<AppState>,
    Extension(http): Extension<RequestHttpSnapshot>,
    current: Option<Extension<CurrentSession>>,
) -> Response {
    log_controller_received("refresh_session", "POST", "/api/auth/refresh", &http);
    let Some(auth) = state.auth.as_ref() else {
        return auth_unavailable();
    };
    let previous_id = current
        .as_ref()
        .map(|Extension(CurrentSession(session))| session.session_id.clone());
    let request_session = optional_shared_session(&current);
    let mut conn = auth.redis.clone();
    match service::refresh_session(&mut conn, &auth.config, request_session).await {
        Ok(session) => {
            let redis_key = auth.config.redis_key(&session.session_id);
            let payload = SessionResponse::from_session(&session, redis_key);
            tracing::info!(
                source = SOURCE,
                controller = "refresh_session",
                previous_session_id = previous_id.as_deref().unwrap_or(""),
                session_id = %session.session_id,
                user_id = session.user_id,
                "refresh_session succeeded"
            );
            let mut res = (StatusCode::CREATED, Json(payload)).into_response();
            append_session_cookie(res.headers_mut(), &auth.config, &session.session_id);
            res
        }
        Err(err) => {
            tracing::warn!(
                source = SOURCE,
                controller = "refresh_session",
                error = %err,
                "refresh_session failed"
            );
            (
                StatusCode::SERVICE_UNAVAILABLE,
                Json(serde_json::json!({ "error": err.to_string() })),
            )
                .into_response()
        }
    }
}

pub async fn current_session(
    Extension(http): Extension<RequestHttpSnapshot>,
    State(state): State<AppState>,
    current: Option<Extension<CurrentSession>>,
) -> Response {
    log_controller_received("current_session", "GET", "/api/auth/session", &http);
    let Some(auth) = state.auth.as_ref() else {
        return auth_unavailable();
    };
    let Some(Extension(CurrentSession(session))) = current else {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "No active session" })),
        )
            .into_response();
    };
    if session.is_expired(Utc::now()) {
        return (
            StatusCode::UNAUTHORIZED,
            Json(serde_json::json!({ "error": "Session expired" })),
        )
            .into_response();
    }
    let redis_key = auth.config.redis_key(&session.session_id);
    let payload = SessionResponse::from_session(&session, redis_key);
    tracing::info!(
        source = SOURCE,
        controller = "current_session",
        session_id = %session.session_id,
        user_id = session.user_id,
        "current_session succeeded"
    );
    (StatusCode::OK, Json(payload)).into_response()
}

fn auth_unavailable() -> Response {
    (
        StatusCode::SERVICE_UNAVAILABLE,
        Json(serde_json::json!({ "error": "Redis session store not configured" })),
    )
        .into_response()
}

fn map_auth_error(err: AuthServiceError, action: &str) -> Response {
    match &err {
        AuthServiceError::BadRequest(msg) => tracing::warn!(
            source = SOURCE,
            action = action,
            error = %msg,
            "auth request rejected"
        ),
        AuthServiceError::NotFound(msg) => tracing::warn!(
            source = SOURCE,
            action = action,
            error = %msg,
            "auth user not found"
        ),
        AuthServiceError::Db(e) => tracing::warn!(
            source = SOURCE,
            action = action,
            error = %e,
            "auth database error"
        ),
        AuthServiceError::Redis(e) => tracing::warn!(
            source = SOURCE,
            action = action,
            error = %e,
            "auth redis error"
        ),
    }
    let (status, message) = match err {
        AuthServiceError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
        AuthServiceError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
        AuthServiceError::Db(_) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            "Database error".into(),
        ),
        AuthServiceError::Redis(_) => (
            StatusCode::SERVICE_UNAVAILABLE,
            "Redis error".into(),
        ),
    };
    (status, Json(serde_json::json!({ "error": message }))).into_response()
}
