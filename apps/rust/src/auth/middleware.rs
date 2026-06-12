use axum::extract::{Request, State};
use axum::http::Method;
use axum::middleware::Next;
use axum::response::Response;
use chrono::Utc;

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
