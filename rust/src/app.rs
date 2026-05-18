use axum::extract::{Form, Query, Request, State};
use axum::http::{header, HeaderValue, StatusCode};
use std::time::Duration;
use axum::middleware::{self, Next};
use axum::response::{Html, IntoResponse, Json, Redirect, Response};
use axum::routing::{get, post};
use axum::Router;
use prometheus::{Encoder, Opts, TextEncoder};
use serde::Deserialize;
use serde::Serialize;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::path::{Path, PathBuf};
use sqlx::PgPool;
use std::sync::Arc;
use std::sync::LazyLock;
use tera::{Context, Tera};

/// Same series name as the Python Flask app; Prometheus **`job`** label distinguishes scrapes.
static HTTP_REQUESTS: LazyLock<prometheus::CounterVec> = LazyLock::new(|| {
    let cv = prometheus::CounterVec::new(
        Opts::new(
            "exercises_http_requests_total",
            "HTTP requests handled by the exercises Axum app",
        ),
        &["method", "endpoint"],
    )
    .expect("exercises_http_requests_total opts");
    prometheus::default_registry()
        .register(Box::new(cv.clone()))
        .expect("register exercises_http_requests_total");
    cv
});

#[derive(Clone)]
pub struct AppState {
    pub project_root: PathBuf,
    pub tera: Arc<Tera>,
    /// Base URL for the Spring Boot app (no trailing slash). Compose: `http://java:8080`.
    pub java_base_url: String,
    pub pg_pool: Option<PgPool>,
}

impl AppState {
    pub fn new(project_root: PathBuf, tera: Tera, pg_pool: Option<PgPool>) -> Self {
        let java_base_url = std::env::var("EXERCISES_JAVA_BASE_URL")
            .unwrap_or_else(|_| "http://127.0.0.1:8080".to_string());
        Self {
            project_root,
            tera: Arc::new(tera),
            java_base_url,
            pg_pool,
        }
    }
}

fn route_endpoint_label(path: &str) -> &'static str {
    match path {
        "/" => "home",
        "/health" => "health",
        "/welcome" => "welcome_landing",
        "/welcome/ping-java" => "welcome_ping_java",
        "/tests/run" => "run_tests_post",
        "/tests/source" => "test_source",
        "/api/items" => "create_item",
        "/metrics" => "metrics",
        _ => "other",
    }
}

async fn record_http_request_metrics(req: Request, next: Next) -> Response {
    let method = req.method().to_string();
    let endpoint = route_endpoint_label(req.uri().path());
    let res = next.run(req).await;
    HTTP_REQUESTS
        .with_label_values(&[method.as_str(), endpoint])
        .inc();
    res
}

async fn metrics() -> impl IntoResponse {
    let encoder = TextEncoder::new();
    let mut buf = Vec::new();
    if encoder
        .encode(&prometheus::gather(), &mut buf)
        .is_err()
    {
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    }
    (
        [(
            header::CONTENT_TYPE,
            HeaderValue::from_static("text/plain; version=0.0.4; charset=utf-8"),
        )],
        buf,
    )
        .into_response()
}

fn resolve_project_root() -> PathBuf {
    if let Ok(root) = std::env::var("EXERCISES_RUST_ROOT") {
        let p = PathBuf::from(root.trim());
        if p.is_dir() {
            return p;
        }
    }
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
}

/// Load HTML templates from `{project_root}/templates/*.html` (no glob — works in Docker and on Windows).
pub fn load_tera(project_root: &Path) -> std::io::Result<Tera> {
    let templates_dir = project_root.join("templates");
    if !templates_dir.is_dir() {
        return Err(std::io::Error::new(
            std::io::ErrorKind::NotFound,
            format!("templates directory missing: {}", templates_dir.display()),
        ));
    }
    let mut tera = Tera::default();
    for entry in std::fs::read_dir(&templates_dir)? {
        let entry = entry?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let is_html = path
            .extension()
            .and_then(|s| s.to_str())
            .map(|ext| ext.eq_ignore_ascii_case("html"))
            == Some(true);
        if !is_html {
            continue;
        }
        let name = path
            .file_name()
            .and_then(|n| n.to_str())
            .ok_or_else(|| {
                std::io::Error::new(
                    std::io::ErrorKind::InvalidInput,
                    "template file name is not valid UTF-8",
                )
            })?;
        let src = std::fs::read_to_string(&path)?;
        tera.add_raw_template(name, &src).map_err(|e| {
            std::io::Error::new(
                std::io::ErrorKind::InvalidData,
                format!("tera parse {name}: {e}"),
            )
        })?;
    }
    tera.autoescape_on(vec!["html"]);
    tera.get_template("home.html").map_err(|_| {
        std::io::Error::new(
            std::io::ErrorKind::NotFound,
            "home.html not found under templates/",
        )
    })?;
    tera.get_template("landing.html").map_err(|_| {
        std::io::Error::new(
            std::io::ErrorKind::NotFound,
            "landing.html not found under templates/",
        )
    })?;
    Ok(tera)
}

