# ELK stack (Elasticsearch, Logstash, Kibana)

Optional **Elastic Stack** layout for **local experiments**: ship logs through **Logstash** into **Elasticsearch**, explore them in **Kibana**.

The **root** `docker-compose.yml` includes a **`filebeat`** service that tails **Java**, **Python**, **Rust**, and **Postgres** JSON logs. Apps write **`*/logs/demo-app.json.log`** (Java **`observability`** profile; Python / Rust **`WEBSERVER_BENCHMARK_OBSERVABILITY=1`**). Postgres writes **`postgres/logs/postgresql-*.json`** (`log_destination=jsonlog`; see [../postgres/README.md](../postgres/README.md)).

## What “ELK” means here

| Letter | Component | Role |
|--------|-----------|------|
| **E** | **Elasticsearch** | Search and storage for log documents (`http://localhost:9200`). |
| **L** | **Logstash** | Ingest pipeline (here: **Beats** input on **5044**). |
| **K** | **Kibana** | Web UI for search and dashboards (`http://localhost:5601`). |

Modern deployments often add **Beats** (for example **Filebeat**) in front of Logstash; this folder ships the three core services only.

**Kibana is included:** the **`kibana`** service in **`docker-compose.yml`** runs the official **Kibana 8** image on **`http://localhost:5601`**. Elasticsearch connection and bind address are set in **`kibana/kibana.yml`** (mounted into the container), including **`server.publicBaseUrl`** so redirects match opening Kibana via **localhost**. There is no separate “Kibana installer” step beyond bringing the stack up (see **Run locally** below).

## Layout

| Path | Purpose |
|------|---------|
| `docker-compose.yml` | Single-node Elasticsearch + Logstash + **Kibana** |
| `kibana/kibana.yml` | **Kibana** server + Elasticsearch host; **`server.defaultRoute`** opens the WebServer BenchMark dashboard |
| `kibana/provision-kibana.sh` | Imports data view, dashboards, and default route (run by **`kibana-setup`** on Compose up) |
| `kibana/build-dashboards.py` | Builds dashboards that link panels to library saved searches |
| `kibana/build-ndjson.py` | Bundles data view, searches, and dashboards for `_import` |
| `logstash/pipeline/logstash.conf` | Pipeline: Beats → Elasticsearch |
| `filebeat/filebeat-compose.yml` | Compose Filebeat: tails app JSON logs; **timestamp processors** set `@timestamp` from each app's log field (`timestamp` or `@timestamp`) instead of harvest time |
| `filebeat/filebeat.yml.example` | Sample Filebeat config: **what** to watch + where to send |

## Run locally (Podman or Docker)

For a **single machine**, use **Compose** with **Podman** or **Docker** — no cluster required.

The **root** `docker-compose.yml` starts **apps**, **Grafana**, and **ELK** together (`podman compose up --build`). Use **either** the root file **or** the per-folder compose files — not both at once when the same ports are published (ports **9200**, **5044**, **5601**, **3000** would collide).

For **ELK only**, from **`elk/`**:

```bash
podman compose up -d
```

If you use Docker Engine instead:

```bash
docker compose up -d
```

Then:

- **Elasticsearch:** `http://localhost:9200/` (JSON API; expect a small cluster-health payload).
- **Kibana:** `http://localhost:5601/` — security is **disabled** in this Compose file **for trusted local use only**. The **`kibana-setup`** service imports the **`logstash-*`** data view, saved searches, and dashboards, then sets the default landing page to **WebServer BenchMark — HTTP & Postgres logs** (`/app/dashboards#/view/webserver-benchmark-requests-logs-kibana`). Open `http://localhost:5601/` after `compose up` — no manual data-view wizard required.

### Using Kibana after logs arrive

1. Generate log lines (sample endpoints on each app):
   ```bash
   curl -s http://127.0.0.1:8080/api/observability/sample-log
   curl -s http://127.0.0.1:5000/api/observability/sample-log
   curl -s http://127.0.0.1:8082/api/observability/sample-log
   ```
2. Confirm indices exist: `curl -s http://127.0.0.1:9200/_cat/indices?v` — look for **`logstash-YYYY.MM.dd`**.
3. Open **Kibana** at `http://127.0.0.1:5601/` — you should land on **WebServer BenchMark — HTTP & Postgres logs** (last 6 hours). If the dashboard is empty, wait for **`kibana-setup`** to finish (`podman compose logs kibana-setup`) or run `.\devops\elk\kibana\provision-kibana.ps1` manually.
4. **Discover** uses the pre-provisioned **`logstash-*`** data view (**Logstash pipeline**); timestamp field **`@timestamp`**.

Until Filebeat (or another Beat) sends events through Logstash, indices may not exist yet; Elasticsearch will create **`logstash-YYYY.MM.dd`** on first ingest.

