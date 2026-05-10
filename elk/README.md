# ELK stack (Elasticsearch, Logstash, Kibana)

Optional **Elastic Stack** layout for **local experiments**: ship logs through **Logstash** into **Elasticsearch**, explore them in **Kibana**. This is **not** wired to the Java / Python / Rust apps by default—add Filebeat or app logging pipelines when you need end-to-end ingestion.

## What “ELK” means here

| Letter | Component | Role |
|--------|-----------|------|
| **E** | **Elasticsearch** | Search and storage for log documents (`http://localhost:9200`). |
| **L** | **Logstash** | Ingest pipeline (here: **Beats** input on **5044**). |
| **K** | **Kibana** | Web UI for search and dashboards (`http://localhost:5601`). |

Modern deployments often add **Beats** (for example **Filebeat**) in front of Logstash; this folder ships the three core services only.

**Kibana is included:** the **`kibana`** service in **`docker-compose.yml`** runs the official **Kibana 8** image on **`http://localhost:5601`**. Elasticsearch connection and bind address are set in **`kibana/kibana.yml`** (mounted into the container). There is no separate “Kibana installer” step beyond bringing the stack up (see **Run locally** below).

## Layout

| Path | Purpose |
|------|---------|
| `docker-compose.yml` | Single-node Elasticsearch + Logstash + **Kibana** |
| `kibana/kibana.yml` | **Kibana** server + Elasticsearch host (explicit config file) |
| `logstash/pipeline/logstash.conf` | Pipeline: Beats → Elasticsearch |
| `filebeat/filebeat.yml.example` | Sample Filebeat config: **what** to watch + where to send |

## Run locally (Podman or Docker)

For a **single machine** (laptop or VM), use **Compose** with **Podman** or **Docker** — no Helm and no cluster required. Same `docker-compose.yml` file works with both runtimes (matches [../DOCKER.md](../DOCKER.md) for the rest of the repo).

From **`elk/`**:

```bash
podman compose up -d
```

If you use Docker Engine instead:

```bash
docker compose up -d
```

Then:

- **Elasticsearch:** `http://localhost:9200/` (JSON API; expect a small cluster-health payload).
- **Kibana:** `http://localhost:5601/` — security is **disabled** in this Compose file **for trusted local use only**.

### Using Kibana after logs arrive

1. Open **Discover** (left nav).
2. Create a **data view** (Stack Management → Data views, or the prompt in Discover) with index pattern **`logstash-*`** — that matches indices created by **`logstash/pipeline/logstash.conf`**.
3. Set **Timestamp field** to **`@timestamp`** when prompted.

Until Filebeat (or another Beat) sends events through Logstash, indices may not exist yet; Elasticsearch will create **`logstash-YYYY.MM.dd`** on first ingest.

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
