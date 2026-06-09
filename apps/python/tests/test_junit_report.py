from __future__ import annotations

from pathlib import Path

from exercises.web.junit_report import parse_junit_file


def test_parse_junit_roundtrip(tmp_path: Path) -> None:
    root = tmp_path
    (root / "tests").mkdir()
    (root / "tests" / "test_sample.py").write_text("def test_ok(): assert 1\n", encoding="utf-8")
    xml = root / "junit.xml"
    xml.write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="1" failures="0" errors="0" skipped="0">
    <testcase classname="tests.test_sample" name="test_ok" time="0.01"/>
  </testsuite>
</testsuites>
""",
        encoding="utf-8",
    )
    rows = parse_junit_file(xml, root)
    assert len(rows) == 1
    r = rows[0]
    assert r.method_name == "test_ok"
    assert r.class_name == "tests.test_sample"
    assert r.status.name == "PASSED"
    assert r.node_id == "tests/test_sample.py::test_ok"
