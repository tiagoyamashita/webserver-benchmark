# Kubernetes orchestration

This folder packages the **Java**, **Python**, **Rust**, and **PostgreSQL** workloads for deployment with **Helm**. You choose **how many replicas** of each app, **which container images** (registry + tag), and **where** they run using labels, node selectors, and per-environment value files (region / cloud / cluster).

**Compared to `docker compose up`:** Compose runs containers on **one Docker/Podman host** (ideal for local dev). **Helm** installs the same workloads as Kubernetes resources on a **cluster**—scheduling, Services, optional Ingress, and per-environment values. If you only need a laptop-sized stack, start with Compose at the repo root; use this folder when you target **Kubernetes** (cloud, datacenter, or local clusters like kind/minikube). A longer comparison lives in the root [README.md — Docker Compose vs Helm](../README.md#docker-compose-vs-helm-kubernetes).

## Prerequisites

- A Kubernetes cluster and `kubectl` configured.
- **[Helm](https://helm.sh/)** (3.14+ recommended; Helm 4.x works). If `helm` is not installed or not on your `PATH`:
  - **Windows:** `winget install --id Helm.Helm -e --accept-source-agreements --accept-package-agreements`
  - After installing, **close and reopen your terminal** (or restart the editor) so the updated `PATH` is picked up. If needed, open a new PowerShell and run `helm version` to confirm.
  - **Other OS:** follow [Installing Helm](https://helm.sh/docs/intro/install/).
- Images built and pushed to a registry your cluster can pull (see [../DOCKER.md](../DOCKER.md)).

## Layout

| Path | Purpose |
|------|---------|
| `helm/exercises-stack/` | Umbrella chart: Deployments, Services, PVC, optional Ingress |
| `helm/exercises-stack/values.yaml` | Defaults (replicas `1`, dev-oriented passwords) |
| `helm/elk-stack/` | **Optional** Elasticsearch + Logstash + Kibana (same roles as [`../elk/`](../elk/) Compose) |
| `helm/elk-stack/values.yaml` | ELK image tags, resources, persistence for Elasticsearch |
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

## Optional: Heimdall application dashboard

[Heimdall](https://github.com/linuxserver/Heimdall) is an application dashboard (organize links to your cluster UIs and other URLs). It is **not** part of `exercises-stack`; install it separately if you want a landing page for operators.

From **`kubernetes-orchestration/`**, using a community chart that wraps the `linuxserver/heimdall` image ([Artifact Hub listing](https://artifacthub.io/packages/helm/djjudas21/heimdall)):

```bash
helm repo add djjudas21 https://djjudas21.github.io/charts/
helm repo update
helm upgrade --install heimdall djjudas21/heimdall \
  --namespace heimdall \
  --create-namespace
```

Adjust exposure (ClusterIP vs LoadBalancer vs Ingress) with the chart’s values—see `helm show values djjudas21/heimdall`. Other charts are listed on [Artifact Hub](https://artifacthub.io/packages/search?ts_query=heimdall) if you prefer different defaults.

## Optional: ELK stack (Elasticsearch, Logstash, Kibana)

**On one machine (no cluster):** the **root** [`../docker-compose.yml`](../docker-compose.yml) starts ELK with the apps (`podman compose up --build`). Alternatively run ELK alone from [`../elk/`](../elk/) — `podman compose up -d` or `docker compose up -d`.

**On Kubernetes:** install the **`elk-stack`** Helm chart (same three components as the Compose stack). **Helm** applies the chart; **`kubectl`** is what you use afterward (`port-forward`, `get pods`, `logs`, delete namespace, etc.). The chart is **separate** from `exercises-stack`—use its own namespace (for example **`elk`**). Defaults match the local **`elk/docker-compose.yml`** idea: single-node Elasticsearch, **security disabled** (lab only), Logstash **Beats** input on **5044**, Kibana on **5601**.

From **`kubernetes-orchestration/`**:

```bash
helm upgrade --install logging ./helm/elk-stack \
  --namespace elk \
  --create-namespace \
  --set fullnameOverride=elk \
  -f helm/elk-stack/values.yaml
```

`fullnameOverride=elk` keeps service names short (`elk-elasticsearch`, `elk-logstash`, `elk-kibana`) instead of `logging-elk-stack-…`.

Check workloads with **`kubectl`**, then reach Kibana from your laptop (services are **ClusterIP** by default):

```bash
kubectl get pods -n elk
kubectl port-forward -n elk svc/elk-kibana 5601:5601
```

Then open **http://localhost:5601/**.

**Filebeat / Beats** in the cluster should send to **`elk-logstash.elk.svc.cluster.local:5044`** (namespace **`elk`**, adjust if yours differs).

**Production:** Prefer **Elastic Cloud**, **ECK** (Elastic Cloud on Kubernetes), or charts with TLS and auth. This chart is for **learning and parity** with the repo’s **`elk/`** folder.

```bash
helm uninstall logging --namespace elk
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
