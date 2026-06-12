import type { ProbeResult } from "./types";

export function formatProbeResult(result: ProbeResult): string {
  if (result.kind === "postgres" && result.ok) {
    return `Postgres OK · ${result.ms} ms`;
  }
  if (result.kind === "redis" && result.ok) {
    return `Redis OK · ${result.ms} ms`;
  }
  if (result.ok && result.status != null) {
    return `HTTP ${result.status} · ${result.ms} ms`;
  }
  if (result.status != null) {
    return `HTTP ${result.status} · ${result.ms} ms`;
  }
  return `${result.error ?? "Unreachable"} · ${result.ms} ms`;
}

export function probeResultClassName(result: ProbeResult): string {
  if (result.ok) return "status-ok";
  if (result.status != null) return "status-warn";
  return "status-err";
}
