# PostgreSQL (Podman)

Run a local **PostgreSQL** server with **Podman**. Defaults match the Java Spring Boot **`postgres`** profile in `java/src/main/resources/application.yml` (`DB_*` env vars).

## Prerequisites

- [Podman](https://podman.io/) installed and `podman machine start` running (macOS/Windows).

## Quick start

From this directory (`postgre/`):

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

## Connection (Java)

With the container running:

```bash
cd ../java
export SPRING_PROFILES_ACTIVE=postgres
# optional overrides — defaults match run.sh
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=demo
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
./mvnw spring-boot:run
```

On Windows PowerShell, use `$env:SPRING_PROFILES_ACTIVE="postgres"` etc.

## Image & data

- Image: `docker.io/library/postgres:16` (override with env `POSTGRES_IMAGE`).
- Named volume: `exercises-postgre-data` (persists until `podman volume rm …`).

The root **`docker-compose.yml`** starts the same Postgres major version alongside the Java, Python, and Rust containers — see [../DOCKER.md](../DOCKER.md).

## Observability (ELK / Filebeat)

When Postgres runs via the **root** `docker-compose.yml`, it writes **JSON Lines** to **`postgres/logs/`** on the host (`log_destination=jsonlog`). **Filebeat** tails those files and ships them through **Logstash** to **Elasticsearch**, same pipeline as the Java / Python / Rust app logs.

| Setting | Value |
|---------|--------|
| Host log dir | `postgres/logs/` (mounted at `/var/log/postgresql` in the container) |
| File pattern | `postgresql-*` (under host `apps/postgres/logs/`) |
| Kibana filter | `service: "exercises-postgres"` |

Logged by default: connections, disconnections, and data-modifying statements (`log_statement=mod`). `log_duration=off` avoids separate `duration: … ms` lines for idle waits and extended-query protocol steps (PARSE/BIND) where no SQL statement is logged.

### Shared `items` table

Schema and seed data are owned by **Java Flyway** migrations under **`java/src/main/resources/db/migration/`** (`V1__create_items.sql`, `V2__seed_items.sql`). **Java**, **Python**, and **Rust** expose **`/api/items`** against the same Postgres table once Java has applied migrations.

1. Start the stack with ELK + Filebeat (see [../elk/README.md](../elk/README.md)).
2. Generate DB activity (e.g. use Java or Rust `POST /api/items`, or connect with `psql`).
3. Confirm files appear under **`postgres/logs/`**.
4. In **Kibana → Discover** (data view **`logstash-*`**), filter with **`service: "exercises-postgres"`**.

The standalone **`scripts/run.ps1`** / **`run.sh`** Podman scripts do **not** enable JSON logging — use root Compose for the ELK path.
