from __future__ import annotations

import logging
import os
import subprocess
import time
from pathlib import Path

from flask import Flask, Response, g, jsonify, redirect, render_template, request, session, url_for
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest

from exercises.web.controller_logging import log_error, log_received, log_succeeded, log_warn
from exercises.web.items_api import register_items_routes
from exercises.web.openapi_routes import register_openapi_routes
from exercises.web.observability_logging import observability_enabled
from exercises.web.request_id import resolve_request_id
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
_HTTP_REQUEST_LOG = logging.getLogger("http.request")
_APP_SOURCE = "src/exercises/web/app.py"
_APP_LOG = logging.getLogger(__name__)


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
    register_items_routes(app)
    register_openapi_routes(app)

    @app.before_request
    def _record_request_start() -> None:
        g.request_id = resolve_request_id(request)
        g._req_start = time.perf_counter()

    @app.after_request
    def after_request_hooks(response):
        _HTTP_REQUESTS.labels(request.method, request.endpoint or "unknown").inc()
        request_id = getattr(g, "request_id", None)
        if request_id:
            response.headers["X-Request-ID"] = request_id
        if observability_enabled():
            start = getattr(g, "_req_start", None)
            ms = int((time.perf_counter() - start) * 1000) if start is not None else 0
            _HTTP_REQUEST_LOG.info(
                "%s %s %s",
                request.method,
                request.path,
                response.status_code,
                extra={
                    "method": request.method,
                    "path": request.path,
                    "status": response.status_code,
                    "ms": ms,
                    "request_id": request_id,
                },
            )
        if request.endpoint in ("stack_landing", "tests_dashboard"):
            response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"
            response.headers["Pragma"] = "no-cache"
            response.headers["Expires"] = "0"
        return response

    @app.route("/")
    def stack_landing() -> str:
        log_received(_APP_LOG, "stack_landing", _APP_SOURCE, "GET", "/")
        links = StackLinks.from_env()
        html = render_template("landing.html", stack_links=links.browser_view())
        log_succeeded(_APP_LOG, "stack_landing", _APP_SOURCE, template="landing.html")
        return html

    @app.route("/tests")
    def tests_dashboard() -> str:
        log_received(_APP_LOG, "tests_dashboard", _APP_SOURCE, "GET", "/tests")
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
        page = render_template(
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
        log_succeeded(
            _APP_LOG,
            "tests_dashboard",
            _APP_SOURCE,
            result_count=len(test_results),
            has_report_file=has_report_file,
        )
        return page

    @app.get("/stack-ping/<target>")
    def stack_ping(target: str):
        log_received(
            _APP_LOG,
            "stack_ping",
            _APP_SOURCE,
            "GET",
            "/stack-ping/{target}",
            target=target,
        )
        links = StackLinks.from_env()
        result = links.ping(target)
        if result.get("ok") is False:
            log_warn(
                _APP_LOG,
                "stack_ping",
                _APP_SOURCE,
                "stack_ping downstream unreachable",
                target=target,
                ok=result.get("ok"),
                status=result.get("status"),
                error=result.get("error"),
                url=result.get("url"),
            )
        else:
            log_succeeded(
                _APP_LOG,
                "stack_ping",
                _APP_SOURCE,
                target=target,
                ok=result.get("ok"),
                status=result.get("status"),
            )
        return jsonify(result)

    @app.post("/tests/run")
    def run_tests() -> Response:
        root: Path = app.config["PROJECT_ROOT"]
        node = (request.form.get("nodeid") or "").strip() or None
        log_received(_APP_LOG, "run_tests", _APP_SOURCE, "POST", "/tests/run", nodeid=node)
        try:
            rc, log = run_pytest(root, node_id=node)
        except subprocess.TimeoutExpired:
            log_warn(_APP_LOG, "run_tests", _APP_SOURCE, "run_tests timed out", nodeid=node)
            session["test_run_error"] = "Pytest timed out."
            session["test_run_log_tail"] = ""
            return redirect(url_for("tests_dashboard"))
        except OSError as e:
            log_error(_APP_LOG, "run_tests", _APP_SOURCE, "run_tests failed", exc=e, nodeid=node)
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
            log_succeeded(_APP_LOG, "run_tests", _APP_SOURCE, nodeid=node, exit_code=rc)
        else:
            session["test_run_error"] = f"Pytest finished (exit code {rc})."
            log_warn(
                _APP_LOG,
                "run_tests",
                _APP_SOURCE,
                "run_tests finished with errors",
                nodeid=node,
                exit_code=rc,
            )
        return redirect(url_for("tests_dashboard"))

    @app.get("/tests/source")
    def test_source():
        cn = (request.args.get("className") or "").strip()
        log_received(
            _APP_LOG,
            "test_source",
            _APP_SOURCE,
            "GET",
            "/tests/source",
            className=cn,
        )
        root: Path = app.config["PROJECT_ROOT"]
        payload = read_test_source(root, cn)
        if payload is None:
            log_warn(_APP_LOG, "test_source", _APP_SOURCE, "test_source not found", className=cn)
            return jsonify(error="not found"), 404
        path, content = payload
        log_succeeded(_APP_LOG, "test_source", _APP_SOURCE, className=cn, path=path)
        return jsonify(path=path, content=content)

    @app.get("/metrics")
    def metrics() -> Response:
        """Prometheus scrape endpoint (see root `prometheus/prometheus.yml`)."""
        return Response(generate_latest(), mimetype=CONTENT_TYPE_LATEST)

    @app.get("/api/observability/sample-log")
    def observability_sample_log() -> str:
        """Emit one INFO JSON log line for Filebeat / ELK verification."""
        log_received(
            _APP_LOG,
            "observability_sample_log",
            _APP_SOURCE,
            "GET",
            "/api/observability/sample-log",
        )
        _APP_LOG.info(
            "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)",
            extra={"source": _APP_SOURCE, "controller": "observability_sample_log"},
        )
        log_succeeded(_APP_LOG, "observability_sample_log", _APP_SOURCE)
        return "logged"

    return app
