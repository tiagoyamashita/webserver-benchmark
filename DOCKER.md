# Docker / Podman

Build contexts live next to each stack:

| Directory | Image | Default port |
|-----------|--------|----------------|
| `java/` | Spring Boot | **8080** |
| `python/` | Flask dashboard | **5000** |
| `rust/` | Axum dashboard | **8082** |
| `ai-model/` | Local GGUF AI model (optional `--profile ai-model`) | **8095** |

From the **repository root** (use **Podman** if `docker` is not installed):

```bash
podman compose up -d --build
```

Compose is split so you can **restart apps without stopping observability**:

| File | Services |
|------|----------|
| **`docker-compose.apps.yml`** | postgres, redis, redisinsight, redisinsight-embed, kafka, kafka-ui, kafka-ui-embed, java, python, rust, react-node, optional **ai-model** (`--profile ai-model`) |
| **`docker-compose.observability.yml`** | prometheus, grafana, elasticsearch, logstash, kibana, filebeat |
| **`docker-compose.yml`** | includes both (full stack) |

```bash
# Observability once (Grafana, Prometheus, ELK)
podman compose -f docker-compose.observability.yml up -d

# (Re)start / rebuild apps only
podman compose -f docker-compose.apps.yml up -d --build

# Restart one app
podman compose -f docker-compose.apps.yml restart java rust
```

All files share the **`exercises`** network (same Compose project name from the repo directory).

**Hot reload (dev):** overlay **`docker-compose.dev.yml`** on the **apps** file so Java runs **`spring-boot:run`**, Python uses **`FLASK_DEBUG=1`**, Rust uses **`cargo-watch`**, **react-node** runs Express + Vite on **`http://127.0.0.1:5174/`**, and optional **ai-model** runs uvicorn with **`--reload`** (`--profile ai-model`):

```bash
podman compose -f docker-compose.apps.yml -f docker-compose.dev.yml up -d --build
```

Production **`rust/Dockerfile`** uses **`rust:1.86-bookworm`** (lockfile ICU crates need **rustc ≥ 1.86**; **`rust:1.85-*`** fails at build time).

With Docker Engine:

```bash
docker compose up --build
```

Works with **Podman** using `podman compose` (Compose v2) or legacy `podman-compose`.

### Linux / WSL: `unrecognized command podman compose`

Many distro packages ship **Podman without** the built-in **`podman compose`** subcommand. You will see:

`Error: unrecognized command podman compose`

Use one of these (same repo root, same `docker-compose.yml`):

1. **Standalone `podman-compose`** (hyphen — different program):

   ```bash
   pip3 install --user podman-compose   # or: sudo apt install podman-compose (if available)
   ~/.local/bin/podman-compose up -d --build
   ```

   Replace `podman compose …` everywhere in this repo’s docs with **`podman-compose …`** (hyphen, no space).

2. **Docker Compose v2** (works with Docker Engine; on some setups Podman 4+ can delegate to the same plugin if installed):

   ```bash
   sudo apt install docker-compose-plugin docker-ce-cli  # names vary by distro
   docker compose up -d --build
   ```

3. **Upgrade Podman** to a release that documents **`podman compose`** (often 4.1+ plus the compose plugin package on Fedora/RHEL).

The **`unknown shorthand flag: 'd' in -d`** message usually means **`compose` was not accepted as a subcommand**, so **`podman`** parsed the rest incorrectly. Fix the compose integration first, then **`up -d`** will work.

### Compose layout

