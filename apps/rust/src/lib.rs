//! Library crate: Axum app + JUnit parsing for the local test dashboard.

pub mod app;
pub mod auth;
pub mod db;
pub mod http_access_logging;
pub mod request_id;
pub mod stack_ping;
pub mod tracing_init;
pub mod items;
pub mod kafka;
pub mod users;
pub mod obs_log;
pub mod openapi;
pub mod flash;
pub mod junit;
pub mod runner;
pub mod source;

pub fn add(left: i32, right: i32) -> i32 {
    left + right
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn adds() {
        assert_eq!(add(2, 3), 5);
    }
}

pub async fn run_server() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    app::serve().await
}