**Root Compose Filebeat:** config is **`elk/filebeat/filebeat-compose.yml`**. After `podman compose up`, check **`podman compose logs filebeat --tail 30`** if nothing appears in Kibana.

**Java logs missing in Kibana?** Dashboard logs must not use a top-level JSON field named **`event`** (string) — Logstash’s Beats input expects ECS **`event`** as an object and will reset the connection. This repo uses **`ui_event`** instead; Filebeat also renames legacy **`event` → `ui_event`** before shipping.

Logstash normalizes Rust tracing JSON (flattens nested `fields` to top-level keys) so Grafana/Kibana queries match Java and Python log shape. Controller HTTP snapshots (`headers`, `body`, `url_params`) are **JSON-stringified** before Elasticsearch so daily indices never reject object values against legacy `text` mappings. **Restart Logstash** after changing `logstash/pipeline/logstash.conf`, then **restart Filebeat** if the shipper was backlogged (`connection reset by peer` in `filebeat` logs).

**Logs stopped appearing in Kibana/Grafana?** Check `podman compose -f docker-compose.observability.yml logs logstash --tail 30` for `document_parsing_exception` on `body` or `headers`. After updating `logstash.conf`, restart Logstash and Filebeat. Expand the time picker (dashboard default is **Last 6 hours**).

**Filter by app in Discover:** `service: "webserver-benchmark-java"` · `service: "webserver-benchmark-python"` · `service: "webserver-benchmark-rust"` · `service: "webserver-benchmark-react-node"` · `service: "webserver-benchmark-postgres"` · `service: "webserver-benchmark-kafka"`. By file path: `log.file.path: *react-node*` · `log.file.path: *postgresql*` · `log.file.path: *kafka*`.

**Find one request in Kibana Discover** (normal search only matches `message` text — paste the UUID as a field query):

```text
request_id: "paste-your-uuid-here"
```

**URL query string** (not POST body) appears on HTTP **request received** lines (`phase: "received"`) in **`url_params`** — e.g. Java dashboard `POST /dashboard/items/publish-create-item?name=…` puts `name` in the query string. Logstash stores the map as a JSON string and flattens common keys to **`url_param_name`**, **`url_param_email`**, etc. Runtime fields **`url_params.name`** / **`url_params.email`** mirror those. **POST JSON/form payload** is in **`body`** (often `{}` when the app uses query params only).

For Postgres SQL lines use **`request_id`** (copied from stamped `application_name` by Logstash) or runtime field **`correlation.request_id`**: `request_id: "paste-your-uuid-here"`.

### Postgres logs missing in Kibana / Grafana?

Postgres logs often **are** in Elasticsearch while saved searches hide them. Verify layer by layer:

1. **Postgres file** (inside container): `podman compose -f docker-compose.apps.yml exec postgres tail -3 /var/log/postgresql/postgresql-$(date -u +%Y-%m-%d).json` — expect `"message":"execute … select …"`.
2. **Elasticsearch** (browser or curl): `service.keyword:webserver-benchmark-postgres AND message:*execute*` on index `logstash-*`, time **Last 15 minutes**.
3. **Kibana Discover** (simplest query): `service: webserver-benchmark-postgres and message: *execute* and not message: *SELECT 1*`
4. **Re-import** saved objects after query changes: `.\devops\elk\kibana\provision-kibana.ps1`

Common traps: time range too narrow; **`log_statement=mod`** (SELECT not logged — need **`all`** in Compose + postgres recreate); saved search requiring `application_name: *webserver-benchmark*` while viewing unstamped JDBC lines; panel that only matched `_exists_:detail` (INSERT params only, not SELECT).

### HTTP ↔ Postgres correlation (Kibana)

After logs are flowing, the stack provisions correlation objects automatically via **`kibana-setup`**. To re-run import manually:

```powershell
.\devops\elk\kibana\provision-kibana.ps1
```

Open **`http://127.0.0.1:5601/`** (default route) or **`http://127.0.0.1:5601/app/dashboards#/view/webserver-benchmark-requests-logs-kibana`**.

| Panel | What |
|-------|------|
| **HTTP — recent requests** | App access logs (health/metrics excluded) |
| **PostgreSQL — SQL CRUD by origin** | Stamped CRUD lines; `correlation.origin` / `correlation.request_id` parsed from `application_name` |
| **PostgreSQL — log stream** | Full Postgres JSON log tail |
| **HTTP — handler logs** | Controller received/succeeded/failed lines |

Click **`correlation.request_id`** in the SQL table (URL link on the data view) to jump to handler logs for that request.

### Kafka logs (Kibana)

