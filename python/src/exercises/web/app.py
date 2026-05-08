from __future__ import annotations

import os
import subprocess
from pathlib import Path

from flask import Flask, Response, redirect, render_template, request, session, url_for

from exercises.web.junit_report import (
    existing_report_hints,
    load_latest_results,
    report_xml_path,
)
from exercises.web.pytest_runner import run_pytest
from exercises.web.source_service import read_test_source


def resolve_project_root() -> Path:
    override = os.environ.get("EXERCISES_PROJECT_ROOT", "").strip()
    if override:
        return Path(override).expanduser().resolve()
    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "pyproject.toml").is_file():
            return parent
    return Path.cwd().resolve()


def create_app() -> Flask:
    pkg_dir = Path(__file__).resolve().parent
    static_dir = pkg_dir / "static"
    static_dir.mkdir(exist_ok=True)
    app = Flask(
        __name__,
        template_folder=str(pkg_dir / "templates"),
        static_folder=str(static_dir),
    )
    app.secret_key = os.environ.get("SECRET_KEY", "dev-exercises-web")
    app.config["PROJECT_ROOT"] = resolve_project_root()

    @app.route("/")
    def home() -> str:
        root: Path = app.config["PROJECT_ROOT"]
        test_results = load_latest_results(root)
        jpath = report_xml_path(root)
        if jpath.is_file():
            report_sources = [str(jpath.resolve())]
        else:
            report_sources = existing_report_hints(root)
        test_run_message = session.pop("test_run_message", None)
        test_run_error = session.pop("test_run_error", None)
        test_run_log_tail = session.pop("test_run_log_tail", None)
        return render_template(
            "home.html",
            test_results=test_results,
            report_sources=report_sources,
            test_run_message=test_run_message,
            test_run_error=test_run_error,
            test_run_log_tail=test_run_log_tail,
        )

    @app.post("/tests/run")
    def run_tests() -> Response:
        root: Path = app.config["PROJECT_ROOT"]
        node = (request.form.get("nodeid") or "").strip() or None
        try:
            rc, log = run_pytest(root, node_id=node)
        except subprocess.TimeoutExpired:
            session["test_run_error"] = "Pytest timed out."
            session["test_run_log_tail"] = ""
            return redirect(url_for("home"))
        except OSError as e:
            session["test_run_error"] = f"Could not run pytest: {e}"
            session["test_run_log_tail"] = ""
            return redirect(url_for("home"))
        tail = log[-12000:] if len(log) > 12000 else log
        session["test_run_log_tail"] = tail
        if rc == 0:
            session["test_run_message"] = "Pytest finished (exit code 0)."
        else:
            session["test_run_error"] = f"Pytest finished (exit code {rc})."
        return redirect(url_for("home"))

    @app.get("/tests/source")
    def test_source():
        from flask import jsonify

        cn = (request.args.get("className") or "").strip()
        root: Path = app.config["PROJECT_ROOT"]
        payload = read_test_source(root, cn)
        if payload is None:
            return jsonify(error="not found"), 404
        path, content = payload
        return jsonify(path=path, content=content)

    return app
