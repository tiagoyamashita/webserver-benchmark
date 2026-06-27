use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// Session payload in Redis at `webserver-benchmark:session:{sessionId}` (shared with Java).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SharedSession {
    pub session_id: String,
    pub user_id: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    pub name: String,
    pub issued_at: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub issuer: String,
}

impl SharedSession {
    pub fn is_expired(&self, now: DateTime<Utc>) -> bool {
        !self.expires_at.gt(&now)
    }
}

#[derive(Debug, Clone)]
pub struct SessionConfig {
    pub redis_key_prefix: String,
    pub ttl_secs: u64,
    pub cookie_name: String,
}

impl SessionConfig {
    pub fn from_env() -> Self {
        Self {
            redis_key_prefix: read_env(
                "WEBSERVER_BENCHMARK_SESSION_REDIS_PREFIX",
                "webserver-benchmark:session:",
            ),
            ttl_secs: 86_400,
            cookie_name: read_env("WEBSERVER_BENCHMARK_SESSION_COOKIE", "webserver_benchmark_session"),
        }
    }

    pub fn redis_key(&self, session_id: &str) -> String {
        format!("{}{}", self.redis_key_prefix, session_id)
    }
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SessionResponse {
    pub session_id: String,
    pub user_id: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    pub name: String,
    pub issued_at: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub issuer: String,
    pub redis_key: String,
}

impl SessionResponse {
    pub fn from_session(session: &SharedSession, redis_key: String) -> Self {
        Self {
            session_id: session.session_id.clone(),
            user_id: session.user_id,
            email: session.email.clone(),
            name: session.name.clone(),
            issued_at: session.issued_at,
            expires_at: session.expires_at,
            issuer: session.issuer.clone(),
            redis_key,
        }
    }
}

#[derive(Debug, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct EnsureSessionRequest {
    pub session_id: Option<String>,
}

#[derive(Debug, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LoginRequest {
    pub email: Option<String>,
    pub user_id: Option<i64>,
    pub password: Option<String>,
}

pub fn read_env(key: &str, default: &str) -> String {
    std::env::var(key)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| default.to_string())
}

pub fn redis_url_from_env() -> Option<String> {
    if let Ok(url) = std::env::var("REDIS_URL") {
        let trimmed = url.trim();
        if !trimmed.is_empty() {
            return Some(trimmed.to_string());
        }
    }
    let host = std::env::var("REDIS_HOST").ok()?;
    if host.trim().is_empty() {
        return None;
    }
    let port = read_env("REDIS_PORT", "6379");
    Some(format!("redis://{}:{}", host.trim(), port))
}
