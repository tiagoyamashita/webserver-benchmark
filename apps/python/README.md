# Python exercises

## Virtual environment

From this directory (`python/`):

**Windows (PowerShell)**

```powershell
.\scripts\init_venv.ps1
```

**macOS / Linux**

```bash
chmod +x scripts/init_venv.sh
./scripts/init_venv.sh
```

This creates `exercises/` (the virtual environment directory), upgrades `pip`, and installs the project in editable mode plus dev tools from `requirements-dev.txt`.

## Test dashboard (Flask)

From `python/` with the venv activated:

```bash
exercises-web
```

Open `http://127.0.0.1:5000/` for **stack connectivity** (GET probes to Java, Rust, Prometheus, Grafana, ELK, React Node). The **pytest dashboard** is at `http://127.0.0.1:5000/tests` — it loads `reports/junit.xml`, lists pytest results, and **Run all tests** / **Re-run** invoke pytest on the server (for local development). **`GET /metrics`** exposes **Prometheus** text format (`prometheus-client`); the root **`docker-compose.yml`** **`prometheus`** service scrapes it, and **Grafana** is provisioned against Prometheus. Optional: `EXERCISES_PROJECT_ROOT` points at another checkout; default is the directory that contains `pyproject.toml`.

**Postgres items API:** with `DB_HOST` set (root Compose), Flask exposes **`/api/items`** CRUD against the shared `items` table (Flyway schema + seed from Java). Without `DB_*` env vars the routes return **503**.

## Observability and logging

When **`EXERCISES_OBSERVABILITY=1`** (set in root Compose), the app appends JSON lines to **`${LOG_PATH}/demo-app.json.log`** (default `logs/demo-app.json.log`) for Filebeat → Logstash → Elasticsearch. Console logging is unchanged.

### Correlation: `request_id` and `session_id`

Every log line inside an HTTP request should carry the same correlation fields as the Java app, so you can filter a full flow in Kibana.

| Field | Source |
|-------|--------|
| **`request_id`** | `X-Request-ID` on the inbound request, or a new UUID (`app.py` `before_request` → `g.request_id`) |
| **`session_id`** | Shared Redis session from `Authorization: Bearer …`, `X-Session-ID`, or the `exercises_session` cookie (`g.shared_session`) |

Central pieces (mirror Java `ObservabilityJsonProvider`):

| Module | Role |
|--------|------|
| **`src/exercises/web/correlation.py`** | `current_correlation()` reads `g.request_id` and `g.shared_session`; `CorrelationFilter` injects both onto every log record |
| **`src/exercises/web/observability_logging.py`** | Attaches `CorrelationFilter` to the JSON file handler |
| **`src/exercises/web/controller_logging.py`** | `log_received` / `log_succeeded` / `log_warn` / `log_error` merge correlation via `merge_correlation()` |

Any logger call during a request gets `request_id` and `session_id` automatically—including service-layer logs that do not use the controller helpers (for example Postgres connect failures in **`db.py`** or Redis failures in **`session_repository.py`**). Explicit `extra={"request_id": …}` values are not overwritten.

Outside a Flask request (startup, background work), those fields are omitted.

### Postgres `application_name` (not an HTTP header)

Postgres does not receive `X-Request-ID` as a header. Python stamps the active request id on the DB session the same way Java and Rust do:

| App | Mechanism |
|-----|-----------|
| **Java** | `RequestIdDataSource` → `SET application_name TO 'exercises-java;req=<uuid>'` |
| **Rust** | `stamp_application_name` → `set_config('application_name', …)` |
| **Python** | `db.connection()` → `psycopg.connect(..., application_name=…)` plus `set_config` |

`resolve_postgres_request_id()` reads `g.request_id` (from inbound `X-Request-ID`) when callers omit an explicit id—mirroring Java `RequestIdContext`. In Kibana Postgres logs, filter on runtime field **`correlation.request_id`** or `application_name` containing `;req=`.

### HTTP access logs

Logger **`http.request`** emits structured access lines:

- **received** — method, path, headers, url_params, body
- **completed** — status, ms; **POST** responses with status ≥ 300 log at **WARN** with an **`error`** field parsed from the response body

### Controller logs

Handlers use **`controller_logging`** (see `.cursor/skills/controller-logging/`). Pattern per handler:

1. **INFO** — request received (method, path, params)
2. **INFO** — succeeded, or **WARN** / **ERROR** with replay params

Do not duplicate `request_id` in controller `extra` fields; the correlation layer adds it.

### Service failures

Connection helpers log failures at the service layer:

| Service | Module | Levels |
|---------|--------|--------|
| **Postgres** | `db.py` | **WARN** not configured; **ERROR** on `psycopg.connect()` failure (`service`, `host`, `port`, `dbname`) |
| **Redis** | `session_repository.py` | **WARN** not configured; **ERROR** on ping failure |
| **Stack probes** | `stack_ping.py` | **WARN** when Postgres, Redis, or outbound HTTP probes fail |

Items API controllers also log with **`service=postgres`** on database errors.

### Kibana

Use the **Logs by tiago** data view (`logstash-*`). Correlate a user action with:

```text
request_id: "<uuid from dashboard or X-Request-ID>"
```

or

```text
session_id_resolved: "<session uuid>"
```

A Java → Python item relay should show Java inbound/outbound lines, Python `http.request`, `postgres_connect` (if DB fails), and `create_item` controller lines sharing the same **`request_id`**.

## Docker builds

The **`Dockerfile`** installs third-party packages from **`requirements.txt`** in a separate layer **before** copying **`src/`**, so **`podman compose build python`** reuses the pip cache when you only change Python code. The first build still downloads wheels (Flask, **psycopg**, pytest); later rebuilds are much faster. Use **`docker-compose.dev.yml`** for day-to-day edits without rebuilding on every change.
