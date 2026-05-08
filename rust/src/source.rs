use regex::Regex;
use std::fs;
use std::path::{Path, PathBuf};

const MAX_BYTES: u64 = 450_000;

fn strip_crate_prefix(s: &str) -> &str {
    s.strip_prefix("crate::").unwrap_or(s)
}

/// Map a nextest `classname` / Rust module path to a `.rs` file under `src/` or `tests/`.
pub fn resolve_rs_file(project_root: &Path, class_name: &str) -> Option<PathBuf> {
    let trimmed = class_name.trim();
    let body = strip_crate_prefix(trimmed);
    let parts: Vec<&str> = body.split("::").filter(|p| !p.is_empty()).collect();
    if parts.is_empty() {
        return None;
    }
    for i in (1..=parts.len()).rev() {
        let mut rel = PathBuf::new();
        for part in &parts[..i] {
            rel.push(part);
        }
        let with_rs = rel.with_extension("rs");
        for base in ["src", "tests"] {
            let candidate = project_root.join(base).join(&with_rs);
            if candidate.is_file() {
                return Some(candidate);
            }
        }
    }
    None
}

pub fn read_rust_source(project_root: &Path, class_name: &str) -> Option<(String, String)> {
    let re = Regex::new(r"^[a-zA-Z_][a-zA-Z0-9_:]*$").ok()?;
    if !re.is_match(class_name.trim()) {
        return None;
    }
    let path = resolve_rs_file(project_root, class_name)?;
    let root = project_root.canonicalize().ok()?;
    let path = path.canonicalize().ok()?;
    if !path.starts_with(&root) {
        return None;
    }
    let size = fs::metadata(&path).ok()?.len();
    if size > MAX_BYTES {
        return Some((
            path.to_string_lossy().into_owned(),
            format!("File is too large to show here ({size} bytes). Open it in your editor."),
        ));
    }
    let content = fs::read_to_string(&path).ok()?;
    Some((path.to_string_lossy().into_owned(), content))
}
