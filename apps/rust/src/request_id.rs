//! HTTP request id (`X-Request-ID`) for log and Postgres correlation.

use axum::http::{HeaderValue, Request, Response};
use axum::middleware::Next;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Debug)]
pub struct RequestId(pub String);

pub fn postgres_application_name(service: &str, request_id: &str) -> String {
    let value = format!("{service};req={request_id}");
    if value.len() <= 63 {
        value
    } else {
        value[..63].to_string()
    }
}

pub fn resolve_request_id(header: Option<&HeaderValue>) -> RequestId {
    if let Some(value) = header {
        if let Ok(text) = value.to_str() {
            let trimmed = text.trim();
            if (8..=64).contains(&trimmed.len())
                && trimmed
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
            {
                return RequestId(trimmed.to_string());
            }
        }
    }
    RequestId(generate_request_id())
}

fn generate_request_id() -> String {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let seq = COUNTER.fetch_add(1, Ordering::Relaxed);
    format!("{nanos:x}-{seq:x}")
}

pub async fn assign_request_id(mut req: Request, next: Next) -> Response {
    let request_id = resolve_request_id(req.headers().get("x-request-id"));
    let id = request_id.0.clone();
    req.extensions_mut().insert(request_id);
    let mut res = next.run(req).await;
    if let Ok(value) = HeaderValue::from_str(&id) {
        res.headers_mut().insert("x-request-id", value);
    }
    res
}
