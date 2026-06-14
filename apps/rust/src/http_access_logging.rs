//! Skip noisy http.request access lines for routine probe/scrape traffic (log failures only).

const QUIET_GET_PATHS: &[&str] = &["/health", "/metrics"];
const QUIET_POST_PATH: &str = "/api/auth/ensure";
const QUIET_POST_STATUS: u16 = 200;

pub fn request_pathname(path: &str) -> &str {
    path.split('?').next().unwrap_or(path)
}

fn is_quiet_post(method: &str, pathname: &str, status: Option<u16>) -> bool {
    method.eq_ignore_ascii_case("POST")
        && pathname == QUIET_POST_PATH
        && matches!(status, None | Some(QUIET_POST_STATUS))
}

/// When false, skip http.request access lines for this request.
/// GET /health and GET /metrics: omit on 200; log only when status is known and not 200.
/// POST /api/auth/ensure: omit on 200 (routine session reuse); log 201 and errors.
pub fn should_log_http_access(method: &str, path: &str, status: Option<u16>) -> bool {
    let pathname = request_pathname(path);

    if method.eq_ignore_ascii_case("GET") {
        if QUIET_GET_PATHS.contains(&pathname) {
            return !matches!(status, None | Some(200));
        }
        return true;
    }

    if is_quiet_post(method, pathname, status) {
        return false;
    }

    true
}

#[cfg(test)]
mod tests {
    use super::{request_pathname, should_log_http_access};

    #[test]
    fn skips_get_health_on_200_or_unknown_status() {
        assert!(!should_log_http_access("GET", "/health", None));
        assert!(!should_log_http_access("GET", "/health", Some(200)));
    }

    #[test]
    fn logs_get_health_on_non_200() {
        assert!(should_log_http_access("GET", "/health", Some(503)));
    }

    #[test]
    fn still_logs_other_routes() {
        assert!(should_log_http_access("GET", "/api/items", Some(200)));
        assert!(should_log_http_access("POST", "/health", Some(200)));
    }

    #[test]
    fn strips_query_string_from_path() {
        assert_eq!(request_pathname("/health?verbose=1"), "/health");
        assert!(!should_log_http_access("GET", "/health?verbose=1", Some(200)));
    }

    #[test]
    fn skips_post_auth_ensure_on_200_or_unknown_status() {
        assert!(!should_log_http_access("POST", "/api/auth/ensure", None));
        assert!(!should_log_http_access("POST", "/api/auth/ensure", Some(200)));
    }

    #[test]
    fn logs_post_auth_ensure_on_non_200() {
        assert!(should_log_http_access("POST", "/api/auth/ensure", Some(201)));
        assert!(should_log_http_access("POST", "/api/auth/ensure", Some(503)));
    }
}
