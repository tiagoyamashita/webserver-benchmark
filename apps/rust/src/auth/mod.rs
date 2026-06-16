mod cookies;
mod handlers;
mod middleware;
pub mod password;
mod repository;
mod service;
mod session;

pub use cookies::http_access_session_id;
pub use middleware::{bootstrap_page_session, require_logged_in_user, resolve_session, session_log_span};
pub use session::redis_url_from_env;
pub use session::SessionConfig;

use redis::aio::ConnectionManager;

#[derive(Clone)]
pub struct AuthState {
    pub redis: ConnectionManager,
    pub config: SessionConfig,
}

#[derive(Clone, Debug)]
pub struct CurrentSession(pub session::SharedSession);

pub async fn connect_redis() -> Result<ConnectionManager, redis::RedisError> {
    let url = redis_url_from_env().ok_or_else(|| {
        redis::RedisError::from((
            redis::ErrorKind::InvalidClientConfig,
            "REDIS_URL or REDIS_HOST not set",
        ))
    })?;
    let client = redis::Client::open(url)?;
    ConnectionManager::new(client).await
}

pub async fn verify_redis_startup(auth: &AuthState) {
    let mut conn = auth.redis.clone();
    let host = session::read_env("REDIS_HOST", "127.0.0.1");
    let port = session::read_env("REDIS_PORT", "6379");
    let url = format!("redis://{host}:{port}");
    match repository::ping(&mut conn).await {
        Ok(pong) => tracing::info!(
            source = "src/auth/mod.rs",
            url = %url,
            pong = %pong,
            "Redis startup verify succeeded"
        ),
        Err(err) => tracing::warn!(
            source = "src/auth/mod.rs",
            url = %url,
            error = %err,
            "Redis startup verify failed"
        ),
    }
}

pub use handlers::{current_session, ensure_session, login, logout, refresh_session};
