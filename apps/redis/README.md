# Redis (Podman / Compose)

Single-node **Redis 7** for caching and session exercises. Defaults match **`docker-compose.apps.yml`** on the **`exercises`** network.

## Compose (recommended)

From the **repo root**:

```bash
podman compose -f docker-compose.apps.yml up -d redis
```

| Context | Connection |
|---------|------------|
| Java / Python / Rust / react-node in Compose | `redis:6379` |
| URL env (`REDIS_URL`) | `redis://redis:6379` |
| Host CLI | `127.0.0.1:6379` |

Persistence: **AOF** enabled (`appendonly yes`) on volume `exercises_redis_data`.

## Standalone Podman

From this directory (`apps/redis/`):

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

## Smoke test

Compose:

```bash
podman compose -f docker-compose.apps.yml exec redis redis-cli ping
podman compose -f docker-compose.apps.yml exec redis redis-cli SET exercises:hello world
podman compose -f docker-compose.apps.yml exec redis redis-cli GET exercises:hello
```

## App env vars

Set in Compose or `.env.apps` (see `.env.apps.example` at repo root):

```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_URL=redis://redis:6379
```

Wire clients in each app when you add caching exercises.
