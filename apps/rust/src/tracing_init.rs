//! Console + optional JSON file logging for Filebeat -> Logstash -> Elasticsearch.
//!
//! File logs use flattened JSON (same shape as Java/Python) so Grafana/Kibana can query
//! top-level `status`, `request_id`, `controller`, etc.

use std::fs::{self, OpenOptions};
use std::io;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

fn observability_enabled() -> bool {
    crate::obs_log::observability_enabled()
}

fn log_dir() -> PathBuf {
    PathBuf::from(std::env::var("LOG_PATH").unwrap_or_else(|_| "logs".into()))
}

pub fn init() {
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("info"));

    if observability_enabled() {
        let dir = log_dir();
        let _ = fs::create_dir_all(&dir);
        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(dir.join("demo-app.json.log"))
            .unwrap_or_else(|e| panic!("open observability log file: {e}"));
        let shared = Arc::new(Mutex::new(file));
        crate::obs_log::init_shared_log_file(shared.clone());
        // flatten_event: emit status/request_id/controller at JSON root (not under "fields").
        let file_layer = fmt::layer()
            .json()
            .flatten_event(true)
            .with_writer(crate::obs_log::SharedLogWriter(shared));
        let stderr_layer = fmt::layer().with_writer(io::stderr);
        tracing_subscriber::registry()
            .with(filter)
            .with(stderr_layer)
            .with(file_layer)
            .init();
    } else {
        tracing_subscriber::fmt().with_env_filter(filter).init();
    }
}
