use chrono::{Duration, Utc};
use redis::aio::ConnectionManager;
use uuid::Uuid;

use crate::db::UserRow;

use super::repository;
use super::session::{LoginRequest, SessionConfig, SharedSession};

pub struct EnsureSessionResult {
    pub session: SharedSession,
    pub created: bool,
}

pub async fn ensure_session(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    client_session_id: Option<&str>,
    request_session: Option<&SharedSession>,
) -> Result<EnsureSessionResult, redis::RedisError> {
    let now = Utc::now();
    if let Some(session) = request_session {
        if !session.is_expired(now) {
            return Ok(EnsureSessionResult {
                session: session.clone(),
                created: false,
            });
        }
    }
    if let Some(id) = client_session_id.map(str::trim).filter(|s| !s.is_empty()) {
        if let Some(session) = repository::find_by_id(conn, config, id).await? {
            if !session.is_expired(now) {
                return Ok(EnsureSessionResult {
                    session,
                    created: false,
                });
            }
            let _ = repository::delete(conn, config, id).await;
        }
    }
    let session = create_anonymous_session(conn, config).await?;
    Ok(EnsureSessionResult {
        session,
        created: true,
    })
}

pub async fn login(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    pool: &sqlx::PgPool,
    body: &LoginRequest,
) -> Result<SharedSession, AuthServiceError> {
    let user = resolve_user(pool, body).await?;
    let issued_at = Utc::now();
    let expires_at = issued_at + Duration::seconds(config.ttl_secs as i64);
    let session = SharedSession {
        session_id: Uuid::new_v4().to_string(),
        user_id: user.id,
        email: Some(user.email),
        name: user.name,
        issued_at,
        expires_at,
        issuer: "rust".into(),
    };
    repository::save(conn, config, &session)
        .await
        .map_err(AuthServiceError::Redis)?;
    Ok(session)
}

pub async fn logout(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    session_id: &str,
) -> Result<(), redis::RedisError> {
    repository::delete(conn, config, session_id).await
}

async fn create_anonymous_session(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
) -> Result<SharedSession, redis::RedisError> {
    let issued_at = Utc::now();
    let expires_at = issued_at + Duration::seconds(config.ttl_secs as i64);
    let session = SharedSession {
        session_id: Uuid::new_v4().to_string(),
        user_id: 0,
        email: None,
        name: "Guest".into(),
        issued_at,
        expires_at,
        issuer: "rust".into(),
    };
    repository::save(conn, config, &session).await?;
    Ok(session)
}

async fn resolve_user(pool: &sqlx::PgPool, body: &LoginRequest) -> Result<UserRow, AuthServiceError> {
    if let Some(email) = body.email.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        return crate::db::find_user_by_email(pool, email)
            .await
            .map_err(AuthServiceError::Db)?
            .ok_or_else(|| AuthServiceError::NotFound(format!("No user with email {email}")));
    }
    if let Some(user_id) = body.user_id {
        return crate::db::find_user_by_id(pool, user_id)
            .await
            .map_err(AuthServiceError::Db)?
            .ok_or_else(|| AuthServiceError::NotFound(format!("No user with id {user_id}")));
    }
    Err(AuthServiceError::BadRequest(
        "email or userId is required".into(),
    ))
}

#[derive(Debug)]
pub enum AuthServiceError {
    BadRequest(String),
    NotFound(String),
    Db(sqlx::Error),
    Redis(redis::RedisError),
}
