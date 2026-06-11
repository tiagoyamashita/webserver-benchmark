use axum::extract::{Extension, Form, Query, Request, State};
use axum::http::{header, HeaderValue, StatusCode};
use axum::middleware::{self, Next};
use axum::response::{Html, IntoResponse, Json, Redirect, Response};
use axum::routing::{get, post};
use axum::Router;
use utoipa::OpenApi;
use utoipa_swagger_ui::SwaggerUi;
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
    pub stack_links: crate::stack_ping::StackLinks,
    pub pg_pool: Option<PgPool>,
}

impl AppState {
    pub fn new(project_root: PathBuf, tera: Tera, pg_pool: Option<PgPool>) -> Self {
        Self {
            project_root,
            tera: Arc::new(tera),
            stack_links: crate::stack_ping::StackLinks::from_env(),
            pg_pool,
        }
    }
}

fn route_endpoint_label(path: &str) -> &'static str {
    match path {
        "/" => "stack_landing",
        "/tests" => "tests_dashboard",
        "/health" => "health",
        "/welcome" => "welcome_redirect",
        "/tests/run" => "run_tests_post",
        "/tests/source" => "test_source",
        "/api/items" => "create_item",
        "/metrics" => "metrics",
        _ => "other",
    }
}

async fn record_http_request_metrics(req: Request, next: Next) -> Response {
    let start = std::time::Instant::now();
    let method = req.method().to_string();
    let path = req.uri().path().to_string();
    let endpoint = route_endpoint_label(req.uri().path());
    let request_id = req
        .extensions()
        .get::<crate::request_id::RequestId>()
        .map(|id| id.0.as_str())
        .unwrap_or("");
    let res = next.run(req).await;
    let status = res.status().as_u16();
    let ms = start.elapsed().as_millis();
    HTTP_REQUESTS
        .with_label_values(&[method.as_str(), endpoint])
        .inc();
    tracing::info!(
        method = %method,
        path = %path,
        status = status,
        ms = %ms,
        request_id = %request_id,
        "{method} {path} {status}"
    );
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

async fn list_items(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> impl IntoResponse {
    let Some(pool) = state.pg_pool.clone() else {
        tracing::warn!(
            source = "src/app.rs",
            controller = "list_items",
            method = "GET",
            path = "/api/items",
            request_id = %request_id.0,
            reason = "postgres-not-configured",
            "list_items database not configured"
        );
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({
                "error": "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)"
            })),
        )
            .into_response();
    };
    crate::items::list_items(pool, Some(&request_id.0))
        .await
        .into_response()
}

async fn create_item(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
    Query(query): Query<crate::items::CreateItemQuery>,
) -> impl IntoResponse {
    let Some(pool) = state.pg_pool.clone() else {
        tracing::warn!(
            source = "src/app.rs",
            controller = "create_item",
            method = "POST",
            path = "/api/items",
            name = %query.name,
            request_id = %request_id.0,
            reason = "postgres-not-configured",
            "create_item database not configured"
        );
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({
                "ok": false,
                "error": "Postgres not configured (set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD)"
            })),
        )
            .into_response();
    };
    crate::items::create_item(pool, query, Some(&request_id.0))
        .await
        .into_response()
}

async fn welcome_redirect(
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> Redirect {
    tracing::info!(
        source = "src/app.rs",
        controller = "welcome_redirect",
        method = "GET",
        path = "/welcome",
        request_id = %request_id.0,
        "welcome_redirect request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "welcome_redirect",
        request_id = %request_id.0,
        redirect = "/",
        "welcome_redirect succeeded"
    );
    Redirect::to("/")
}

async fn health(Extension(request_id): Extension<crate::request_id::RequestId>) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "health",
        method = "GET",
        path = "/health",
        request_id = %request_id.0,
        "health request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "health",
        request_id = %request_id.0,
        "health succeeded"
    );
    (
        [(
            header::CONTENT_TYPE,
            HeaderValue::from_static("text/plain; charset=utf-8"),
        )],
        "ok",
    )
}

async fn observability_sample_log(
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
        method = "GET",
        path = "/api/observability/sample-log",
        request_id = %request_id.0,
        "observability_sample_log request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
        request_id = %request_id.0,
        service = "exercises-rust",
        "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
        request_id = %request_id.0,
        "observability_sample_log succeeded"
    );
    (
        [(
            header::CONTENT_TYPE,
            HeaderValue::from_static("text/plain; charset=utf-8"),
        )],
        "logged",
    )
}

