use axum::extract::{Form, Query, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Json, Redirect};
use axum::routing::{get, post};
use axum::Router;
use serde::Deserialize;
use serde::Serialize;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tera::{Context, Tera};

#[derive(Clone)]
pub struct AppState {
    pub project_root: PathBuf,
    pub tera: Arc<Tera>,
}

fn resolve_project_root() -> PathBuf {
    std::env::var("EXERCISES_RUST_ROOT")
        .ok()
        .map(PathBuf::from)
        .filter(|p| p.join("Cargo.toml").is_file())
        .unwrap_or_else(|| PathBuf::from(env!("CARGO_MANIFEST_DIR")))
}

pub async fn serve() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let project_root = resolve_project_root();
    let tera_glob = concat!(env!("CARGO_MANIFEST_DIR"), "/templates/**/*");
    eprintln!("exercises-web: project root {}", project_root.display());
    eprintln!("exercises-web: loading templates from {}", tera_glob);
    let mut tera = Tera::new(tera_glob).map_err(|e| {
        format!("Tera template load failed (is rust/templates/ present?): {e}")
    })?;
    tera.autoescape_on(vec!["html"]);
    let state = AppState {
        project_root,
        tera: Arc::new(tera),
    };
    let app = Router::new()
        .route("/", get(home))
        .route("/tests/run", post(run_tests_post))
        .route("/tests/source", get(test_source))
        .layer(tower_http::trace::TraceLayer::new_for_http())
        .with_state(state);
    let port: u16 = std::env::var("EXERCISES_WEB_PORT")
        .ok()
        .and_then(|s| s.parse().ok())
        .unwrap_or(8082);
    let addr = SocketAddr::from(([127, 0, 0, 1], port));
    let listener = tokio::net::TcpListener::bind(addr)
        .await
        .map_err(|e| {
            format!(
                "Failed to bind {addr}: {e}. Another process may be using port {port}. Try: set EXERCISES_WEB_PORT=8090 (PowerShell: $env:EXERCISES_WEB_PORT=8090)"
            )
        })?;
    eprintln!("exercises-web: listening at http://127.0.0.1:{port}/  (Ctrl+C to stop)");
    tracing::info!("exercises-web listening on http://{}", addr);
    axum::serve(listener, app).await?;
    Ok(())
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

async fn home(State(state): State<AppState>) -> Result<Html<String>, StatusCode> {
    let flash = crate::flash::take_flash(&state.project_root);
    let rows = crate::junit::load_latest_results(&state.project_root);
    let jpath = crate::junit::report_xml_path(&state.project_root);
    let has_report_file = jpath.is_file();
    let report_sources = if has_report_file {
        vec![jpath.to_string_lossy().into_owned()]
    } else {
        crate::junit::existing_report_hints(&state.project_root)
    };
    let page = HomePage {
        test_results: rows,
        report_sources,
        has_report_file,
        report_xml_resolved: jpath.to_string_lossy().into_owned(),
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
                "Could not run cargo nextest ({e}). Install: cargo install cargo-nextest"
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
            Some("cargo-nextest is required. Install: cargo install cargo-nextest".into());
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
