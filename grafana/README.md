# Grafana

Optional **Grafana OSS** setup for dashboards (metrics, logs, or anything you wire later). This folder is **separate** from the Java / Python / Rust apps—use it when you want a local or lab visualization layer (often paired with Prometheus or Loki elsewhere).

## Layout

| Path | Purpose |
|------|---------|
| `docker-compose.yml` | Runs Grafana on port **3000** with provisioning mounts |
| `provisioning/datasources/` | Data source definitions loaded at startup |
| `provisioning/dashboards/` | Dashboard sidecar config (loads JSON from `dashboards/`) |
| `dashboards/` | Export or hand-written dashboard JSON files |

## Run locally

From **`grafana/`**:

```bash
docker compose up -d
```

Open **http://localhost:3000/** — default credentials are **`admin` / `admin`** (you will be prompted to change the password on first login unless you override env vars). **Do not** use these defaults outside a trusted local machine; set `GF_SECURITY_ADMIN_PASSWORD` (and stronger auth) for shared environments.

To stop:

```bash
docker compose down
```

## Provisioning

- Edit **`provisioning/datasources/datasources.yml`** to add Prometheus, Loki, TestData, etc. ([Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)).
- Drop dashboard JSON under **`dashboards/`**; Grafana picks them up via **`provisioning/dashboards/dashboards.yml`**.

The shipped **`datasources.yml`** starts with an empty list so the stack boots without an unreachable Prometheus URL—add entries when your metrics backend exists.
