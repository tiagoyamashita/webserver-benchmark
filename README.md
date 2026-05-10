# exercises
Exercises (tiagoyamashita.com)

Rust tooling bootstrap (rustup, cargo-nextest, `cargo build`) lives under **`rust/scripts/`**: see [rust/README.md](rust/README.md) (“One-shot setup / reinstall”).

**PostgreSQL** for local development runs under **Podman** from **`postgre/`**: see [postgre/README.md](postgre/README.md).

**Docker / Podman images** for Java, Python, and Rust plus Postgres: see [DOCKER.md](DOCKER.md) and `docker-compose.yml` at the repo root.

**Kubernetes (Helm)** — replicas, regions, and environments: [kubernetes-orchestration/README.md](kubernetes-orchestration/README.md).

On Windows, if **`link.exe` not found** and you do not want Visual Studio’s MSVC build tools, use the **GNU / MinGW** path: [rust/README.md — GNU target](rust/README.md#windows-gnu-target-mingw-instead-of-msvc) or run the project under **WSL**: [rust/README.md — WSL](rust/README.md#windows-wsl-linux-in-windows).