- **`postgres`** — database on host port **5432** (named volume `exercises_pg_data`). JSON server logs land in **`postgres/logs/`** for **Filebeat → ELK** (see [postgres/README.md](postgres/README.md)).
- **`redis`** — cache on host port **6379** (named volume `exercises_redis_data`, AOF on). Apps receive **`REDIS_URL=redis://redis:6379`** (see [apps/redis/README.md](apps/redis/README.md)).
- **`redisinsight`** — [RedisInsight](https://redis.io/docs/latest/operate/redisinsight/) on **5540** → container **5540**; pre-configured connection **exercises** at `redis:6379` (`RI_REDIS_HOST` / `RI_REDIS_ALIAS`).
- **`redisinsight-embed`** — nginx on **5541** proxies **`redisinsight`** and strips **`X-Frame-Options`** so dashboards can iframe RedisInsight (`apps/redis/embed-proxy/nginx.conf`).
- **`kafka`** — single-node broker on host port **9092** (named volume `exercises_kafka_data`, KRaft). JSON broker logs land in **`apps/kafka/logs/`** for **Filebeat → ELK**; **`kafka-exporter`** exposes metrics for **Prometheus → Grafana** (see [apps/kafka/README.md](apps/kafka/README.md)).
- **`kafka-exporter`** — Prometheus metrics for broker / topics / consumer groups (scraped as job **`exercises-kafka`**).
- **`kafka-ui`** — [UI for Apache Kafka](https://github.com/provectus/kafka-ui) on **8090** → container **8080**; cluster **`exercises`** at `kafka:9092`.
- **`kafka-ui-embed`** — nginx on **8091** proxies **`kafka-ui`** and strips **`X-Frame-Options`** so dashboards can iframe Kafka UI (`apps/kafka/embed-proxy/nginx.conf`).
- **`java`** — uses Spring **`postgres`** profile; connects to the `postgres` service (`DB_HOST=postgres`). The **Dockerfile** keeps `pom.xml`, `src`, `mvnw`, and `target/` (including **Surefire reports** from the image build when tests run at build time) so the **test dashboard** works inside Compose, not only when running `./mvnw` on the host. **Build time:** the default image build runs **`mvn package`** with **all tests**, which is slow but populates Surefire XML; dependencies are cached in a separate layer when only `src/` changes. For a **faster** image (no tests at build time): `docker compose build --build-arg SKIP_TESTS=true java` (then run tests from the UI or `mvnw` inside the container to refresh reports).
- **`python`** / **`rust`** — listen on `0.0.0.0` inside the container (required for published ports). Both expose **`/metrics`** in Prometheus format for **`prometheus`** to scrape.
- **`java`** — exposes **`/actuator/prometheus`** (Spring Boot Actuator + Micrometer) for **`prometheus`**.
- **`prometheus`** — metrics TSDB and UI on **9090**; config in **`prometheus/prometheus.yml`** (scrapes **Java**, **Python**, **Rust**, **react-node**, **kafka-exporter**, **podman-exporter**, Filebeat, Logstash). **Grafana** uses this datasource (provisioned).
- **`podman-exporter`** — optional (**Compose profile `podman-metrics`**); [prometheus-podman-exporter](https://github.com/containers/prometheus-podman-exporter) for Grafana **`exercises-containers.json`**. **Not started by default** — on Windows use **`devops/prometheus/start-podman-exporter.ps1`** (socket bind mount fails on the host). See **`devops/prometheus/README.md`**.
- **`grafana`** — dashboards on **3000**; provisioning under **`grafana/`**. Starter dashboards include **`exercises-java-python-rust.json`**, **`exercises-kafka.json`** (broker metrics + ELK logs), **`exercises-containers.json`** (Podman memory), **`exercises-log-pipeline.json`**, etc. (folder **Exercises**). **`GF_SECURITY_ALLOW_EMBEDDING=true`** so it can load inside an **`<iframe>`** (dev-oriented; tighten for production).
- **`elasticsearch`** / **`logstash`** / **`kibana`** — ELK (lab defaults; security off). In **`docker-compose.observability.yml`**; config under **`elk/`**. **Heavy on RAM** — run apps without observability: `podman compose -f docker-compose.apps.yml up -d --build`.

**Metrics vs ELK:** **Prometheus + Grafana** handle **metrics** (e.g. Python/Rust **`/metrics`**, Java **`/actuator/prometheus`**). **ELK** handles **logs** (Filebeat → Logstash → Elasticsearch → Kibana). Prometheus does not replace ELK; wire logs separately if you want both.

Use **`elk/docker-compose.yml`** or **`grafana/docker-compose.yml`** only if you want those stacks **without** the app services — do **not** run them at the same time as the root file when the same ports are published (duplicate **3000** / ELK ports).

### Inter-container network (`exercises`)

All root-compose services attach to a **named bridge network** `exercises`. From **inside** any of those containers, other services resolve by **Compose service name** and **internal port** (not `127.0.0.1`):

- Postgres: `postgres:5432`
- Redis: `redis:6379`
- RedisInsight: `http://redisinsight:5540` (browser on host: `http://127.0.0.1:5540`, iframe embed: `http://127.0.0.1:5541`)
- Kafka: `kafka:9092`
- Kafka UI: `http://kafka-ui:8080` (browser on host: `http://127.0.0.1:8090`, iframe embed: `http://127.0.0.1:8091`)
- Java: `http://java:8080`
- Python: `http://python:5000`
- Rust: `http://rust:8082`
- AI model: `http://ai-model:8095` (when `--profile ai-model` is enabled)
- Grafana: `http://grafana:3000`
- Prometheus: `http://prometheus:9090`
- Elasticsearch: `http://elasticsearch:9200`, Kibana: `http://kibana:5601`, Logstash: `logstash:5044`

Example: `podman compose exec grafana wget -qO- http://java:8080/ | head` (or `curl` if present).

**Browser vs container:** Your **browser on the host** uses **published** ports on `127.0.0.1` (see URLs below). If something “can’t reach” another service from **JavaScript in the browser** (e.g. Grafana front end calling `http://java:8080`), that is **not** fixed by Compose networking — the browser is not on `exercises`; you need **CORS** on the target app or a **server-side** proxy.

URLs (use **`127.0.0.1`** in the browser on Windows if **`localhost`** hangs or refuses — see troubleshooting below):

- Java: `http://127.0.0.1:8080/`
- Python: `http://127.0.0.1:5000/`
- Rust: `http://127.0.0.1:8082/`
- Grafana: `http://127.0.0.1:3000/` (default login `admin` / `admin`; `GF_SERVER_ROOT_URL` matches this — use the same host you type in the address bar)
- Prometheus UI: `http://127.0.0.1:9090/`
- Elasticsearch: `http://localhost:9200/`, Kibana: `http://localhost:5601/` (`server.publicBaseUrl` in `elk/kibana/kibana.yml` matches this), Logstash Beats **5044**

### Browser cannot reach containers (Podman on Windows)

Containers can be **Up** in `podman compose ps` while the browser still fails. Common causes:

1. **`localhost` uses IPv6 first** — On some Windows setups, `http://localhost:PORT` goes to **`::1`** while published ports are only on **IPv4**. Try **`http://127.0.0.1:PORT/`** for Java (**8080**), Python (**5000**), Rust (**8082**), Grafana (**3000**).
2. **Podman machine not running** — From PowerShell: `podman machine list` then `podman machine start` if the VM is stopped.
3. **Grafana still starting** — First boot can take a minute; wait and reload. Check logs: `podman compose logs grafana --tail 50`.
4. **Port clash** — Another program (or a second Compose project) using **3000** / **8080** / etc. shows as running but wrong app. Run `podman compose ps` and confirm **PORTS** include `0.0.0.0:3000->3000/tcp` (or similar).
5. **Firewall** — Allow **Podman** / **WSL** / **gvproxy** through Windows Defender Firewall if prompted.

Quick checks from PowerShell (after `podman compose up`):

```powershell
podman compose ps
Test-NetConnection 127.0.0.1 -Port 3000
Test-NetConnection 127.0.0.1 -Port 8080
```

Optional **React Node** stack probe UI (React + Express, `GET /api/probe/:id`): [../react-node/README.md](../react-node/README.md).

### Build one service

```bash
docker compose build java
docker compose up postgres java
```

### Java H2 only (no Postgres)

Run the image without Compose wiring — override profile and DB settings:

```bash
docker build -t exercises-java ./java
docker run --rm -p 8080:8080 exercises-java
```

(Default Spring profile uses in-memory H2 when `SPRING_PROFILES_ACTIVE` is unset.)

### Standalone Postgres (Podman scripts)

See **`postgre/README.md`** for `podman` scripts that match this Compose database defaults.

### Kubernetes image tags

Push images to your registry using the same repository names as in **`kubernetes-orchestration/helm/exercises-stack/values.yaml`** (defaults: `exercises-java`, `exercises-python`, `exercises-rust`), then set **`global.imageRegistry`** in Helm. See **`kubernetes-orchestration/README.md`**.
