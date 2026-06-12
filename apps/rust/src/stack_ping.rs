//! Server-side GET probes for other stack services (same idea as Java `StackPingService`).

use axum::extract::{Extension, Path, State};
use axum::response::IntoResponse;
use axum::Json;
use serde::Serialize;
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

use crate::app::AppState;

#[derive(Clone)]
pub struct StackLinks {
    pub java_browser_url: String,
    pub python_browser_url: String,
    pub prometheus_browser_url: String,
    pub grafana_browser_url: String,
    pub elasticsearch_browser_url: String,
    pub kibana_browser_url: String,
    pub react_node_browser_url: String,
    java_base_url: String,
    python_base_url: String,
    prometheus_base_url: String,
    grafana_base_url: String,
    elasticsearch_base_url: String,
    kibana_base_url: String,
    react_node_base_url: String,
}

#[derive(Serialize)]
pub struct StackLinksView {
    pub java_browser_url: String,
    pub python_browser_url: String,
    pub prometheus_browser_url: String,
    pub grafana_browser_url: String,
    pub elasticsearch_browser_url: String,
    pub kibana_browser_url: String,
    pub react_node_browser_url: String,
}

impl StackLinks {
    pub fn from_env() -> Self {
        let java_base = read_env(
            "APP_STACK_JAVA_BASE_URL",
            &read_env("EXERCISES_JAVA_BASE_URL", "http://127.0.0.1:8080"),
        );
        Self {
            java_browser_url: read_env("APP_STACK_JAVA_BROWSER_URL", "http://127.0.0.1:8080/"),
            python_browser_url: read_env("APP_STACK_PYTHON_BROWSER_URL", "http://127.0.0.1:5000/"),
            prometheus_browser_url: read_env(
                "APP_STACK_PROMETHEUS_BROWSER_URL",
                "http://127.0.0.1:9090/",
            ),
            grafana_browser_url: read_env("APP_STACK_GRAFANA_BROWSER_URL", "http://127.0.0.1:3000/"),
            elasticsearch_browser_url: read_env(
                "APP_STACK_ELASTICSEARCH_BROWSER_URL",
                "http://127.0.0.1:9200/",
            ),
            kibana_browser_url: read_env("APP_STACK_KIBANA_BROWSER_URL", "http://127.0.0.1:5601/"),
            react_node_browser_url: read_env(
                "APP_STACK_REACT_NODE_BROWSER_URL",
                "http://127.0.0.1:5174/",
            ),
            java_base_url: java_base,
            python_base_url: read_env("APP_STACK_PYTHON_BASE_URL", "http://127.0.0.1:5000"),
            prometheus_base_url: read_env("APP_STACK_PROMETHEUS_BASE_URL", "http://127.0.0.1:9090"),
            grafana_base_url: read_env("APP_STACK_GRAFANA_BASE_URL", "http://127.0.0.1:3000"),
            elasticsearch_base_url: read_env(
                "APP_STACK_ELASTICSEARCH_BASE_URL",
                "http://127.0.0.1:9200",
            ),
            kibana_base_url: read_env("APP_STACK_KIBANA_BASE_URL", "http://127.0.0.1:5601"),
            react_node_base_url: read_env("APP_STACK_REACT_NODE_BASE_URL", "http://127.0.0.1:5174"),
        }
    }

    pub fn browser_view(&self) -> StackLinksView {
        StackLinksView {
            java_browser_url: self.java_browser_url.clone(),
            python_browser_url: self.python_browser_url.clone(),
            prometheus_browser_url: self.prometheus_browser_url.clone(),
            grafana_browser_url: self.grafana_browser_url.clone(),
            elasticsearch_browser_url: self.elasticsearch_browser_url.clone(),
            kibana_browser_url: self.kibana_browser_url.clone(),
            react_node_browser_url: self.react_node_browser_url.clone(),
        }
    }

    pub fn ping(&self, target: &str, request_id: Option<&str>) -> StackPingResult {
        match target {
            "postgres" => ping_postgres(),
            "redis" => ping_redis(),
            "java" => empty_get("java", &self.java_base_url, request_id),
            "python" => empty_get("python", &self.python_base_url, request_id),
            "prometheus" => empty_get("prometheus", &self.prometheus_base_url, request_id),
            "grafana" => empty_get("grafana", &self.grafana_base_url, request_id),
            "elasticsearch" => empty_get("elasticsearch", &self.elasticsearch_base_url, request_id),
            "kibana" => empty_get("kibana", &self.kibana_base_url, request_id),
            "react-node" => {
                let base = self.react_node_base_url.trim_end_matches('/');
                empty_get("react-node", &format!("{base}/api/health"), request_id)
            }
            _ => StackPingResult {
                stack: target.to_string(),
                url: String::new(),
                ok: false,
                status: None,
                error: Some("unknown stack target".into()),
            },
        }
    }
}

#[derive(Serialize)]
pub struct StackPingResult {
    pub stack: String,
    pub url: String,
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<u16>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

fn read_env(key: &str, default: &str) -> String {
    std::env::var(key)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| default.to_string())
}

fn normalize_root(base_url: &str) -> String {
    let t = base_url.trim();
    if t.is_empty() {
        return "http://127.0.0.1/".to_string();
    }
    // Keep explicit paths (e.g. /api/health) without forcing a trailing slash.
    if let Some(scheme_end) = t.find("://") {
        let after_scheme = &t[scheme_end + 3..];
        if let Some(slash) = after_scheme.find('/') {
            if slash < after_scheme.len() - 1 {
                return t.to_string();
            }
        }
    }
    if t.ends_with('/') {
        t.to_string()
    } else {
        format!("{t}/")
    }
}

