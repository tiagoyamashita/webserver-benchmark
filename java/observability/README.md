# Java observability example (logs for ELK / Filebeat)

This app normally logs to the console only. With Spring profile **`observability`**, it also appends **JSON Lines** to a rolling file — one event per line — which **Filebeat** can ship to **`../../elk`** Logstash (**port 5044**) without parsing ad hoc plain text.

## What gets enabled

| Piece | Purpose |
|-------|---------|
| **`logback-spring.xml`** | When profile **`observability`** is active: **console** + **`${LOG_PATH:-logs}/demo-app.json.log`** using **LogstashEncoder** (Elastic-friendly JSON). |
| **`application-observability.yml`** | Log levels for that profile. |
| **`ObservabilitySampleController`** | `GET /api/observability/sample-log` emits one **INFO** line so you can confirm end-to-end delivery in Kibana. |

## Run locally (JVM on your machine)

From **`java/`**:

```bash
mkdir -p logs
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=observability
```

Trigger a log line:

```bash
curl -s http://localhost:8080/api/observability/sample-log
```

Open **`logs/demo-app.json.log`** — each line should be a JSON object.

## Run in Docker (Compose)

Use **both** **`postgres`** (if you use DB) and **`observability`**, and mount a volume so logs survive on the host for Filebeat:

```yaml
  java:
    environment:
      SPRING_PROFILES_ACTIVE: postgres,observability
      LOG_PATH: /app/logs
    volumes:
      - ./java/logs:/app/logs
```

The repo root **`Dockerfile`** creates **`/app/logs`** so the container can write before the volume mount replaces it.

## Filebeat

**Filebeat is not inside this Java process** — it is a separate agent that tails files or container logs and forwards to Logstash.

Copy **`filebeat-java.yml.example`** → **`filebeat.yml`**, set **`paths`** to your **`demo-app.json.log`** location (host path when Filebeat runs on the host), and point **`output.logstash`** at **`localhost:5044`** while the **`elk`** stack is running.

See also the top-level **`elk/`** folder: [../../elk/README.md](../../elk/README.md).
