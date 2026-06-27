# Kafka (Podman / Compose)

Single-node **Apache Kafka** (KRaft, no ZooKeeper) for app messaging exercises. Defaults match **`docker-compose.apps.yml`** on the **`exercises`** network.

## Compose (recommended)

From the **repo root**:

```bash
podman compose -f docker-compose.apps.yml up -d kafka kafka-ui
```

Bootstrap address **inside Compose**: `kafka:9092` (`KAFKA_BOOTSTRAP_SERVERS`).

## Web UI

**[UI for Apache Kafka](https://github.com/provectus/kafka-ui)** (`provectuslabs/kafka-ui`) runs as the **`kafka-ui`** Compose service.

| | |
|--|--|
| URL (host) | `http://127.0.0.1:8090/` |
| Cluster name | `exercises` |
| Bootstrap (from UI container) | `kafka:9092` |

Host port **8090** avoids clashing with Java on **8080**. Browse topics, messages, and consumer groups after creating topics.

### Embed in app dashboards (`<iframe>`)

Direct **8090** sends `X-Frame-Options: DENY`. Use the **nginx embed proxy** instead:

| | |
|--|--|
| Embed URL (host) | `http://127.0.0.1:8091/` |
| Compose service | `kafka-ui-embed` → proxies `kafka-ui:8080` |
| Config | `apps/kafka/embed-proxy/nginx.conf` |

Java and Rust dashboards load this URL in an iframe (**Menu → Kafka UI**). Standalone browsing can still use **8090**.

## Standalone Podman

From this directory (`apps/kafka/`):

**Linux / macOS**

```bash
./scripts/run.sh
```

**Windows (PowerShell)**

```powershell
.\scripts\run.ps1
```

Stop / remove:

```bash
./scripts/stop.sh
```

```powershell
.\scripts\stop.ps1
```

## Connection

| Context | Bootstrap servers |
|---------|-------------------|
| Java / Python / Rust / react-node in Compose | `kafka:9092` |
| CLI inside the `kafka` container | `localhost:9092` |

List topics (Compose):

```bash
podman compose -f docker-compose.apps.yml exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Create a topic:

```bash
podman compose -f docker-compose.apps.yml exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic demo-events --partitions 1 --replication-factor 1
```

### `create-user` (Java producer → Rust consumer)

| | |
|--|--|
| Topic | `create-user` |
| Producer | Java `CreateUserEventPublisher` (dashboard **Kafka create user** or `POST /dashboard/users/publish-create-user`) |
| Consumer group (Java + Rust, shared) | `exercises-create-user` |
| Effect | Rust inserts into Postgres `users` |

Event JSON (includes the dashboard/API `requestId` for log correlation):

```json
{"event":"create-user","name":"Jane Doe","email":"jane@example.com","requestId":"…"}
```

The same id is also sent as Kafka header `X-Request-ID`. Java and Rust consumers restore it into their request context so handler logs and Postgres `application_name` match the original HTTP request instead of generating a new id.

### `create-item` (Java producer → Python consumer)

| | |
|--|--|
| Topic | `create-item` |
| Producer | Java `CreateItemEventPublisher` (dashboard **Kafka create item (Python)** or `POST /dashboard/items/publish-create-item`) |
| Consumer group (Python only) | `exercises-create-item-python` |
| Effect | Python inserts into Postgres `items` |

Event JSON:

```json
{"event":"create-item","name":"Widget from Kafka","requestId":"…"}
```

The same id is also sent as Kafka header `X-Request-ID`. The Python consumer restores it for handler logs and Postgres `application_name`.

**Topic creation:** Java `KafkaTopicConfig` ensures `create-item` on startup (same fail-fast admin as `create-user`).

| App | Config |
|-----|--------|
| Java | `app.kafka.create-item-*` |
| Python | `KAFKA_CREATE_ITEM_TOPIC`, `KAFKA_CREATE_ITEM_CONSUMER_GROUP` |

**Topic creation (create-user):** Java and Rust both ensure `create-user` on startup when it is missing (fail-fast if Kafka is down or config mismatches). No manual `kafka-topics.sh` step is required when Kafka is healthy before the apps start.

| App | Config |
|-----|--------|
| Java | `KafkaTopicConfig`, `app.kafka.create-user-*`, `spring.kafka.admin.fail-fast: true` |
| Rust | `KAFKA_CREATE_USER_*`, `KAFKA_ADMIN_FAIL_FAST=true` (see `.env.apps.example`) |

Optional manual create (same settings as config):

```bash
podman compose -f docker-compose.apps.yml exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic create-user --partitions 1 --replication-factor 1
```

Produce / consume smoke test:

```bash
podman compose -f docker-compose.apps.yml exec kafka \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic demo-events

podman compose -f docker-compose.apps.yml exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic demo-events --from-beginning
```

## App env vars

Set in Compose or `.env.apps` (see `.env.apps.example` at repo root):

```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

**Wired today:** Java publishes `create-user` (Java/Rust consumers → Postgres `users`) and `create-item` (Python-only consumer → Postgres `items`). Rust can also publish `create-user`. Add more producers/consumers in each app as needed.

## Observability (Grafana + Elasticsearch)

| Path | Role |
|------|------|
| **`config/log4j2.yaml`** | Broker JSON logs → **`logs/kafka.json.log`** (Compose mount) |
| **`logs/`** | Tailed by **Filebeat** as **`service: exercises-kafka`** → Logstash → Elasticsearch |
| **`kafka-exporter`** (Compose) | Prometheus job **`exercises-kafka`** — broker / topic / consumer-group metrics |
| **Grafana** | Dashboard **WebServer BenchMark — Kafka** (`devops/grafana/dashboards/exercises-kafka.json`) |

After changing broker logging or adding the exporter, rebuild/restart **kafka**, **kafka-exporter**, and **filebeat** (or full stacks). Reload Prometheus if needed:

```bash
curl -X POST http://127.0.0.1:9090/-/reload
podman compose -f docker-compose.observability.yml restart filebeat grafana
```