fn ping_redis() -> StackPingResult {
    let url = crate::auth::redis_url_from_env().unwrap_or_else(|| "redis://127.0.0.1:6379".into());
    match redis::Client::open(url.as_str()) {
        Ok(client) => match client.get_connection() {
            Ok(mut conn) => match redis::cmd("PING").query::<String>(&mut conn) {
                Ok(pong) => StackPingResult {
                    stack: "redis".into(),
                    url: url.clone(),
                    ok: pong.eq_ignore_ascii_case("PONG"),
                    status: None,
                    error: if pong.eq_ignore_ascii_case("PONG") {
                        None
                    } else {
                        Some(format!("unexpected PING response: {pong}"))
                    },
                },
                Err(e) => StackPingResult {
                    stack: "redis".into(),
                    url,
                    ok: false,
                    status: None,
                    error: Some(format!(
                        "Cannot connect to Redis. {e}"
                    )),
                },
            },
            Err(e) => StackPingResult {
                stack: "redis".into(),
                url,
                ok: false,
                status: None,
                error: Some(format!(
                    "Cannot connect to Redis. {e}"
                )),
            },
        },
        Err(e) => StackPingResult {
            stack: "redis".into(),
            url,
            ok: false,
            status: None,
            error: Some(format!("Invalid Redis URL: {e}")),
        },
    }
}

fn ping_postgres() -> StackPingResult {
    let host = read_env("DB_HOST", "");
    if host.is_empty() {
        return StackPingResult {
            stack: "postgres".into(),
            url: String::new(),
            ok: false,
            status: None,
            error: Some("Postgres not configured (set DB_HOST)".into()),
        };
    }
    let port = read_env("DB_PORT", "5432");
    let url = format!("postgres://{host}:{port}");
    let addr = format!("{host}:{port}");
    match addr.to_socket_addrs() {
        Ok(mut addrs) => match addrs.next() {
            Some(socket_addr) => match TcpStream::connect_timeout(&socket_addr, Duration::from_secs(15))
            {
                Ok(_) => StackPingResult {
                    stack: "postgres".into(),
                    url,
                    ok: true,
                    status: None,
                    error: None,
                },
                Err(e) => StackPingResult {
                    stack: "postgres".into(),
                    url,
                    ok: false,
                    status: None,
                    error: Some(format!(
                        "Cannot connect to Postgres (is the container running on the Compose network?). {e}"
                    )),
                },
            },
            None => StackPingResult {
                stack: "postgres".into(),
                url,
                ok: false,
                status: None,
                error: Some("Could not resolve Postgres address".into()),
            },
        },
        Err(e) => StackPingResult {
            stack: "postgres".into(),
            url,
            ok: false,
            status: None,
            error: Some(format!("Invalid Postgres address: {e}")),
        },
    }
}

fn empty_get(stack: &str, base_url: &str, request_id: Option<&str>) -> StackPingResult {
    let outbound_id = crate::request_id::resolve_outbound_request_id(request_id);
    let url = normalize_root(base_url);
    match ureq::get(&url)
        .set(crate::request_id::REQUEST_ID_HEADER, &outbound_id)
        .set(crate::request_id::ORIGIN_HEADER, crate::request_id::OUTBOUND_ORIGIN)
        .timeout(Duration::from_secs(15))
        .call()
    {
        Ok(resp) => {
            let status = resp.status();
            StackPingResult {
                stack: stack.to_string(),
                url,
                ok: (200..300).contains(&status),
                status: Some(status),
                error: None,
            }
        }
        Err(ureq::Error::Status(status, _)) => StackPingResult {
            stack: stack.to_string(),
            url,
            ok: false,
            status: Some(status),
            error: None,
        },
        Err(e) => StackPingResult {
            stack: stack.to_string(),
            url,
            ok: false,
            status: None,
            error: Some(format!(
                "Cannot connect (is the container running on the Compose network?). {e}"
            )),
        },
    }
}

pub async fn stack_ping_handler(
    Path(target): Path<String>,
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> impl IntoResponse {
    let target = target.trim().to_string();
    tracing::info!(
        source = "src/stack_ping.rs",
        controller = "stack_ping_handler",
        method = "GET",
        path = "/stack-ping/{target}",
        target = %target,
        "stack_ping_handler request received"
    );
    let stack_name = target.clone();
    let stack = state.stack_links.clone();
    let rid = request_id.0.clone();
    let result = tokio::task::spawn_blocking(move || stack.ping(&target, Some(&rid)))
        .await
        .unwrap_or_else(|e| StackPingResult {
            stack: stack_name,
            url: String::new(),
            ok: false,
            status: None,
            error: Some(format!("join error: {e}")),
        });
    if result.ok {
        tracing::info!(
            source = "src/stack_ping.rs",
            controller = "stack_ping_handler",
            target = %result.stack,
            ok = result.ok,
            status = ?result.status,
            url = %result.url,
            "stack_ping_handler succeeded"
        );
    } else {
        tracing::warn!(
            source = "src/stack_ping.rs",
            controller = "stack_ping_handler",
            target = %result.stack,
            ok = result.ok,
            status = ?result.status,
            error = ?result.error,
            url = %result.url,
            "stack_ping_handler downstream unreachable"
        );
    }
    Json(result)
}
