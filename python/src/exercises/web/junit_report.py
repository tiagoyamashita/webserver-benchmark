from __future__ import annotations

import xml.etree.ElementTree as ET
from pathlib import Path

from exercises.web.pytest_nodeid import pytest_node_id
from exercises.web.test_result_row import Status, TestResultRow


def _local_tag(el: ET.Element) -> str:
    tag = el.tag
    if tag.startswith("{"):
        return tag.split("}", 1)[1]
    return tag


def _has_outcome(parent: ET.Element, local: str) -> bool:
    for el in parent:
        if _local_tag(el) == local:
            return True
    return False


def _first_message(parent: ET.Element, local: str, max_len: int = 450) -> str:
    for el in parent:
        if _local_tag(el) != local:
            continue
        msg = (el.get("message") or "").strip()
        if msg:
            return _truncate_one_line(msg, max_len)
        typ = (el.get("type") or "").strip()
        body = (el.text or "").strip()
        if body:
            return _truncate_one_line(f"{typ}: {body}" if typ else body, max_len)
        return _truncate_one_line(typ, max_len)
    return ""


def _truncate_one_line(s: str, max_len: int) -> str:
    one = " ".join(s.split())
    if len(one) <= max_len:
        return one
    return one[: max_len - 1] + "…"


def _status_for(testcase: ET.Element) -> Status:
    if (
        _has_outcome(testcase, "failure")
        or _has_outcome(testcase, "error")
        or _has_outcome(testcase, "rerunFailure")
    ):
        return Status.FAILED
    if _has_outcome(testcase, "skipped"):
        return Status.SKIPPED
    return Status.PASSED


def _detail_for(testcase: ET.Element, status: Status) -> str:
    if status is Status.PASSED:
        return ""
    if status is Status.FAILED:
        for tag in ("failure", "error", "rerunFailure"):
            d = _first_message(testcase, tag)
            if d:
                return d
        return ""
    return _first_message(testcase, "skipped")


def _parse_time(raw: str) -> float:
    raw = (raw or "").strip()
    if not raw:
        return 0.0
    try:
        return float(raw)
    except ValueError:
        return 0.0


def _collect_testcases(suite: ET.Element, cases: list[tuple[ET.Element, str]]) -> None:
    suite_fallback = (suite.get("name") or "").strip()
    for child in suite:
        ln = _local_tag(child)
        if ln == "testcase":
            cases.append((child, suite_fallback))
        elif ln == "testsuite":
            _collect_testcases(child, cases)


def _status_sort_key(row: TestResultRow) -> int:
    order = {Status.FAILED: 0, Status.SKIPPED: 1, Status.PASSED: 2}
    return order[row.status]


def parse_junit_file(path: Path, project_root: Path) -> list[TestResultRow]:
    try:
        tree = ET.parse(path)
        root = tree.getroot()
    except (ET.ParseError, OSError):
        return []
    cases: list[tuple[ET.Element, str]] = []
    if _local_tag(root) == "testsuites":
        for child in root:
            if _local_tag(child) == "testsuite":
                _collect_testcases(child, cases)
    elif _local_tag(root) == "testsuite":
        _collect_testcases(root, cases)
    out: list[TestResultRow] = []
    for el, suite_fallback in cases:
        name = (el.get("name") or "").strip()
        classname = (el.get("classname") or "").strip() or suite_fallback
        if not name or not classname:
            continue
        seconds = _parse_time(el.get("time") or "")
        st = _status_for(el)
        detail = _detail_for(el, st)
        try:
            node_id = pytest_node_id(project_root, classname, name)
        except (OSError, ValueError):
            node_id = f"{classname}::{name}"
        out.append(
            TestResultRow(
                method_name=name,
                class_name=classname,
                status=st,
                seconds=seconds,
                detail=detail,
                node_id=node_id,
            )
        )
    return out


def report_xml_path(project_root: Path) -> Path:
    return project_root / "reports" / "junit.xml"


def load_latest_results(project_root: Path) -> list[TestResultRow]:
    path = report_xml_path(project_root)
    if not path.is_file():
        return []
    rows = parse_junit_file(path, project_root)
    return sorted(
        rows,
        key=lambda r: (_status_sort_key(r), r.class_name, r.method_name),
    )


def existing_report_hints(project_root: Path) -> list[str]:
    d = project_root / "reports"
    if d.is_dir():
        return [str(d.resolve())]
    return [str((project_root / "reports").resolve())]
