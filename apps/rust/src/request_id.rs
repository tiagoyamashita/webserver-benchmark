//! HTTP request id (`X-Request-ID`) for log and Postgres correlation.

use axum::extract::Request;
use axum::http::HeaderValue;
use axum::middleware::Next;
use axum::response::Response;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

pub const ORIGIN_HEADER: &str = "x-request-origin";

#[derive(Clone, Debug)]
pub struct RequestId(pub String);

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum RequestIdSource {
    ReceivedHeader,
    Generated,
}

#[derive(Clone, Debug)]
pub struct RequestOrigin(pub Option<String>);

/// Monotonic log line order within one HTTP request.
#[derive(Clone, Debug)]
pub struct RequestLogSeq(Arc<AtomicU64>);

impl RequestLogSeq {
    pub fn new() -> Self {
        Self(Arc::new(AtomicU64::new(0)))
    }

    pub fn next(&self) -> u64 {
        self.0.fetch_add(1, Ordering::Relaxed) + 1
    }
}

pub fn postgres_application_name(service: &str, request_id: &str) -> String {
    let value = format!("{service};req={request_id}");
    if value.len() <= 63 {
        value
    } else {
        value[..63].to_string()
    }
}

pub fn resolve_request_id(header: Option<&HeaderValue>) -> (RequestId, RequestIdSource) {
    if let Some(value) = header {
        if let Ok(text) = value.to_str() {
            let trimmed = text.trim();
            if is_safe_token(trimmed) {
                return (RequestId(trimmed.to_string()), RequestIdSource::ReceivedHeader);
            }
        }
    }
    (
        RequestId(generate_request_id()),
        RequestIdSource::Generated,
    )
}

pub fn resolve_request_origin(header: Option<&HeaderValue>) -> RequestOrigin {
    let origin = header.and_then(|value| value.to_str().ok()).and_then(|text| {
        let trimmed = text.trim();
        if trimmed.starts_with("exercises-") && is_safe_token(trimmed) {
            Some(trimmed.to_string())
        } else {
            None
        }
    });
    RequestOrigin(origin)
}

fn is_safe_token(value: &str) -> bool {
    (8..=64).contains(&value.len())
        && value
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-'))
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
    let (request_id, source) = resolve_request_id(req.headers().get("x-request-id"));
    let origin = resolve_request_origin(req.headers().get(ORIGIN_HEADER));
    let id = request_id.0.clone();
    req.extensions_mut().insert(request_id);
    req.extensions_mut().insert(source);
    req.extensions_mut().insert(origin);
    req.extensions_mut().insert(RequestLogSeq::new());
    let mut res = next.run(req).await;
    if let Ok(value) = HeaderValue::from_str(&id) {
        res.headers_mut().insert("x-request-id", value);
    }
    res
}
