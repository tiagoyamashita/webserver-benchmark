from __future__ import annotations

from pathlib import Path


def find_py_file(project_root: Path, classname: str) -> Path | None:
    """Map junit ``classname`` to a ``.py`` file under the project."""
    root = project_root.resolve()
    parts = classname.split(".")
    for i in range(len(parts), 0, -1):
        rel = Path(*parts[:i]).with_suffix(".py")
        for base in (root, root / "src"):
            candidate = (base / rel).resolve()
            try:
                candidate.relative_to(root)
            except ValueError:
                continue
            if candidate.is_file():
                return candidate
    return None


def pytest_node_id(project_root: Path, classname: str, method_name: str) -> str:
    """Build a pytest node id (``path::[Class::]name``) for ``pytest NODEID``."""
    root = project_root.resolve()
    path = find_py_file(project_root, classname)
    parts = classname.split(".")
    if path is None:
        rel = Path(*parts).with_suffix(".py")
        path_str = rel.as_posix()
        remainder: list[str] = []
    else:
        path = path.resolve()
        path_str = path.relative_to(root).as_posix()
        mod_parts = path.relative_to(root).with_suffix("").parts
        remainder = parts[len(mod_parts) :]
    if remainder:
        return f"{path_str}::{'::'.join(remainder)}::{method_name}"
    return f"{path_str}::{method_name}"
