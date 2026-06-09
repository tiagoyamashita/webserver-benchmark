export type ProbeTargetId =
  | "java"
  | "rust"
  | "python"
  | "prometheus"
  | "grafana"
  | "elasticsearch"
  | "kibana";

export const PROBE_SERVICES: ReadonlyArray<{ id: ProbeTargetId; label: string }> = [
  { id: "java", label: "Java" },
  { id: "rust", label: "Rust" },
  { id: "python", label: "Python" },
  { id: "prometheus", label: "Prometheus" },
  { id: "grafana", label: "Grafana" },
  { id: "elasticsearch", label: "Elasticsearch" },
  { id: "kibana", label: "Kibana" },
];

function readEnv(key: string, fallback: string): string {
  const value = process.env[key]?.trim();
  return value || fallback;
}

export function probeTargetUrl(id: ProbeTargetId): string {
  const map: Record<ProbeTargetId, string> = {
    java: readEnv("PROBE_JAVA_URL", "http://127.0.0.1:8080"),
    rust: readEnv("PROBE_RUST_URL", "http://127.0.0.1:8082"),
    python: readEnv("PROBE_PYTHON_URL", "http://127.0.0.1:5000"),
    prometheus: readEnv("PROBE_PROMETHEUS_URL", "http://127.0.0.1:9090"),
    grafana: readEnv("PROBE_GRAFANA_URL", "http://127.0.0.1:3000"),
    elasticsearch: readEnv("PROBE_ELASTICSEARCH_URL", "http://127.0.0.1:9200"),
    kibana: readEnv("PROBE_KIBANA_URL", "http://127.0.0.1:5601"),
  };
  return map[id];
}

export function isProbeTargetId(value: string): value is ProbeTargetId {
  return PROBE_SERVICES.some((service) => service.id === value);
}
