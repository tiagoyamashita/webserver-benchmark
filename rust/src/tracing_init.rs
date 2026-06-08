//! Console + optional JSON file logging for Filebeat -> Logstash -> Elasticsearch.

use std::fs::{self, OpenOptions};
use std::io;
use std::path::PathBuf;

use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

fn observability_enabled() -> bool {
    std::env::var("EXERCISES_OBSERVABILITY")
        .map(|v| matches!(v.to_ascii_lowercase().as_str(), "1" | "true" | "yes"))
        .unwrap_or(false)
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
        let file_layer = fmt::layer().json().with_writer(file);
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
