# Grafana

Optional **Grafana OSS** setup for dashboards (metrics, logs, or anything you wire later). This folder is **separate** from the Java / Python / Rust apps—use it when you want a local or lab visualization layer (often paired with Prometheus or Loki elsewhere).

## Layout

| Path | Purpose |
|------|---------|
| `docker-compose.yml` | Runs Grafana on port **3000** with provisioning mounts |
| `provisioning/datasources/` | Data source definitions loaded at startup |
| `provisioning/dashboards/` | Dashboard sidecar config (loads JSON from `dashboards/`) |
| `dashboards/` | Dashboard JSON (e.g. **`exercises-java-python-rust.json`** — Java / Python / Rust over Prometheus) |

## Bundled dashboard (root compose)

With the **repository root** `docker-compose.yml`, Grafana loads **`dashboards/exercises-java-python-rust.json`** into the **Exercises** folder. It expects Prometheus jobs **`exercises-java`**, **`exercises-python`**, and **`exercises-rust`** (see **`prometheus/prometheus.yml`**). After changing the JSON file, restart Grafana or wait for the file provider to rescan (if configured).

## Run locally

The **root** `docker-compose.yml` starts Grafana with the apps, **Prometheus**, and **ELK** (Elasticsearch, Logstash, Kibana). Use **either** that file **or** this folder’s compose — not both (duplicate port **3000**).

From **`grafana/`**:

```bash
podman compose up -d
```

With Docker Engine:

```bash
docker compose up -d
```

Open **http://127.0.0.1:3000/** when using the root compose file (it sets `GF_SERVER_ROOT_URL` to **127.0.0.1**). For this folder’s compose, **http://localhost:3000/** is fine. Default credentials are **`admin` / `admin`** (you may be prompted to change the password on first login unless you override env vars). **Do not** use these defaults outside a trusted local machine; set `GF_SECURITY_ADMIN_PASSWORD` (and stronger auth) for shared environments.

To stop:

```bash
podman compose down
```

(or `docker compose down`.)

## Default backends (datasources)

Grafana does **not** auto-discover your Java/Python/Rust HTTP ports. “Servers” here means **data sources** (Prometheus, Elasticsearch, Loki, TestData, …), configured under **`provisioning/datasources/`** and loaded on startup.

1. Edit **`provisioning/datasources/datasources.yml`** (or add more `*.yml` files in that folder).
2. Set each **`url`** to something Grafana’s container can reach:
   - **Same Compose project:** use the **service name** and internal port, e.g. `http://prometheus:9090`, `http://elasticsearch:9200` (only when that service exists).
3. **`isDefault: true`** — exactly one default; root compose ships **Prometheus** as default (see **`datasources.yml`**).
4. Restart Grafana: `podman compose restart grafana` (from repo root) or recreate the stack.

## Provisioning

- Edit **`provisioning/datasources/datasources.yml`** to add Loki, extra Prometheus targets, etc. ([Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)).
- Drop dashboard JSON under **`dashboards/`**; Grafana picks them up via **`provisioning/dashboards/dashboards.yml`**.

The shipped **`datasources.yml`** provisions **Prometheus** (**`http://prometheus:9090`**, default) and **TestData**. **Elasticsearch** is **commented** — uncomment when ELK runs in the same Compose project. The shipped **`dashboards/exercises-java-python-rust.json`** is a starter overview for the three app scrape jobs. Restart Grafana after edits.

## Embedding in an `<iframe>`

By default Grafana sends headers that **block** other sites from embedding it. This repo sets **`GF_SECURITY_ALLOW_EMBEDDING=true`** in **`docker-compose.yml`** (root and **`grafana/`**) so embedding works for **local / trusted** parent pages.

If the parent page is on a **different origin** than Grafana, logins and cookies can still be awkward (browser third‑cookie rules). Prefer **same host/port** or Grafana’s **anonymous** auth only for public embeds. **Turn embedding off** in production unless you understand the risk (`GF_SECURITY_ALLOW_EMBEDDING=false`).
