# Agent notes

## Skills (project)

| Skill | When to use |
|-------|-------------|
| `.cursor/skills/persist-postgres-item/` | Create/list items in shared Postgres `items` table; Python/Java/Rust/React Node `POST /api/items` |
| `.cursor/skills/controller-logging/` | Controller request/success/error logging; INFO/WARN/ERROR/TRACE; ELK replication params |

## Podman / Compose

The user runs **Podman Compose in a separate terminal**. Assume containers may already be up.

- **Dev overlay:** use `-f docker-compose.dev.yml` with apps compose so Java keeps a **`maven-cache`** volume (no full Maven download every rebuild), sources are mounted at `/workspace`, and static HTML changes apply on container start:
  `podman compose -f docker-compose.apps.yml -f docker-compose.dev.yml up -d --build`
- You may **inspect** the stack (`podman compose ps`, `logs`, `exec`, health checks, curl against published ports).
- Do **not** start, stop, restart, rebuild, or tear down Compose services (`up`, `down`, `stop`, `restart`, `rm`, etc.) unless the user asks first.
- If a task needs the stack running or rebuilt, ask the user to run the command in their terminal (or confirm before you run it).
