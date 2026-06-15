# Prometheus (Compose)

This folder holds **`prometheus.yml`** used by the **root** `docker-compose.yml` **`prometheus`** service. Prometheus scrapes app metrics on the shared **`exercises`** network and **Podman container** stats via **podman-exporter** (optional).

| Job | Target | Metrics |
|-----|--------|---------|
| `exercises-java` | `java:8080/actuator/prometheus` | JVM, HTTP, … |
| `exercises-python` | `python:5000/metrics` | `exercises_http_requests_total`, … |
| `exercises-rust` | `rust:8082/metrics` | same |
| `exercises-react-node` | `react-node:5174/metrics` | same |
| `exercises-kafka` | `kafka-exporter:9308/metrics` | broker / topic metrics |
| `exercises-podman` | `podman-exporter:9882/metrics` | **`podman_container_mem_usage_bytes`**, CPU, … |
| `exercises-gpu` | `exercises-gpu-exporter:9066/metrics` | **`exercises_container_gpu_memory_bytes`**, GPU util per container (optional NVIDIA) |

- UI: **http://127.0.0.1:9090/** (after `podman compose up`)
- **Grafana** is provisioned with a **Prometheus** datasource pointing at **`http://prometheus:9090`**.
- Container memory dashboard: **Dashboards → Exercises → Exercises — Container memory (Podman)** (`exercises-containers.json`).

**ELK (Elasticsearch / Logstash / Kibana)** is for **logs**, not Prometheus metrics.

## Podman exporter (per-container RAM)

**`podman-exporter` is optional.** It uses Compose profile **`podman-metrics`** so a normal `podman compose up` does **not** fail when the Podman socket cannot be bind-mounted (common on **Windows Podman Machine**).

### Windows / Podman Machine (recommended)

The error `mkdir /run/podman/podman.sock: permission denied` means Compose tried to create a socket path on the **Windows host** — the real socket is only inside the **Podman Machine VM**.

From repo root (stack already up):

```powershell
.\devops\prometheus\start-podman-exporter.ps1
curl -X POST http://127.0.0.1:9090/-/reload
```

That SSHs into the VM and runs **`start-podman-exporter.sh`**, attaching the exporter to network **`exercises`** as container **`podman-exporter`**.

### Linux (native Podman, socket on host)

```bash
podman compose -f docker-compose.observability.yml --profile podman-metrics up -d podman-exporter
curl -X POST http://127.0.0.1:9090/-/reload
```

Or run the shell script directly on the host:

```bash
sh devops/prometheus/start-podman-exporter.sh
curl -X POST http://127.0.0.1:9090/-/reload
```

### Quick checks

```bash
curl -s http://127.0.0.1:9882/metrics | grep podman_container_mem_usage_bytes | head
curl -s 'http://127.0.0.1:9090/api/v1/query?query=up{job="exercises-podman"}'
```

### Rootless Podman (Linux)

Use **`docker-compose.podman-exporter-rootless.yml`** with the profile:

```bash
podman compose -f docker-compose.observability.yml -f docker-compose.podman-exporter-rootless.yml --profile podman-metrics up -d podman-exporter
```

### Grafana shows **No data**

1. **Exporter not running** — it is **not** started by `podman compose up` on Windows. Run:
   ```powershell
   .\devops\prometheus\start-podman-exporter.ps1
   ```
   Do **not** use `-UseSudo` (root podman cannot see the `exercises` network on Podman Machine).
2. **Prometheus not scraping** — script reloads Prometheus; or `curl.exe -X POST http://127.0.0.1:9090/-/reload`.
3. **Check targets** — http://127.0.0.1:9090/targets → `exercises-podman` should be **UP**.
4. **Grafana stale JSON** — `podman compose -f docker-compose.observability.yml restart grafana` then Ctrl+Shift+R.
5. **After Podman Machine restart** — re-run the start script (exporter container is not in compose).

### Troubleshooting

| Symptom | Fix |
|---------|-----|
| `permission denied` on `/run/podman/podman.sock` during **`compose up`** | Expected on Windows — exporter is **not** in default compose; use **`start-podman-exporter.ps1`**. |
| `up{job="exercises-podman"} == 0` | Exporter not running; start with script or `--profile podman-metrics`. |
| Scrape up but no `podman_container_*` | Socket wrong inside VM; `podman machine ssh` → `ls -l /run/podman/podman.sock`. |
| Empty panels, scrape up | Filter is `name=~"exercises-.*"` — Compose project must be **`exercises`**. |
| Docker Engine instead of Podman | Use **`podman stats`** or cAdvisor. |

Compare with CLI: `podman stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}"`.

## GPU exporter (per-container VRAM — optional)

**podman-exporter does not expose GPU metrics.** For **GPU memory and utilization per exercises container**, use **`exercises-gpu-exporter`** (maps `nvidia-smi` PIDs → Podman container names).

Requirements:

- NVIDIA driver + **`nvidia-smi`** on the **Podman host VM**
- Containers using the GPU must run with **`--device nvidia.com/gpu=all`** (or CDI) so processes appear in `nvidia-smi`

Start (after stack is up):

```powershell
.\devops\prometheus\start-gpu-exporter.ps1
```

Linux / inside Podman Machine:

```bash
sh devops/prometheus/start-gpu-exporter.sh
curl -X POST http://127.0.0.1:9090/-/reload
```

Grafana: **Exercises — Container resources (Podman)** → **GPU by pod / container** section.

Quick check:

```bash
curl -s http://127.0.0.1:9066/metrics | grep exercises_container_gpu
curl -s 'http://127.0.0.1:9090/api/v1/query?query=up{job="exercises-gpu"}'
```

**Windows Podman Desktop without GPU passthrough:** job stays down; RAM/CPU panels still work. GPU is opt-in for hosts with NVIDIA hardware.
