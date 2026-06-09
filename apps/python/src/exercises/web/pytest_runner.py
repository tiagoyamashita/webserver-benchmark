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
    """Run pytest; XML report path comes from ``pyproject.toml`` ``addopts`` (``--junitxml=reports/junit.xml``)."""
    root = project_root.resolve()
    report_xml_path(root).parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable,
        "-m",
        "pytest",
        "-v",
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
