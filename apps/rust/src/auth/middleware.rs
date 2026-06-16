use axum::extract::{Request, State};
use axum::http::Method;
use axum::http::StatusCode;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use chrono::Utc;
use tracing::Instrument;

use crate::app::AppState;

use super::cookies::{append_session_cookie, session_id_candidates};
use super::repository;
use super::service;
use super::CurrentSession;

pub async fn resolve_session(
    State(state): State<AppState>,
    mut req: Request,
    next: Next,
) -> Response {
    if let Some(auth) = state.auth.as_ref() {
        let mut conn = auth.redis.clone();
        let now = Utc::now();
        for session_id in session_id_candidates(req.headers(), &auth.config.cookie_name) {
            match repository::find_by_id(&mut conn, &auth.config, &session_id).await {
                Ok(Some(session)) if !session.is_expired(now) => {
                    req.extensions_mut().insert(CurrentSession(session));
                    break;
                }
                Ok(Some(session)) => {
                    let _ = repository::delete(&mut conn, &auth.config, &session.session_id).await;
                }
                Ok(None) => {}
                Err(err) => {
                    tracing::warn!(
                        source = "src/auth/middleware.rs",
                        error = %err,
                        "resolve_session redis lookup failed"
                    );
                }
            }
        }
    }
    next.run(req).await
}

pub async fn bootstrap_page_session(
    State(state): State<AppState>,
    mut req: Request,
    next: Next,
) -> Response {
    let is_landing = req.method() == Method::GET && req.uri().path() == "/";
    let had_session = req.extensions().get::<CurrentSession>().is_some();
    let mut created_session = None;

    if is_landing && !had_session {
        if let Some(auth) = state.auth.as_ref() {
            let mut conn = auth.redis.clone();
            match service::ensure_session(&mut conn, &auth.config, None, None).await {
                Ok(result) => {
                    created_session = Some(result.session.clone());
                    req.extensions_mut()
                        .insert(CurrentSession(result.session));
                }
                Err(err) => {
                    tracing::warn!(
                        source = "src/auth/middleware.rs",
                        error = %err,
                        "bootstrap_page_session failed"
                    );
                }
            }
        }
    }

    let mut res = next.run(req).await;
    if let (Some(auth), Some(session)) = (state.auth.as_ref(), created_session) {
        if res.status().is_success() {
            append_session_cookie(res.headers_mut(), &auth.config, &session.session_id);
        }
    }
    res
}

/// Attach `session_id` to controller tracing fields (HTTP access logs run outside this span).
pub async fn session_log_span(req: Request, next: Next) -> Response {
    let session_id = req
        .extensions()
        .get::<CurrentSession>()
        .map(|session| session.0.session_id.clone());

    match session_id {
        Some(id) => {
            let span = tracing::info_span!("request", session_id = %id);
            next.run(req).instrument(span).await
        }
        None => next.run(req).await,
    }
}

fn is_public_path(method: &Method, path: &str) -> bool {
    if *method == Method::GET && (path == "/" || path == "/health" || path == "/metrics") {
        return true;
    }
    if path.starts_with("/api/auth/") {
        return true;
    }
    if *method == Method::POST && path == "/api/users" {
        return true;
    }
    if path.starts_with("/swagger-ui") || path.starts_with("/api-docs") {
        return true;
    }
    false
}

pub async fn require_logged_in_user(req: Request, next: Next) -> Response {
    let method = req.method().clone();
    let path = req.uri().path().to_string();
    if is_public_path(&method, &path) {
        return next.run(req).await;
    }
    let logged_in = req
        .extensions()
        .get::<CurrentSession>()
        .map(|session| session.0.user_id > 0 && session.0.email.is_some())
        .unwrap_or(false);
    if logged_in {
        return next.run(req).await;
    }
    (
        StatusCode::UNAUTHORIZED,
        axum::Json(serde_json::json!({ "error": "Sign in required" })),
    )
        .into_response()
}
