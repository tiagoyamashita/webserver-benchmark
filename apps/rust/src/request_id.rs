//! HTTP request id (`X-Request-ID`) for log and Postgres correlation.

use axum::body::{to_bytes, Body, Bytes};
use axum::extract::Request;
use axum::http::{HeaderMap, HeaderValue};
use axum::middleware::Next;
use axum::response::Response;
use serde_json::{Map, Value};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

pub const ORIGIN_HEADER: &str = "x-request-origin";
pub const REQUEST_ID_HEADER: &str = "x-request-id";
pub const OUTBOUND_ORIGIN: &str = "exercises-rust";

const MAX_BODY_LOG_BYTES: usize = 65_536;

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

#[derive(Clone, Debug)]
pub struct RequestHttpSnapshot {
    pub headers: Map<String, Value>,
    pub url_params: Map<String, Value>,
    pub body: Map<String, Value>,
}

pub fn postgres_application_name(service: &str, request_id: &str) -> String {
    let value = format!("{service};req={request_id}");
    if value.len() <= 63 {
        value
    } else {
        value[..63].to_string()
    }
}

pub fn resolve_outbound_request_id(current: Option<&str>) -> String {
    if let Some(text) = current {
        let trimmed = text.trim();
        if is_acceptable_request_id(trimmed) {
            return trimmed.to_string();
        }
    }
    generate_request_id()
}

pub fn resolve_request_id(header: Option<&HeaderValue>) -> (RequestId, RequestIdSource) {
    if let Some(value) = header {
        if let Ok(text) = value.to_str() {
            let trimmed = text.trim();
            if is_acceptable_request_id(trimmed) {
                return (RequestId(trimmed.to_string()), RequestIdSource::ReceivedHeader);
            }
            tracing::debug!(
                target: "http.request",
                rejected_request_id = %trimmed,
                "ignored inbound X-Request-ID (invalid format)"
            );
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

/// Match Java/Python `^[a-zA-Z0-9._-]{8,64}$` plus standard UUIDs from browser `crypto.randomUUID()`.
fn is_acceptable_request_id(value: &str) -> bool {
    is_uuid(value) || is_safe_token(value)
}

fn is_uuid(value: &str) -> bool {
    value.len() == 36
        && value.as_bytes().get(8) == Some(&b'-')
        && value.as_bytes().get(13) == Some(&b'-')
        && value.as_bytes().get(18) == Some(&b'-')
        && value.as_bytes().get(23) == Some(&b'-')
        && value
            .chars()
            .all(|c| c.is_ascii_hexdigit() || c == '-')
}

fn collect_headers(req: &Request) -> Map<String, Value> {
    const ALLOW: &[&str] = &[
        "x-request-id",
        "x-request-origin",
        "x-dashboard-page",
        "x-session-id",
        "cookie",
        "authorization",
        "content-type",
        "accept",
        "user-agent",
        "host",
    ];
    let mut out = Map::new();
    for (name, value) in req.headers().iter() {
        if ALLOW.contains(&name.as_str()) {
            if let Ok(text) = value.to_str() {
                out.insert(name.as_str().to_string(), Value::String(text.to_string()));
            }
        }
    }
    out
}

fn collect_url_params(query: Option<&str>) -> Map<String, Value> {
    let Some(query) = query else {
        return Map::new();
    };
    let mut out = Map::new();
    for pair in query.split('&') {
        if pair.is_empty() {
            continue;
        }
        let (key, value) = pair
            .split_once('=')
            .map(|(k, v)| (k, v))
            .unwrap_or((pair, ""));
        if !key.is_empty() {
            out.insert(key.to_string(), Value::String(value.to_string()));
        }
    }
    out
}

fn collect_body(bytes: &Bytes) -> Map<String, Value> {
    if bytes.is_empty() {
        return Map::new();
    }
    if let Ok(value) = serde_json::from_slice::<Value>(bytes) {
        return match value {
            Value::Object(map) => map,
            other => {
                let mut out = Map::new();
                out.insert("_json".into(), other);
                out
            }
        };
    }
    let mut out = Map::new();
    out.insert(
        "_raw".into(),
        Value::String(String::from_utf8_lossy(bytes).to_string()),
    );
    out
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

fn incoming_request_id_header(headers: &HeaderMap) -> Option<&HeaderValue> {
    headers
        .get(REQUEST_ID_HEADER)
        .or_else(|| headers.get(axum::http::header::HeaderName::from_static("X-Request-ID")))
}

fn http_access_session_id(headers: &HeaderMap) -> Option<String> {
    let cookie_name =
        std::env::var("EXERCISES_SESSION_COOKIE").unwrap_or_else(|_| "exercises_session".to_string());
    crate::auth::http_access_session_id(headers, &cookie_name)
}

pub async fn assign_request_id(req: Request, next: Next) -> Response {
    let (parts, body) = req.into_parts();
    let body_bytes = to_bytes(body, MAX_BODY_LOG_BYTES)
        .await
        .unwrap_or_default();
    let mut req = Request::from_parts(parts, Body::from(body_bytes.clone()));

    let (request_id, source) =
        resolve_request_id(incoming_request_id_header(req.headers()));
    let origin = resolve_request_origin(req.headers().get(ORIGIN_HEADER));
    let id = request_id.0.clone();
    let headers = collect_headers(&req);
    let url_params = collect_url_params(req.uri().query());
    let body_map = collect_body(&body_bytes);
    let snapshot = RequestHttpSnapshot {
        headers: headers.clone(),
        url_params: url_params.clone(),
        body: body_map.clone(),
    };
    req.extensions_mut().insert(request_id);
    req.extensions_mut().insert(source);
    req.extensions_mut().insert(origin);
    req.extensions_mut().insert(RequestLogSeq::new());
    req.extensions_mut().insert(snapshot);
    let log_seq = req
        .extensions()
        .get::<RequestLogSeq>()
        .map(RequestLogSeq::next)
        .unwrap_or(0);
    let method = req.method().to_string();
    let path = req.uri().path().to_string();
    let session_id = http_access_session_id(req.headers());
    crate::obs_log::log_http_request_received(
        &method,
        &path,
        &id,
        session_id.as_deref(),
        log_seq,
        &headers,
        &url_params,
        &body_map,
    );
    let mut res = next.run(req).await;
    if let Ok(value) = HeaderValue::from_str(&id) {
        res.headers_mut().insert("x-request-id", value);
    }
    res
}
