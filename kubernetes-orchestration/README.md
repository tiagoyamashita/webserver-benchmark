# Kubernetes orchestration

This folder packages the **Java**, **Python**, **Rust**, and **PostgreSQL** workloads for deployment with **Helm**. You choose **how many replicas** of each app, **which container images** (registry + tag), and **where** they run using labels, node selectors, and per-environment value files (region / cloud / cluster).

## Prerequisites

- A Kubernetes cluster and `kubectl` configured.
- [Helm](https://helm.sh/) 3.14+ recommended.
- Images built and pushed to a registry your cluster can pull (see [../DOCKER.md](../DOCKER.md)).

## Layout

| Path | Purpose |
|------|---------|
| `helm/exercises-stack/` | Umbrella chart: Deployments, Services, PVC, optional Ingress |
| `helm/exercises-stack/values.yaml` | Defaults (replicas `1`, dev-oriented passwords) |
| `values/` | **Examples** for different regions / environments — copy and adjust |

Treat `values/*.yaml` as **templates**: duplicate `values/us-east.example.yaml` to `values/prod-us-east.yaml` (gitignored locally if it holds secrets) and set real registry URLs and replica counts.

## Install (single environment)

From **`kubernetes-orchestration/`**:

```bash
helm upgrade --install exercises ./helm/exercises-stack \
  --namespace exercises \
  --create-namespace \
  -f helm/exercises-stack/values.yaml \
  -f values/my-env.yaml
```

Override replicas on the CLI:

```bash
helm upgrade --install exercises ./helm/exercises-stack \
  --namespace exercises \
  --create-namespace \
  --set java.replicaCount=3 \
  --set python.replicaCount=2 \
  --set rust.replicaCount=1 \
  --set global.region=us-east-1
```

## Where & how many instances

| Goal | Mechanism |
|------|-----------|
| **Replica count per app** | `java.replicaCount`, `python.replicaCount`, `rust.replicaCount`, `postgres.replicaCount` (Postgres is usually `1`; use an operator or managed DB for HA). |
| **Region / topology** | Set `global.region` (labels) and/or `*.nodeSelector`, `*.affinity` in values (e.g. `topology.kubernetes.io/region` on EKS/GKE/AKS). |
| **Separate clusters** | One values file per cluster (same chart, different `global.region`, image tags, and pull secrets). |
| **Platform** | Document in your values file (e.g. `global.platform: eks`) and use cloud-specific selectors if needed. |

## Secrets

Do **not** commit production DB passwords. Options:

- `--set postgres.auth.password=...` at install time (CI/CD secret).
- Sealed Secrets / External Secrets Operator.
- Replace the generated `Secret` with a reference `existingSecret` (extend chart if you adopt this pattern).

## Uninstall

```bash
helm uninstall exercises --namespace exercises
```
