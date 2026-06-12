# Redis (Podman / Compose)

Single-node **Redis 7** for caching and session exercises. Defaults match **`docker-compose.apps.yml`** on the **`exercises`** network.

## Compose (recommended)

From the **repo root**:

```bash
podman compose -f docker-compose.apps.yml up -d redis redisinsight
```

| Context | Connection |
|---------|------------|
| Java / Python / Rust / react-node in Compose | `redis:6379` |
| URL env (`REDIS_URL`) | `redis://redis:6379` |
| Host CLI | `127.0.0.1:6379` |
| **RedisInsight** (Compose) | `http://127.0.0.1:5540/` — pre-wired to `redis:6379` as **exercises** |
| **RedisInsight (embed)** | `http://127.0.0.1:5541/` — nginx proxy for iframe embed in dashboards |

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

## RedisInsight

Official GUI ([`redis/redisinsight`](https://hub.docker.com/r/redis/redisinsight)) on host port **5540**. The Compose service sets `RI_REDIS_HOST=redis` and `RI_REDIS_ALIAS=exercises` so the **exercises** database appears on first open.

```bash
podman compose -f docker-compose.apps.yml up -d redisinsight
```

Open **http://127.0.0.1:5540/** and browse keys (e.g. `exercises:session:*` after Java login).

### Iframe embed (dashboards)

**RedisInsight** can block iframes via `X-Frame-Options` / CSP. The **`redisinsight-embed`** service (nginx on **5541**) proxies **`redisinsight:5540`**, strips frame-blocking headers, and sets `Content-Security-Policy: frame-ancestors *`.

| URL | Use |
|-----|-----|
| `http://127.0.0.1:5540/` | Standalone RedisInsight |
| `http://127.0.0.1:5541/` | Embed in Java / Rust dashboard iframes |

Config: `apps/redis/embed-proxy/nginx.conf`.

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