async fn create_item(
    State(state): State<AppState>,
    Query(query): Query<crate::items::CreateItemQuery>,
) -> impl IntoResponse {
    let Some(pool) = state.pg_pool.clone() else {
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({
                "ok": false,
                "error": "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)"
            })),
        )
            .into_response();
    };
    crate::items::create_item(pool, query).await.into_response()
}

async fn health() -> impl IntoResponse {
    (
        [(
            header::CONTENT_TYPE,
            HeaderValue::from_static("text/plain; charset=utf-8"),
        )],
        "ok",
    )
}

pub async fn serve() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let project_root = resolve_project_root();
    eprintln!("exercises-web: project root {}", project_root.display());
    eprintln!(
        "exercises-web: loading templates from {}/templates/*.html",
        project_root.display()
    );
    let mut tera = load_tera(&project_root)?;
    tera.autoescape_on(vec!["html"]);
    let pg_pool = match crate::db::connect_pool().await {
        Ok(pool) => {
            eprintln!("exercises-web: connected to Postgres (items table)");
            Some(pool)
        }
        Err(e) => {
            eprintln!("exercises-web: Postgres unavailable ({e}); POST /api/items will fail");
            None
        }
    };
    let state = AppState::new(project_root, tera, pg_pool);
    let app = build_router(state);
    let port: u16 = std::env::var("EXERCISES_WEB_PORT")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(8082);
    // Default 0.0.0.0 so http://localhost:PORT works reliably (Docker sets this too). Binding only
    // 127.0.0.1 can fail when the browser resolves "localhost" to ::1 first (Windows / some IPv6 setups).
    let bind_host: IpAddr = std::env::var("EXERCISES_WEB_HOST")
        .ok()
        .and_then(|s| s.trim().parse().ok())
        .unwrap_or(IpAddr::V4(Ipv4Addr::UNSPECIFIED));
    let addr = SocketAddr::new(bind_host, port);
    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .map_err(|e| {
            format!(
                "Failed to bind {addr}: {e}. Another process may be using port {port}. Try: set EXERCISES_WEB_PORT=8090 (PowerShell: $env:EXERCISES_WEB_PORT=8090)"
            )
        })?;
    eprintln!("exercises-web: listening at http://{addr}/  (Ctrl+C to stop)");
    tracing::info!("exercises-web listening on http://{}", addr);
    axum::serve(listener, app).await?;
    Ok(())
}

/// HTTP router (used by `serve` and integration tests).
pub fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(home))
        .route("/health", get(health))
        .route("/welcome", get(landing))
        .route("/welcome/ping-java", get(ping_java))
        .route("/tests/run", post(run_tests_post))
        .route("/tests/source", get(test_source))
        .route("/api/items", post(create_item))
        .route("/metrics", get(metrics))
        .layer(middleware::from_fn(record_http_request_metrics))
        .layer(tower_http::trace::TraceLayer::new_for_http())
        .with_state(state)
}

#[derive(Serialize)]
struct HomePage {
    test_results: Vec<crate::junit::TestRow>,
    report_sources: Vec<String>,
    has_report_file: bool,
    report_xml_resolved: String,
    project_root: String,
    test_run_message: Option<String>,
    test_run_error: Option<String>,
    test_run_log_tail: Option<String>,
}

async fn landing(State(state): State<AppState>) -> Result<Html<String>, StatusCode> {
    let ctx = Context::new();
    let html = state
        .tera
        .render("landing.html", &ctx)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Html(html))
}

#[derive(Serialize)]
struct PingJavaResponse {
    ok: bool,
    java_status: Option<u16>,
    java_body: Option<String>,
    java_url: String,
    error: Option<String>,
}

