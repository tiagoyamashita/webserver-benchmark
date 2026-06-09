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

## Docker builds

The **`Dockerfile`** installs third-party packages from **`requirements.txt`** in a separate layer **before** copying **`src/`**, so **`podman compose build python`** reuses the pip cache when you only change Python code. The first build still downloads wheels (Flask, **psycopg**, pytest); later rebuilds are much faster. Use **`docker-compose.dev.yml`** for day-to-day edits without rebuilding on every change.
