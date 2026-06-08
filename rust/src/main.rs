#[tokio::main]
async fn main() {
    exercises_web::tracing_init::init();
    if let Err(e) = exercises_web::run_server().await {
        eprintln!("exercises-web could not start: {e}");
        std::process::exit(1);
    }
}
