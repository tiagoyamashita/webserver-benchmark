---
name: persist-postgres-item
description: >-
  Persist a row to the shared PostgreSQL `items` table in the exercises stack.
  Use when adding or fixing create-item flows, POST /api/items, dashboard Actions
  forms, or debugging items not saving to Postgres across Java, Python, Rust, or React Node.
---

# Persist item to Postgres (exercises stack)

## Shared table

- **Schema owner:** Java Flyway — `apps/java/src/main/resources/db/migration/V1__create_items.sql`
- **Table:** `items` (`id`, `name`, `created_at`)
- **Seed:** `V2__seed_items.sql` (Java must start once so migrations apply)

## Required env (all app containers)

```text
DB_HOST=postgres
DB_PORT=5432
DB_NAME=demo
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

Set in `docker-compose.apps.yml` for python, rust, java (postgres profile), react-node (proxy only).

## Create an item — by app

| App | How | Endpoint |
|-----|-----|----------|
| **Python** | Dashboard → **Actions → Create item**, or API | `POST /api/items` JSON `{"name":"..."}` |
| **Java** | Items API | `POST /api/items` |
| **Rust** | Dashboard → **Actions → Create item**, or API | `POST /api/items?name=...` |
| **React Node** | Dashboard → **Actions → Create item** (proxies Java) | `POST /api/items` |

## Python implementation (reference)

- **Routes:** `apps/python/src/exercises/web/items_api.py` — `register_items_routes`
- **DB:** `apps/python/src/exercises/web/db.py` — `connection(request_id=...)` uses `set_config('application_name', ...)` for log correlation
- **UI:** `apps/python/src/exercises/web/templates/landing.html` — sidebar **Create item** view calls `POST /api/items`

```python
# Minimal insert pattern (already in items_api.py)
with connection(request_id=_request_id()) as conn:
    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO items (name, created_at) VALUES (%s, NOW()) "
            "RETURNING id, name, created_at",
            (name,),
        )
        row = cur.fetchone()
    conn.commit()
```

## Verify persistence

```bash
curl -s -X POST http://127.0.0.1:5000/api/items \
  -H "Content-Type: application/json" \
  -d '{"name":"skill-test"}'

curl -s http://127.0.0.1:5000/api/items
```

Or open Python dashboard → **List items** → **Refresh list**.

## UI not updating (Python sidebar menu)

Dev bind mount replaces `/app` but the container must load **editable** source, not a stale `site-packages` install:

```bash
podman compose -f docker-compose.apps.yml -f docker-compose.dev.yml up -d --build python
```

Then hard-refresh http://127.0.0.1:5000/ (Ctrl+Shift+R). Expect left **Menu** with Connectivity / List items / Create item.

**Apps-only (no dev overlay):** rebuild the python image so templates are baked in:

```bash
podman compose -f docker-compose.apps.yml up -d --build python
```

## Common failures

| Symptom | Fix |
|---------|-----|
| `503` / Postgres not configured | Set `DB_*` env; ensure `postgres` service is up |
| Empty list, insert works | Java Flyway not run — start **java** once |
| Old landing page (no sidebar) | Dev: rebuild + editable entrypoint; prod: `--build python` |
| `relation "items" does not exist` | Start Java with postgres profile to apply Flyway |
