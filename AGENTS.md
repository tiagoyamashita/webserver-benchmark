# Agent notes

## Skills (project)

| Skill | When to use |
|-------|-------------|
| `.cursor/skills/persist-postgres-item/` | Create/list items in shared Postgres `items` table; Python/Java/Rust/React Node `POST /api/items` |

## Podman / Compose

The user runs **Podman Compose in a separate terminal**. Assume containers may already be up.

- You may **inspect** the stack (`podman compose ps`, `logs`, `exec`, health checks, curl against published ports).
- Do **not** start, stop, restart, rebuild, or tear down Compose services (`up`, `down`, `stop`, `restart`, `rm`, etc.) unless the user asks first.
- If a task needs the stack running or rebuilt, ask the user to run the command in their terminal (or confirm before you run it).
