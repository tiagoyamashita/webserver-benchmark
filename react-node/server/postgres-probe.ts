import type { ProbeResult } from "./probe.js";

function readEnv(key: string, fallback: string): string {
  const value = process.env[key]?.trim();
  return value || fallback;
}

export function postgresProbeConfig() {
  return {
    host: readEnv("PROBE_POSTGRES_HOST", readEnv("DB_HOST", "127.0.0.1")),
    port: Number(readEnv("PROBE_POSTGRES_PORT", readEnv("DB_PORT", "5432"))),
    database: readEnv("PROBE_POSTGRES_DB", readEnv("DB_NAME", "demo")),
    user: readEnv("PROBE_POSTGRES_USER", readEnv("DB_USERNAME", "postgres")),
    password: readEnv("PROBE_POSTGRES_PASSWORD", readEnv("DB_PASSWORD", "postgres")),
  };
}

export async function probePostgres(): Promise<ProbeResult> {
  const start = performance.now();
  let pg: typeof import("pg");
  try {
    pg = await import("pg");
  } catch {
    const ms = Math.round(performance.now() - start);
    return {
      ok: false,
      status: null,
      error: "pg is not installed; rebuild react-node (podman compose build react-node)",
      ms,
      kind: "postgres",
    };
  }

  const config = postgresProbeConfig();
  const client = new pg.default.Client({
    ...config,
    connectionTimeoutMillis: 15_000,
  });

  try {
    await client.connect();
    await client.query("SELECT 1");
    const ms = Math.round(performance.now() - start);
    return { ok: true, status: null, error: null, ms, kind: "postgres" };
  } catch (error) {
    const ms = Math.round(performance.now() - start);
    return {
      ok: false,
      status: null,
      error: error instanceof Error ? error.message : String(error),
      ms,
      kind: "postgres",
    };
  } finally {
    await client.end().catch(() => undefined);
  }
}