Dashboard **`WebServer BenchMark — Kafka logs`**: [http://127.0.0.1:5601/app/dashboards#/view/webserver-benchmark-kafka-logs-kibana](http://127.0.0.1:5601/app/dashboards#/view/webserver-benchmark-kafka-logs-kibana)

Re-import after JSON changes:

```powershell
.\devops\elk\kibana\provision-kibana.ps1
```

| Panel | What |
|-------|------|
| **Kafka — broker logs** | `service: webserver-benchmark-kafka` — broker JSON from `apps/kafka/logs/` |
| **Kafka — app publish & consume** | Java `CreateItem*`, `CreateUser*`, Python `create_item_consumer`, Rust `create_user_consumer` |
| **Kafka — correlate by request_id** | Filter `request_id: "your-uuid"` to stack Java publish → consumer → Postgres for one dashboard action |

Saved searches live under **`devops/elk/kibana/saved_objects/kafka-logs/`** and ship in **`webserver-benchmark-kibana.ndjson`**.

### Log pipeline monitoring (Filebeat → Logstash)

| Layer | What | Where |
|-------|------|--------|
| **Filebeat** | HTTP stats on `:5066` (Compose internal) | `filebeat/filebeat-compose.yml` (`http.enabled`) |
| **Prometheus** | `beat-exporter` + `logstash-exporter` scrape jobs | `prometheus/prometheus.yml` |
| **Grafana** | “Log pipeline” row on apps dashboard + **`webserver-benchmark-log-pipeline.json`** | Alert rules in `grafana/provisioning/alerting/log-pipeline.yaml` |
| **Kibana** | Dashboard + saved searches | Auto on Compose up via **`kibana-setup`** (`provision-kibana.sh`); manual: `kibana/provision-kibana.ps1` → **WebServer BenchMark — HTTP & Postgres logs** (default) and **WebServer BenchMark — Log pipeline (Kibana)** |

When Grafana alerts fire, unshipped lines remain on disk under **`apps/*/logs/`** until the pipeline recovers. Use the alert time window to grep those files and compare with Kibana **`logstash-*`**.

To stop:

```bash
podman compose down
```

(or `docker compose down` if you used Docker.)

## Sending logs — do I pick apps in a UI?

**No automatic “watch these apps” toggle** in this setup. Elasticsearch only indexes what you send; **you choose sources in config** (usually a **YAML file** for Elastic **Beats**, or extra **Logstash** `input {}` blocks in `logstash.conf`).

**Typical pattern:** run **[Filebeat](https://www.elastic.co/beats/filebeat)** with a config file that lists **paths** (log files), **container** log paths, or other inputs, and set **output to Logstash** on port **5044**.

1. Copy **`filebeat/filebeat.yml.example`** → **`filebeat.yml`** (any name you like).
2. Edit **`filebeat.inputs`**: enable **`paths:`** for your log files, or use **container** / **Docker** inputs (see comments in the example—host paths differ on Linux vs Docker Desktop).
3. Set **`output.logstash.hosts`**: use **`localhost:5044`** if Filebeat runs on your machine; use **`logstash:5044`** if Filebeat runs **inside Podman/Docker** on the **same network** as the ELK Compose stack.
4. Install Filebeat from Elastic’s docs and run it with **`-c filebeat.yml`** (or install Filebeat as a service pointing at that file).

You can instead extend **`logstash/pipeline/logstash.conf`** with more **`input { ... }`** blocks (TCP, HTTP, syslog, etc.) so agents send **directly to Logstash** without Filebeat—still **file-based config**, not a wizard inside this repo.

## Kubernetes (Helm installs; kubectl operates)

On a **Kubernetes** cluster you install resources with **Helm** (or another templater), then use **`kubectl`** for everything else: **`kubectl port-forward`** to reach Kibana, **`kubectl get pods -n elk`**, **`kubectl logs`**, uninstall, and so on.

To deploy the same three roles on a cluster, use the **`elk-stack`** chart under **`kubernetes-orchestration/helm/elk-stack/`** (Helm install plus **`kubectl`** examples): [../kubernetes-orchestration/README.md#optional-elk-stack-elasticsearch-logstash-kibana](../kubernetes-orchestration/README.md#optional-elk-stack-elasticsearch-logstash-kibana).

## Notes and caveats

- **Memory:** Elasticsearch and Logstash need RAM; if containers exit with OOM, raise Docker Desktop memory or lower `ES_JAVA_OPTS` / `LS_JAVA_OPTS` in `docker-compose.yml`.
- **Linux hosts:** Elasticsearch may require `vm.max_map_count` ≥ **262144** ([Elastic docs](https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html#_set_vm_max_map_count_to_at_least_262144)).
- **Security:** `xpack.security.enabled=false` keeps startup simple and is **unsuitable** for production or shared networks. Do not expose these ports publicly without TLS, auth, and network controls.
