import type { ProbeTargetId } from "./targets.js";
import { isProbeTargetId, probeTargetUrl } from "./targets.js";

export type ProbeResult = {
  ok: boolean;
  status: number | null;
  error: string | null;
  ms: number;
};

export async function runProbe(id: ProbeTargetId, fetchImpl: typeof fetch = fetch): Promise<ProbeResult> {
  const url = probeTargetUrl(id);
  const start = performance.now();
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      redirect: "follow",
      signal: AbortSignal.timeout(15_000),
    });
    const ms = Math.round(performance.now() - start);
    return {
      ok: response.ok,
      status: response.status,
      error: response.ok ? null : response.statusText || `HTTP ${response.status}`,
      ms,
    };
  } catch (error) {
    const ms = Math.round(performance.now() - start);
    return {
      ok: false,
      status: null,
      error: error instanceof Error ? error.message : String(error),
      ms,
    };
  }
}

export async function probeById(
  rawId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ProbeResult | { error: string; status: number }> {
  const id = rawId.trim().toLowerCase();
  if (!isProbeTargetId(id)) {
    return { error: "unknown probe target", status: 404 };
  }
  return runProbe(id, fetchImpl);
}
