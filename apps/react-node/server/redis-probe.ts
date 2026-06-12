import type { ProbeResult } from "./probe.js";
import { readEnv } from "./auth/session.js";

export async function probeRedis(): Promise<ProbeResult> {
  const start = performance.now();
  const url =
    process.env.REDIS_URL?.trim() ||
    (process.env.REDIS_HOST?.trim()
      ? `redis://${process.env.REDIS_HOST.trim()}:${readEnv("REDIS_PORT", "6379")}`
      : "");
  if (!url) {
    return {
      ok: false,
      status: null,
      error: "Redis not configured (set REDIS_HOST or REDIS_URL)",
      ms: Math.round(performance.now() - start),
      kind: "redis",
    };
  }
  try {
    const { createClient } = await import("redis");
    const client = createClient({ url });
    await client.connect();
    const pong = await client.ping();
    await client.quit();
    const ms = Math.round(performance.now() - start);
    return {
      ok: pong === "PONG",
      status: pong === "PONG" ? 200 : null,
      error: pong === "PONG" ? null : "Unexpected PING response",
      ms,
      kind: "redis",
    };
  } catch (error) {
    return {
      ok: false,
      status: null,
      error: error instanceof Error ? error.message : String(error),
      ms: Math.round(performance.now() - start),
      kind: "redis",
    };
  }
}