async fn ping_java(State(state): State<AppState>) -> impl IntoResponse {
    let base = state.java_base_url.trim_end_matches('/').to_string();
    let url = format!("{base}/api/hello-from-rust");
    tracing::info!(
        java_url = %url,
        "Rust /welcome: proxying GET to Java /api/hello-from-rust"
    );
    let url_for_task = url.clone();
    let outcome = tokio::task::spawn_blocking(move || ping_java_blocking(url_for_task)).await;

    match outcome {
        Ok(resp) => Json(resp).into_response(),
        Err(e) => Json(PingJavaResponse {
            ok: false,
            java_status: None,
            java_body: None,
            java_url: url,
            error: Some(format!("join error: {e}")),
        })
        .into_response(),
    }
}

fn ping_java_blocking(url: String) -> PingJavaResponse {
    match ureq::get(&url).timeout(Duration::from_secs(15)).call() {
        Ok(resp) => {
            let status = resp.status();
            let ok = (200..300).contains(&status);
            let text = resp.into_string().unwrap_or_default();
            PingJavaResponse {
                ok,
                java_status: Some(status),
                java_body: Some(text),
                java_url: url,
                error: None,
            }
        }
        Err(ureq::Error::Status(status, resp)) => {
            let text = resp.into_string().unwrap_or_default();
            PingJavaResponse {
                ok: false,
                java_status: Some(status),
                java_body: Some(text),
                java_url: url,
                error: None,
            }
        }
        Err(e) => PingJavaResponse {
            ok: false,
            java_status: None,
            java_body: None,
            java_url: url,
            error: Some(e.to_string()),
        },
    }
}

async fn home(State(state): State<AppState>) -> Result<Html<String>, StatusCode> {
    let flash = crate::flash::take_flash(&state.project_root);
    let rows = crate::junit::load_latest_results(&state.project_root);
    let resolved = crate::junit::resolve_existing_report_path_for_ui(&state.project_root);
    let has_report_file = resolved.is_some();
    let report_sources = if let Some(ref p) = resolved {
        vec![p.to_string_lossy().into_owned()]
    } else {
        crate::junit::existing_report_hints(&state.project_root)
    };
    let report_xml_resolved = resolved
        .as_ref()
        .map(|p| p.to_string_lossy().into_owned())
        .unwrap_or_else(|| crate::junit::report_xml_missing_hint(&state.project_root));
    let page = HomePage {
        test_results: rows,
        report_sources,
        has_report_file,
        report_xml_resolved,
        project_root: state.project_root.to_string_lossy().into_owned(),
        test_run_message: flash.message,
        test_run_error: flash.error,
        test_run_log_tail: flash.log_tail,
    };
    let ctx = Context::from_serialize(&page).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    let html = state
        .tera
        .render("home.html", &ctx)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    Ok(Html(html))
}

#[derive(Deserialize, Default)]
pub struct RunForm {
    pub nodeid: Option<String>,
}

async fn run_tests_post(State(state): State<AppState>, Form(form): Form<RunForm>) -> impl IntoResponse {
    let filter = form.nodeid.as_deref().filter(|s| !s.trim().is_empty());
    let result = crate::runner::run_nextest(&state.project_root, filter);
    let mut flash = crate::flash::RunFlash::default();
    let (code, log) = match result {
        Ok(x) => x,
        Err(e) => {
            flash.error = Some(format!(
                "Could not run cargo nextest ({e}). Install: cargo install --locked cargo-nextest"
            ));
            crate::flash::write_flash(&state.project_root, &flash);
            return Redirect::to("/");
        }
    };
    let tail = if log.len() > 12_000 {
        log[log.len() - 12_000..].to_string()
    } else {
        log.clone()
    };
    flash.log_tail = Some(tail);
    if log.contains("no such subcommand: `nextest`")
        || log.contains("requires the `nextest` subcommand")
    {
        flash.error =
            Some("cargo-nextest is required. Install: cargo install --locked cargo-nextest".into());
    } else if code == 0 {
        flash.message = Some("cargo nextest finished (exit code 0).".into());
    } else {
        flash.error = Some(format!("cargo nextest finished (exit code {code})."));
    }
    crate::flash::write_flash(&state.project_root, &flash);
    Redirect::to("/")
}

#[derive(Deserialize)]
pub struct SourceQ {
    #[serde(rename = "className")]
    pub class_name: String,
}

async fn test_source(State(state): State<AppState>, Query(q): Query<SourceQ>) -> impl IntoResponse {
    match crate::source::read_rust_source(&state.project_root, &q.class_name) {
        Some((path, content)) => {
            #[derive(Serialize)]
            struct Out {
                path: String,
                content: String,
            }
            Json(Out { path, content }).into_response()
        }
        None => (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"error": "not found"})),
        )
            .into_response(),
    }
}
