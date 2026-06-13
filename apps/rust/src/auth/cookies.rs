use axum::http::{header, HeaderMap, HeaderValue};

use super::session::SessionConfig;

pub fn session_cookie_value(config: &SessionConfig, session_id: &str) -> String {
    format!(
        "{}={}; HttpOnly; Path=/; Max-Age={}; SameSite=Lax",
        config.cookie_name, session_id, config.ttl_secs
    )
}

pub fn clear_session_cookie_value(config: &SessionConfig) -> String {
    format!(
        "{}=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax",
        config.cookie_name
    )
}

pub fn append_session_cookie(headers: &mut HeaderMap, config: &SessionConfig, session_id: &str) {
    if let Ok(value) = HeaderValue::from_str(&session_cookie_value(config, session_id)) {
        headers.append(header::SET_COOKIE, value);
    }
}

pub fn append_clear_session_cookie(headers: &mut HeaderMap, config: &SessionConfig) {
    if let Ok(value) = HeaderValue::from_str(&clear_session_cookie_value(config)) {
        headers.append(header::SET_COOKIE, value);
    }
}

pub fn http_access_session_id(headers: &HeaderMap, cookie_name: &str) -> Option<String> {
    session_id_candidates(headers, cookie_name).into_iter().next()
}

pub fn session_id_candidates(headers: &HeaderMap, cookie_name: &str) -> Vec<String> {
    let mut candidates = Vec::new();
    if let Some(value) = headers.get(header::AUTHORIZATION).and_then(|v| v.to_str().ok()) {
        if let Some(token) = parse_bearer(value) {
            candidates.push(token);
        }
    }
    if let Some(value) = headers.get("x-session-id").and_then(|v| v.to_str().ok()) {
        let trimmed = value.trim();
        if !trimmed.is_empty() {
            candidates.push(trimmed.to_string());
        }
    }
    if let Some(cookie) = read_cookie(headers, cookie_name) {
        candidates.push(cookie);
    }
    candidates
}

fn parse_bearer(value: &str) -> Option<String> {
    let trimmed = value.trim();
    let rest = trimmed.strip_prefix("Bearer ")?;
    let token = rest.trim();
    if token.is_empty() {
        None
    } else {
        Some(token.to_string())
    }
}

fn read_cookie(headers: &HeaderMap, name: &str) -> Option<String> {
    let raw = headers.get(header::COOKIE)?.to_str().ok()?;
    for part in raw.split(';') {
        let part = part.trim();
        let Some((key, value)) = part.split_once('=') else {
            continue;
        };
        if key == name {
            let value = value.trim();
            if !value.is_empty() {
                return Some(value.to_string());
            }
        }
    }
    None
}
