# Docker / Podman

Build contexts live next to each stack:

| Directory | Image | Default port |
|-----------|--------|----------------|
| `java/` | Spring Boot | **8080** |
| `python/` | Flask dashboard | **5000** |
| `rust/` | Axum dashboard | **8082** |

From the **repository root** (use **Podman** if `docker` is not installed):

```bash
podman compose up --build
```

With Docker Engine:

```bash
docker compose up --build
```

Works with **Podman** using `podman compose` (Compose v2) or legacy `podman-compose`.

### Compose layout

- **`postgres`** — database on host port **5432** (named volume `exercises_pg_data`).
- **`java`** — uses Spring **`postgres`** profile; connects to the `postgres` service (`DB_HOST=postgres`).
- **`python`** / **`rust`** — listen on `0.0.0.0` inside the container (required for published ports).

URLs:

- Java: `http://localhost:8080/`
- Python: `http://localhost:5000/`
- Rust: `http://localhost:8082/`

### Build one service

```bash
docker compose build java
docker compose up postgres java
```

### Java H2 only (no Postgres)

Run the image without Compose wiring — override profile and DB settings:

```bash
docker build -t exercises-java ./java
docker run --rm -p 8080:8080 exercises-java
```

(Default Spring profile uses in-memory H2 when `SPRING_PROFILES_ACTIVE` is unset.)

### Standalone Postgres (Podman scripts)

See **`postgre/README.md`** for `podman` scripts that match this Compose database defaults.

### Kubernetes image tags

Push images to your registry using the same repository names as in **`kubernetes-orchestration/helm/exercises-stack/values.yaml`** (defaults: `exercises-java`, `exercises-python`, `exercises-rust`), then set **`global.imageRegistry`** in Helm. See **`kubernetes-orchestration/README.md`**.
