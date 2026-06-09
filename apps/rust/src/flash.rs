use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

const FLASH_NAME: &str = ".last_run_flash.json";

#[derive(Debug, Default, Serialize, Deserialize)]
pub struct RunFlash {
    pub message: Option<String>,
    pub error: Option<String>,
    pub log_tail: Option<String>,
}

pub fn reports_dir(project_root: &Path) -> PathBuf {
    project_root.join("reports")
}

/// Read and delete the one-shot flash file (same idea as Flask session pop).
pub fn take_flash(project_root: &Path) -> RunFlash {
    let path = reports_dir(project_root).join(FLASH_NAME);
    let data = std::fs::read_to_string(&path).ok();
    let _ = std::fs::remove_file(&path);
    data.and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

pub fn write_flash(project_root: &Path, flash: &RunFlash) {
    let dir = reports_dir(project_root);
    let _ = std::fs::create_dir_all(&dir);
    let path = dir.join(FLASH_NAME);
    if let Ok(s) = serde_json::to_string(flash) {
        let _ = std::fs::write(path, s);
    }
}
