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

Wire producers/consumers in each app when you add messaging exercises.
