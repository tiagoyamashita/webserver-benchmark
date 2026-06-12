use redis::aio::ConnectionManager;
use redis::AsyncCommands;

use super::session::{SessionConfig, SharedSession};

pub async fn save(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    session: &SharedSession,
) -> Result<(), redis::RedisError> {
    let key = config.redis_key(&session.session_id);
    let json = serde_json::to_string(session).map_err(|e| {
        redis::RedisError::from((
            redis::ErrorKind::TypeError,
            "session json",
            e.to_string(),
        ))
    })?;
    conn.set_ex(key, json, config.ttl_secs).await
}

pub async fn find_by_id(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    session_id: &str,
) -> Result<Option<SharedSession>, redis::RedisError> {
    let key = config.redis_key(session_id);
    let json: Option<String> = conn.get(key).await?;
    match json {
        Some(body) if !body.is_empty() => {
            let session: SharedSession = serde_json::from_str(&body).map_err(|e| {
                redis::RedisError::from((
                    redis::ErrorKind::TypeError,
                    "session json",
                    e.to_string(),
                ))
            })?;
            Ok(Some(session))
        }
        _ => Ok(None),
    }
}

pub async fn delete(
    conn: &mut ConnectionManager,
    config: &SessionConfig,
    session_id: &str,
) -> Result<(), redis::RedisError> {
    let key = config.redis_key(session_id);
    conn.del(key).await
}

pub async fn ping(conn: &mut ConnectionManager) -> Result<String, redis::RedisError> {
    redis::cmd("PING").query_async(conn).await
}