async fn stack_landing(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> Result<Html<String>, StatusCode> {
    tracing::info!(
        source = "src/app.rs",
        controller = "stack_landing",
        method = "GET",
        path = "/",
        request_id = %request_id.0,
        "stack_landing request received"
    );
    let page = StackLandingPage {
        stack_links: state.stack_links.browser_view(),
    };
    let ctx = Context::from_serialize(&page).map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    let html = state
        .tera
        .render("landing.html", &ctx)
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    tracing::info!(
        source = "src/app.rs",
        controller = "stack_landing",
        request_id = %request_id.0,
        template = "landing.html",
        "stack_landing succeeded"
    );
    Ok(Html(html))
}

async fn tests_dashboard(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
) -> Result<Html<String>, StatusCode> {
    tracing::info!(
        source = "src/app.rs",
        controller = "tests_dashboard",
        method = "GET",
        path = "/tests",
        request_id = %request_id.0,
        "tests_dashboard request received"
    );
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
    tracing::info!(
        source = "src/app.rs",
        controller = "tests_dashboard",
        request_id = %request_id.0,
        result_count = page.test_results.len(),
        has_report_file = has_report_file,
        "tests_dashboard succeeded"
    );
    Ok(Html(html))
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
        .merge(
            SwaggerUi::new("/swagger-ui")
                .url("/api-docs/openapi.json", crate::openapi::ApiDoc::openapi()),
        )
        .route("/", get(stack_landing))
        .route("/tests", get(tests_dashboard))
        .route("/health", get(health))
        .route("/api/observability/sample-log", get(observability_sample_log))
        .route("/welcome", get(welcome_redirect))
        .route("/stack-ping/:target", get(crate::stack_ping::stack_ping_handler))
        .route("/tests/run", post(run_tests_post))
        .route("/tests/source", get(test_source))
        .route("/api/items", get(list_items).post(create_item))
        .route("/metrics", get(metrics))
        .layer(middleware::from_fn(record_http_request_metrics))
        .layer(middleware::from_fn(crate::request_id::assign_request_id))
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

#[derive(Serialize)]
struct StackLandingPage {
    stack_links: crate::stack_ping::StackLinksView,
}

#[derive(Deserialize, Default)]
pub struct RunForm {
    pub nodeid: Option<String>,
}

async fn run_tests_post(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
    Form(form): Form<RunForm>,
) -> impl IntoResponse {
    let filter = form.nodeid.as_deref().filter(|s| !s.trim().is_empty());
    tracing::info!(
        source = "src/app.rs",
        controller = "run_tests_post",
        method = "POST",
        path = "/tests/run",
        request_id = %request_id.0,
        nodeid = ?filter,
        "run_tests_post request received"
    );
    let result = crate::runner::run_nextest(&state.project_root, filter);
    let mut flash = crate::flash::RunFlash::default();
    let (code, log) = match result {
        Ok(x) => x,
        Err(e) => {
            tracing::error!(
                source = "src/app.rs",
                controller = "run_tests_post",
                request_id = %request_id.0,
                nodeid = ?filter,
                error = %e,
                "run_tests_post failed"
            );
            flash.error = Some(format!(
                "Could not run cargo nextest ({e}). Install: cargo install --locked cargo-nextest"
            ));
            crate::flash::write_flash(&state.project_root, &flash);
            return Redirect::to("/tests");
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
        tracing::info!(
            source = "src/app.rs",
            controller = "run_tests_post",
            request_id = %request_id.0,
            nodeid = ?filter,
            exit_code = code,
            "run_tests_post succeeded"
        );
    } else {
        flash.error = Some(format!("cargo nextest finished (exit code {code})."));
        tracing::warn!(
            source = "src/app.rs",
            controller = "run_tests_post",
            request_id = %request_id.0,
            nodeid = ?filter,
            exit_code = code,
            "run_tests_post finished with errors"
        );
    }
    crate::flash::write_flash(&state.project_root, &flash);
    Redirect::to("/tests")
}

#[derive(Deserialize)]
pub struct SourceQ {
    #[serde(rename = "className")]
    pub class_name: String,
}

async fn test_source(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
    Query(q): Query<SourceQ>,
) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "test_source",
        method = "GET",
        path = "/tests/source",
        request_id = %request_id.0,
        className = %q.class_name,
        "test_source request received"
    );
    match crate::source::read_rust_source(&state.project_root, &q.class_name) {
        Some((path, content)) => {
            tracing::info!(
                source = "src/app.rs",
                controller = "test_source",
                request_id = %request_id.0,
                className = %q.class_name,
                path = %path,
                "test_source succeeded"
            );
            #[derive(Serialize)]
            struct Out {
                path: String,
                content: String,
            }
            Json(Out { path, content }).into_response()
        }
        None => {
            tracing::warn!(
                source = "src/app.rs",
                controller = "test_source",
                request_id = %request_id.0,
                className = %q.class_name,
                "test_source not found"
            );
            (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({"error": "not found"})),
            )
                .into_response()
        }
    }
}
