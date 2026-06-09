# Prometheus (Compose)

This folder holds **`prometheus.yml`** used by the **root** `docker-compose.yml` **`prometheus`** service. Prometheus scrapes **Java** at **`java:8080/actuator/prometheus`**, **Python** at **`python:5000/metrics`**, and **Rust** at **`rust:8082/metrics`** on the shared **`exercises`** network.

- UI: **http://127.0.0.1:9090/** (after `podman compose up`)
- **Grafana** is provisioned with a **Prometheus** datasource pointing at **`http://prometheus:9090`**.

**ELK (Elasticsearch / Logstash / Kibana)** is for **logs**, not Prometheus metrics. Metrics: **Java / Python / Rust → Prometheus → Grafana**. To analyze logs in Kibana, ship logs with **Filebeat** (or similar) into Logstash — that path is separate from `/metrics`.
