use std::io;
use std::path::Path;
use std::process::Command;

/// Run `cargo nextest` with the `dashboard` profile. JUnit is written under `target/nextest/dashboard/junit.xml` (see `.config/nextest.toml`).
pub fn run_nextest(project_root: &Path, filter_expr: Option<&str>) -> io::Result<(i32, String)> {
    let mut cmd = Command::new("cargo");
    cmd.current_dir(project_root);
    cmd.args([
        "nextest",
        "run",
        "--profile",
        "dashboard",
        "--color",
        "always",
    ]);
    if let Some(expr) = filter_expr {
        let e = expr.trim();
        if !e.is_empty() {
            cmd.arg("-E").arg(e);
        }
    }
    let out = cmd.output()?;
    let code = out.status.code().unwrap_or(-1);
    let mut combined = String::new();
    let stdout = String::from_utf8_lossy(&out.stdout);
    let stderr = String::from_utf8_lossy(&out.stderr);
    if !stdout.is_empty() {
        combined.push_str(&stdout);
    }
    if !stderr.is_empty() {
        if !combined.is_empty() {
            combined.push('\n');
        }
        combined.push_str(&stderr);
    }
    Ok((code, combined))
}
