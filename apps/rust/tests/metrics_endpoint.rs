//! Prometheus `/metrics` scrape endpoint (see root `prometheus/prometheus.yml`).

use axum::body::Body;
use axum::http::{Request, StatusCode};
use exercises_web::app::{build_router, load_tera, AppState};
use std::path::PathBuf;
use tower::ServiceExt;

#[tokio::test]
async fn metrics_endpoint_exposes_prometheus_format() {
    let project_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let tera = load_tera(&project_root).expect("tera templates");
    let state = AppState::new(project_root, tera, None, None, None);
    let app = build_router(state);

    let response = app
        .oneshot(
            Request::builder()
                .uri("/metrics")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let body = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let text = String::from_utf8(body.to_vec()).unwrap();
    assert!(text.contains("webserver_benchmark_http_requests_total"));
    assert!(text.contains("# HELP") || text.contains("# TYPE"));
}
