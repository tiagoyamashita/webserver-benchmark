use roxmltree::Node;
use serde::Serialize;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize)]
pub struct TestRow {
    pub method_name: String,
    pub class_name: String,
    pub status: String,
    pub seconds: f64,
    pub detail: String,
    /// Single-test filter passed to `cargo nextest run -E …`.
    pub node_id: String,
    pub pkg: String,
    pub simple_class: String,
    pub duration_label: String,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Status {
    Passed,
    Failed,
    Skipped,
}

fn local_name(tag: &str) -> &str {
    tag.rsplit_once(':').map(|(_, n)| n).unwrap_or(tag)
}

fn has_outcome(case: Node<'_, '_>, kind: &str) -> bool {
    case.children()
        .filter(|n| n.is_element())
        .any(|n| local_name(n.tag_name().name()) == kind)
}

fn truncate_one_line(s: &str, max: usize) -> String {
    let one: String = s.split_whitespace().collect::<Vec<_>>().join(" ");
    if one.len() <= max {
        return one;
    }
    format!("{}…", &one[..max.saturating_sub(1)])
}

fn first_message(case: Node<'_, '_>, kind: &str, max: usize) -> String {
    for child in case.children().filter(|n| n.is_element()) {
        if local_name(child.tag_name().name()) != kind {
            continue;
        }
        if let Some(m) = child.attribute("message").filter(|s| !s.trim().is_empty()) {
            return truncate_one_line(m.trim(), max);
        }
        let typ = child.attribute("type").unwrap_or("").trim();
        let body = child.text().unwrap_or("").trim();
        if !body.is_empty() {
            let line = if typ.is_empty() {
                body.to_string()
            } else {
                format!("{typ}: {body}")
            };
            return truncate_one_line(&line, max);
        }
        return truncate_one_line(typ, max);
    }
    String::new()
}

fn status_for(case: Node<'_, '_>) -> Status {
    if has_outcome(case, "failure")
        || has_outcome(case, "error")
        || has_outcome(case, "rerunFailure")
    {
        return Status::Failed;
    }
    if has_outcome(case, "skipped") {
        return Status::Skipped;
    }
    Status::Passed
}

fn detail_for(case: Node<'_, '_>, st: Status) -> String {
    match st {
        Status::Passed => String::new(),
        Status::Failed => {
            for tag in ["failure", "error", "rerunFailure"] {
                let d = first_message(case, tag, 450);
                if !d.is_empty() {
                    return d;
                }
            }
            String::new()
        }
        Status::Skipped => first_message(case, "skipped", 450),
    }
}

fn parse_time(raw: &str) -> f64 {
    raw.trim().parse().unwrap_or(0.0)
}

fn collect_testcases<'a>(suite: Node<'a, 'a>, cases: &mut Vec<(Node<'a, 'a>, String)>) {
    let suite_fallback = suite.attribute("name").unwrap_or("").to_string();
    for child in suite.children().filter(|n| n.is_element()) {
        let tag = local_name(child.tag_name().name());
        if tag == "testcase" {
            cases.push((child, suite_fallback.clone()));
        } else if tag == "testsuite" {
            collect_testcases(child, cases);
        }
    }
}

fn status_sort_key(st: Status) -> u8 {
    match st {
        Status::Failed => 0,
        Status::Skipped => 1,
        Status::Passed => 2,
    }
}

fn python_style_pkg(class_name: &str) -> String {
    match class_name.rfind("::") {
        None => "(crate)".to_string(),
        Some(i) => class_name[..i].to_string(),
    }
}

fn python_style_simple(class_name: &str) -> String {
    match class_name.rfind("::") {
        None => class_name.to_string(),
        Some(i) => class_name[i + 2..].to_string(),
    }
}

fn nextest_filter_for(test_name: &str) -> String {
    format!("test(^{}$)", regex::escape(test_name))
}

pub fn report_xml_path(project_root: &Path) -> PathBuf {
    project_root.join("reports").join("junit.xml")
}

pub fn parse_junit_file(path: &Path, _project_root: &Path) -> Vec<TestRow> {
    let Ok(text) = fs::read_to_string(path) else {
        return Vec::new();
    };
    let Ok(doc) = roxmltree::Document::parse(&text) else {
        return Vec::new();
    };
    let root = doc.root_element();
    let mut cases = Vec::new();
    match local_name(root.tag_name().name()) {
        "testsuites" => {
            for child in root.children().filter(|n| n.is_element()) {
                if local_name(child.tag_name().name()) == "testsuite" {
                    collect_testcases(child, &mut cases);
                }
            }
        }
        "testsuite" => collect_testcases(root, &mut cases),
        _ => {}
    }

    let mut rows: Vec<(TestRow, u8)> = Vec::new();
    for (el, suite_fb) in cases {
        let name = el.attribute("name").unwrap_or("").trim();
        let classname_raw = el
            .attribute("classname")
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .unwrap_or(suite_fb.trim());
        if name.is_empty() || classname_raw.is_empty() {
            continue;
        }
        let seconds = parse_time(el.attribute("time").unwrap_or("0"));
        let st = status_for(el);
        let detail = detail_for(el, st);
        let status = match st {
            Status::Passed => "PASSED",
            Status::Failed => "FAILED",
            Status::Skipped => "SKIPPED",
        };
        let node_id = nextest_filter_for(name);
        let pkg = python_style_pkg(classname_raw);
        let simple_class = python_style_simple(classname_raw);
        let duration_label = format!("{seconds:.3} s");
        let row = TestRow {
            method_name: name.to_string(),
            class_name: classname_raw.to_string(),
            status: status.to_string(),
            seconds,
            detail,
            node_id,
            pkg,
            simple_class,
            duration_label,
        };
        rows.push((row, status_sort_key(st)));
    }
    rows.sort_by(|a, b| {
        a.1.cmp(&b.1)
            .then_with(|| a.0.class_name.cmp(&b.0.class_name))
            .then_with(|| a.0.method_name.cmp(&b.0.method_name))
    });
    rows.into_iter().map(|(r, _)| r).collect()
}

pub fn load_latest_results(project_root: &Path) -> Vec<TestRow> {
    let path = report_xml_path(project_root);
    if !path.is_file() {
        return Vec::new();
    }
    parse_junit_file(&path, project_root)
}

pub fn existing_report_hints(project_root: &Path) -> Vec<String> {
    let d = crate::flash::reports_dir(project_root);
    vec![d.to_string_lossy().into_owned()]
}
