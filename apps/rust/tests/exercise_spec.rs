//! Specification tests for you to implement.
//!
//! They are **`#[ignore]`** so **`cargo test`** and **`cargo nextest run`** stay green until you
//! remove the attribute and replace each **`todo!()`** with real setup and assertions.
//!
//! Hints live in comments; use **`exercises_web::junit`**, **`exercises_web::flash`**, and
//! **`exercises_web::source`**.

// --- JUnit (`exercises_web::junit`) ---

#[test]
#[ignore = "Exercise: parse one passing testcase from minimal XML"]
fn junit_single_passing_testcase_row() {
    // Fixture idea: root `<testsuites>` → `<testsuite name="suite">` → `<testcase name="it_works"
    // classname="crate::demo::tests" time="0.042"/>`
    // Expect: `parse_junit_file` returns exactly one row with status PASSED, matching names,
    // seconds ~0.042, empty detail, and a sensible `node_id` / `pkg` / `simple_class`.
    todo!("write XML to a temp file under a temp dir; call parse_junit_file(path, project_root)");
}

#[test]
#[ignore = "Exercise: failure element contributes status and detail"]
fn junit_failed_testcase_includes_detail() {
    // Fixture: testcase with `<failure message="boom" type="AssertionError">details here</failure>`
    // Expect: status FAILED, `detail` non-empty and reflects message/body (see junit.rs rules).
    todo!("assert status and detail using parse_junit_file");
}

#[test]
#[ignore = "Exercise: skipped testcase maps to SKIPPED"]
fn junit_skipped_testcase() {
    // Fixture: testcase containing `<skipped message="not yet"/>`
    // Expect: status SKIPPED; detail may include skip reason.
    todo!("assert SKIPPED row");
}

#[test]
#[ignore = "Exercise: load_latest_results when report file is missing"]
fn junit_load_latest_results_missing_file() {
    // Use a temp project root with **no** `reports/junit.xml`.
    // Expect: `load_latest_results` returns an empty `Vec` (no panic).
    todo!("tempdir without reports/junit.xml; call load_latest_results");
}

#[test]
#[ignore = "Exercise: report_xml_path layout"]
fn junit_report_xml_path_under_reports() {
    // Expect: `report_xml_path(root).ends_with(reports/junit.xml)` (or equivalent components).
    todo!("assert report_xml_path for a sample PathBuf");
}

// --- Flash (`exercises_web::flash`) ---

#[test]
#[ignore = "Exercise: write_flash then take_flash round-trip"]
fn flash_write_then_take() {
    // Write a `RunFlash` with `message: Some("ok".into())`, then `take_flash` twice.
    // Expect: first `take_flash` returns that message; second returns default (file removed).
    todo!("use tempdir as project_root; call flash::write_flash, flash::take_flash, and struct RunFlash");
}

// --- Source (`exercises_web::source`) ---

#[test]
#[ignore = "Exercise: resolve_rs_file for this crate"]
fn source_resolve_existing_rs_file() {
    // Use `Path::new(env!("CARGO_MANIFEST_DIR"))` as `project_root`.
    // Pick a class name that maps to a real file, e.g. `crate::junit` → `src/junit.rs`
    // Expect: `resolve_rs_file` returns `Some` with that path.
    todo!("call exercises_web::source::resolve_rs_file(project_root, \"crate::junit\") and assert");
}

#[test]
#[ignore = "Exercise: read_rust_source rejects path traversal"]
fn source_read_rejects_bad_class_names() {
    // Expect: invalid or suspicious `class_name` yields `None` from `read_rust_source`.
    todo!("try class names that should not resolve or should fail the regex guard");
}
