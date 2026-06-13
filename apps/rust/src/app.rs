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
    pub kafka_config: Option<crate::kafka::KafkaAdminConfig>,
    pub auth: Option<crate::auth::AuthState>,
}

impl AppState {
    pub fn new(
        project_root: PathBuf,
        tera: Tera,
        pg_pool: Option<PgPool>,
        kafka_config: Option<crate::kafka::KafkaAdminConfig>,
        auth: Option<crate::auth::AuthState>,
    ) -> Self {
        Self {
            project_root,
            tera: Arc::new(tera),
            stack_links: crate::stack_ping::StackLinks::from_env(),
            pg_pool,
            kafka_config,
            auth,
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
        "/api/users/publish-create-user" => "publish_create_user_kafka",
        "/api/auth/ensure" => "auth_ensure",
        "/api/auth/login" => "auth_login",
        "/api/auth/logout" => "auth_logout",
        "/api/auth/session" => "auth_session",
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
        .map(|id| id.0.clone())
        .unwrap_or_default();
    let request_origin = req
        .extensions()
        .get::<crate::request_id::RequestOrigin>()
        .and_then(|origin| origin.0.clone())
        .unwrap_or_default();
    let request_id_source = req
        .extensions()
        .get::<crate::request_id::RequestIdSource>()
        .copied()
        .unwrap_or(crate::request_id::RequestIdSource::Generated);
    let log_seq_counter = req.extensions().get::<crate::request_id::RequestLogSeq>().cloned();
    let cookie_name =
        std::env::var("EXERCISES_SESSION_COOKIE").unwrap_or_else(|_| "exercises_session".to_string());
    let session_id = req
        .extensions()
        .get::<crate::auth::CurrentSession>()
        .map(|session| session.0.session_id.clone())
        .or_else(|| crate::auth::http_access_session_id(req.headers(), &cookie_name));
    let res = next.run(req).await;
    let status = res.status().as_u16();
    let ms = start.elapsed().as_millis();
    HTTP_REQUESTS
        .with_label_values(&[method.as_str(), endpoint])
        .inc();
    let log_seq = log_seq_counter.as_ref().map(crate::request_id::RequestLogSeq::next).unwrap_or(0);
    let id_source = match request_id_source {
        crate::request_id::RequestIdSource::ReceivedHeader => "header",
        crate::request_id::RequestIdSource::Generated => "generated",
    };
    if let Some(ref session_id) = session_id {
        tracing::info!(
            method = %method,
            path = %path,
            status = status,
            ms = %ms,
            request_id = %request_id,
            session_id = %session_id,
            request_id_source = id_source,
            request_origin = %request_origin,
            log_seq = log_seq,
            phase = "completed",
            "{method} {path} {status} request_id={request_id}"
        );
    } else {
        tracing::info!(
            method = %method,
            path = %path,
            status = status,
            ms = %ms,
            request_id = %request_id,
            request_id_source = id_source,
            request_origin = %request_origin,
            log_seq = log_seq,
            phase = "completed",
            "{method} {path} {status} request_id={request_id}"
        );
    }
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
    Extension(request_id_source): Extension<crate::request_id::RequestIdSource>,
    Extension(request_origin): Extension<crate::request_id::RequestOrigin>,
    Extension(log_seq): Extension<crate::request_id::RequestLogSeq>,
    Json(body): Json<crate::items::CreateItemRequest>,
) -> impl IntoResponse {
    let Some(pool) = state.pg_pool.clone() else {
        tracing::warn!(
            source = "src/app.rs",
            controller = "create_item",
            method = "POST",
            path = "/api/items",
            name = %body.name,
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
    crate::items::create_item(
        pool,
        body,
        Some(&request_id.0),
        request_origin.0.as_deref(),
        request_id_source,
        Some(&log_seq),
    )
    .await
    .into_response()
}

async fn publish_create_user_kafka(
    State(state): State<AppState>,
    Extension(request_id): Extension<crate::request_id::RequestId>,
    Query(query): Query<crate::kafka::PublishCreateUserQuery>,
) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "publish_create_user_kafka",
        method = "POST",
        path = "/api/users/publish-create-user",
        name = %query.name,
        email = %query.email,
        "publish_create_user_kafka request received"
    );
    let Some(config) = state.kafka_config.as_ref() else {
        tracing::warn!(
            source = "src/app.rs",
            controller = "publish_create_user_kafka",
            reason = "kafka-not-configured",
            "publish_create_user_kafka unavailable"
        );
        return (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(serde_json::json!({
                "ok": false,
                "requestId": request_id.0,
                "error": "Kafka not configured (set KAFKA_BOOTSTRAP_SERVERS and ensure broker is reachable)"
            })),
        )
            .into_response();
    };
    crate::kafka::publish_create_user_event(config, query, &request_id.0).await
}

async fn welcome_redirect(
    Extension(_request_id): Extension<crate::request_id::RequestId>,
) -> Redirect {
    tracing::info!(
        source = "src/app.rs",
        controller = "welcome_redirect",
        method = "GET",
        path = "/welcome",
        "welcome_redirect request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "welcome_redirect",
        redirect = "/",
        "welcome_redirect succeeded"
    );
    Redirect::to("/")
}

async fn health(Extension(_request_id): Extension<crate::request_id::RequestId>) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "health",
        method = "GET",
        path = "/health",
        "health request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "health",
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
    Extension(_request_id): Extension<crate::request_id::RequestId>,
) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
        method = "GET",
        path = "/api/observability/sample-log",
        "observability_sample_log request received"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
        service = "exercises-rust",
        "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)"
    );
    tracing::info!(
        source = "src/app.rs",
        controller = "observability_sample_log",
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
    Extension(_request_id): Extension<crate::request_id::RequestId>,
) -> Result<Html<String>, StatusCode> {
    tracing::info!(
        source = "src/app.rs",
        controller = "stack_landing",
        method = "GET",
        path = "/",
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
        template = "landing.html",
        "stack_landing succeeded"
    );
    Ok(Html(html))
}

