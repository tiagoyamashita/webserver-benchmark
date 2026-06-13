//! Structured JSON file lines (Java/Python-compatible nested `headers`, `body`, etc.).

use chrono::Utc;
use serde_json::{json, Map, Value};
use std::fs::File;
use std::io::{self, Write};
use std::sync::{Arc, Mutex, OnceLock};

static SHARED_LOG: OnceLock<Arc<Mutex<File>>> = OnceLock::new();

/// Share the observability log file with `tracing_subscriber` (one mutex per line).
pub fn init_shared_log_file(shared: Arc<Mutex<File>>) {
    let _ = SHARED_LOG.set(shared);
}

pub fn observability_enabled() -> bool {
    std::env::var("EXERCISES_OBSERVABILITY")
        .map(|v| matches!(v.to_ascii_lowercase().as_str(), "1" | "true" | "yes"))
        .unwrap_or(false)
}

pub fn map_to_json(map: &Map<String, Value>) -> Value {
    Value::Object(map.clone())
}

pub fn write_json_line(entry: Value) {
    let Some(shared) = SHARED_LOG.get() else {
        return;
    };
    let mut file = shared.lock().expect("obs log mutex");
    if let Ok(line) = serde_json::to_string(&entry) {
        let _ = writeln!(file, "{line}");
    }
    let _ = file.flush();
}

pub fn log_http_request_received(
    method: &str,
    path: &str,
    request_id: &str,
    session_id: Option<&str>,
    log_seq: u64,
    headers: &Map<String, Value>,
    url_params: &Map<String, Value>,
    body: &Map<String, Value>,
) {
    if !observability_enabled() {
        let _ = (headers, url_params, body);
        if let Some(session_id) = session_id {
            tracing::info!(
                target: "http.request",
                method = %method,
                path = %path,
                request_id = %request_id,
                session_id = %session_id,
                log_seq = log_seq,
                phase = "received",
                "{method} {path} request received request_id={request_id}"
            );
        } else {
            tracing::info!(
                target: "http.request",
                method = %method,
                path = %path,
                request_id = %request_id,
                log_seq = log_seq,
                phase = "received",
                "{method} {path} request received request_id={request_id}"
            );
        }
        return;
    }
    let message = format!("{method} {path} request received request_id={request_id}");
    let mut entry = json!({
        "timestamp": Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true),
        "level": "INFO",
        "message": message,
        "target": "http.request",
        "method": method,
        "path": path,
        "request_id": request_id,
        "log_seq": log_seq,
        "phase": "received",
        "headers": map_to_json(headers),
        "url_params": map_to_json(url_params),
        "body": map_to_json(body),
    });
    if let Some(session_id) = session_id {
        entry["session_id"] = Value::String(session_id.to_string());
    }
    write_json_line(entry);
}

pub fn log_controller_received(
    source: &str,
    controller: &str,
    method: &str,
    path: &str,
    headers: &Map<String, Value>,
    url_params: &Map<String, Value>,
    body: &Map<String, Value>,
) {
    if !observability_enabled() {
        let _ = (headers, url_params, body);
        tracing::info!(
            source = source,
            controller = controller,
            method = method,
            path = path,
            "{controller} request received"
        );
        return;
    }
    write_json_line(json!({
        "timestamp": Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true),
        "level": "INFO",
        "message": format!("{controller} request received"),
        "source": source,
        "controller": controller,
        "method": method,
        "path": path,
        "headers": map_to_json(headers),
        "url_params": map_to_json(url_params),
        "body": map_to_json(body),
    }));
}

/// `MakeWriter` target for tracing JSON logs (same file + mutex as `write_json_line`).
pub struct SharedLogWriter(pub Arc<Mutex<File>>);

impl<'a> tracing_subscriber::fmt::writer::MakeWriter<'a> for SharedLogWriter {
    type Writer = SharedLogWriterHandle;

    fn make_writer(&'a self) -> Self::Writer {
        SharedLogWriterHandle(self.0.clone())
    }
}

pub struct SharedLogWriterHandle(Arc<Mutex<File>>);

impl Write for SharedLogWriterHandle {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.0.lock().expect("obs log mutex").write(buf)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.0.lock().expect("obs log mutex").flush()
    }
}
