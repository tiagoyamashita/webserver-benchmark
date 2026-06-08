from __future__ import annotations

import logging
import os
import subprocess
from pathlib import Path

from flask import Flask, Response, jsonify, redirect, render_template, request, session, url_for
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest

from exercises.web.junit_report import (
    existing_report_hints,
    load_latest_results,
    report_xml_path,
)
from exercises.web.pytest_runner import run_pytest
from exercises.web.source_service import read_test_source
from exercises.web.stack_ping import StackLinks

# Module-level so repeated `create_app()` (e.g. pytest) does not re-register on the default registry.
_HTTP_REQUESTS = Counter(
    "exercises_http_requests_total",
    "HTTP requests handled by the exercises Flask app",
    ["method", "endpoint"],
)


def resolve_project_root() -> Path:
    override = os.environ.get("EXERCISES_PROJECT_ROOT", "").strip()
    if override:
        return Path(override).expanduser().resolve()
    here = Path(__file__).resolve()
    # Editable layout: <root>/src/exercises/web/app.py — tests and reports live under <root>.
    if (
        here.name == "app.py"
        and here.parent.name == "web"
        and len(here.parents) > 3
    ):
        candidate = here.parents[3]
        if (candidate / "pyproject.toml").is_file() and (candidate / "tests").is_dir():
            return candidate
    for parent in here.parents:
        if (parent / "pyproject.toml").is_file() and (parent / "tests").is_dir():
            return parent
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

    @app.after_request
    def after_request_hooks(response):
        _HTTP_REQUESTS.labels(request.method, request.endpoint or "unknown").inc()
        if request.endpoint in ("stack_landing", "tests_dashboard"):
            response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"
            response.headers["Pragma"] = "no-cache"
            response.headers["Expires"] = "0"
        return response

    @app.route("/")
    def stack_landing() -> str:
        links = StackLinks.from_env()
        return render_template("landing.html", stack_links=links.browser_view())

    @app.route("/tests")
    def tests_dashboard() -> str:
        root: Path = app.config["PROJECT_ROOT"]
        jpath = report_xml_path(root)
        has_report_file = jpath.is_file()
        test_results = load_latest_results(root)
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
            has_report_file=has_report_file,
            report_xml_resolved=str(jpath.resolve()),
            project_root=str(root.resolve()),
            test_run_message=test_run_message,
            test_run_error=test_run_error,
            test_run_log_tail=test_run_log_tail,
        )

    @app.get("/stack-ping/<target>")
    def stack_ping(target: str):
        links = StackLinks.from_env()
        return jsonify(links.ping(target))

    @app.post("/tests/run")
    def run_tests() -> Response:
        root: Path = app.config["PROJECT_ROOT"]
        node = (request.form.get("nodeid") or "").strip() or None
        try:
            rc, log = run_pytest(root, node_id=node)
        except subprocess.TimeoutExpired:
            session["test_run_error"] = "Pytest timed out."
            session["test_run_log_tail"] = ""
            return redirect(url_for("tests_dashboard"))
        except OSError as e:
            session["test_run_error"] = f"Could not run pytest: {e}"
            session["test_run_log_tail"] = ""
            return redirect(url_for("tests_dashboard"))
        tail = log[-12000:] if len(log) > 12000 else log
        session["test_run_log_tail"] = tail
        if "No module named pytest" in log or "No module named 'pytest'" in log:
            session["test_run_error"] = (
                "pytest is not installed in this Python environment. "
                "Use the project venv and install dev deps (e.g. pip install -r requirements-dev.txt)."
            )
            session.pop("test_run_message", None)
            return redirect(url_for("tests_dashboard"))
        if rc == 0:
            session["test_run_message"] = "Pytest finished (exit code 0)."
        else:
            session["test_run_error"] = f"Pytest finished (exit code {rc})."
        return redirect(url_for("tests_dashboard"))

    @app.get("/tests/source")
    def test_source():
        cn = (request.args.get("className") or "").strip()
        root: Path = app.config["PROJECT_ROOT"]
        payload = read_test_source(root, cn)
        if payload is None:
            return jsonify(error="not found"), 404
        path, content = payload
        return jsonify(path=path, content=content)

    @app.get("/metrics")
    def metrics() -> Response:
        """Prometheus scrape endpoint (see root `prometheus/prometheus.yml`)."""
        return Response(generate_latest(), mimetype=CONTENT_TYPE_LATEST)

    @app.get("/api/observability/sample-log")
    def observability_sample_log() -> str:
        """Emit one INFO JSON log line for Filebeat / ELK verification."""
        logging.getLogger(__name__).info(
            "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)"
        )
        return "logged"

    return app