async fn tests_dashboard(
    State(state): State<AppState>,
    Extension(_request_id): Extension<crate::request_id::RequestId>,
) -> Result<Html<String>, StatusCode> {
    tracing::info!(
        source = "src/app.rs",
        controller = "tests_dashboard",
        method = "GET",
        path = "/tests",
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

    let kafka_config = crate::kafka::KafkaAdminConfig::from_env();
    let kafka_ready = match crate::kafka::ensure_kafka_admin(&kafka_config).await {
        Ok(()) => {
            eprintln!(
                "exercises-web: Kafka topic `{}` ready (bootstrap={})",
                kafka_config.create_user_topic, kafka_config.bootstrap_servers
            );
            true
        }
        Err(e) => {
            if kafka_config.fail_fast {
                return Err(e.into());
            }
            eprintln!("exercises-web: Kafka admin skipped ({e}); create-user consumer disabled");
            false
        }
    };

    let kafka_publish_config = if kafka_ready {
        Some(kafka_config.clone())
    } else {
        None
    };

    let pg_pool = match crate::db::connect_pool().await {
        Ok(pool) => {
            eprintln!("exercises-web: connected to Postgres (items + users tables)");
            if kafka_ready {
                crate::kafka::spawn_create_user_consumer(pool.clone(), kafka_config);
            }
            Some(pool)
        }
        Err(e) => {
            eprintln!("exercises-web: Postgres unavailable ({e}); POST /api/items will fail");
            None
        }
    };
    let auth = match crate::auth::connect_redis().await {
        Ok(redis) => {
            let auth_state = crate::auth::AuthState {
                redis,
                config: crate::auth::SessionConfig::from_env(),
            };
            crate::auth::verify_redis_startup(&auth_state).await;
            eprintln!("exercises-web: connected to Redis (shared sessions)");
            Some(auth_state)
        }
        Err(e) => {
            eprintln!("exercises-web: Redis unavailable ({e}); session auth disabled");
            None
        }
    };

    let state = AppState::new(project_root, tera, pg_pool, kafka_publish_config, auth);
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
    let session_state = state.clone();
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
        .route(
            "/api/users/publish-create-user",
            post(publish_create_user_kafka),
        )
        .route("/api/auth/ensure", post(crate::auth::ensure_session))
        .route("/api/auth/login", post(crate::auth::login))
        .route("/api/auth/logout", post(crate::auth::logout))
        .route("/api/auth/session", get(crate::auth::current_session))
        .route("/metrics", get(metrics))
        .layer(middleware::from_fn(record_http_request_metrics))
        .layer(middleware::from_fn_with_state(
            session_state.clone(),
            crate::auth::bootstrap_page_session,
        ))
        .layer(middleware::from_fn_with_state(
            session_state,
            crate::auth::resolve_session,
        ))
        .layer(middleware::from_fn(crate::auth::session_log_span))
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
    Extension(_request_id): Extension<crate::request_id::RequestId>,
    Form(form): Form<RunForm>,
) -> impl IntoResponse {
    let filter = form.nodeid.as_deref().filter(|s| !s.trim().is_empty());
    tracing::info!(
        source = "src/app.rs",
        controller = "run_tests_post",
        method = "POST",
        path = "/tests/run",
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
            nodeid = ?filter,
            exit_code = code,
            "run_tests_post succeeded"
        );
    } else {
        flash.error = Some(format!("cargo nextest finished (exit code {code})."));
        tracing::warn!(
            source = "src/app.rs",
            controller = "run_tests_post",
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
    Extension(_request_id): Extension<crate::request_id::RequestId>,
    Query(q): Query<SourceQ>,
) -> impl IntoResponse {
    tracing::info!(
        source = "src/app.rs",
        controller = "test_source",
        method = "GET",
        path = "/tests/source",
        className = %q.class_name,
        "test_source request received"
    );
    match crate::source::read_rust_source(&state.project_root, &q.class_name) {
        Some((path, content)) => {
            tracing::info!(
                source = "src/app.rs",
                controller = "test_source",
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
