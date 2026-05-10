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
