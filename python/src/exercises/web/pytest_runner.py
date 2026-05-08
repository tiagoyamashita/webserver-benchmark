from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from exercises.web.junit_report import report_xml_path


def run_pytest(
    project_root: Path,
    *,
    node_id: str | None = None,
    timeout: int = 600,
) -> tuple[int, str]:
    """Run pytest, write JUnit to ``reports/junit.xml``. Returns (exit_code, combined_log)."""
    root = project_root.resolve()
    out_xml = report_xml_path(root)
    out_xml.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable,
        "-m",
        "pytest",
        "-v",
        f"--junitxml={out_xml}",
        "--tb=short",
    ]
    if node_id:
        cmd.append(node_id)
    else:
        cmd.append("tests")
    proc = subprocess.run(
        cmd,
        cwd=root,
        capture_output=True,
        text=True,
        timeout=timeout,
        encoding="utf-8",
        errors="replace",
    )
    out = (proc.stdout or "") + ((proc.stderr or "") and "\n" + (proc.stderr or ""))
    return proc.returncode, out
