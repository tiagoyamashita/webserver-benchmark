from __future__ import annotations

import re
from pathlib import Path

from exercises.web.pytest_nodeid import find_py_file

_SAFE_CLASSNAME = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_.]*$")
_MAX_BYTES = 450_000


def read_test_source(project_root: Path, classname: str) -> tuple[str, str] | None:
    """Return (absolute_path, content) for the test module backing this junit classname."""
    if not classname or not _SAFE_CLASSNAME.match(classname.strip()):
        return None
    cn = classname.strip()
    path = find_py_file(project_root, cn)
    if path is None:
        return None
    try:
        size = path.stat().st_size
    except OSError:
        return None
    if size > _MAX_BYTES:
        return (
            str(path),
            f"File is too large to show here ({size} bytes). Open it in your editor.",
        )
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    return str(path.resolve()), text
