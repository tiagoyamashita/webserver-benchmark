import type { ProbeTargetId } from "./targets.js";
import { isProbeTargetId, probeTargetUrl } from "./targets.js";
import { outboundRequestHeaders } from "./request-id.js";
import {
  logOutboundFailure,
  logOutboundRequest,
  logOutboundResponse,
} from "./outbound-http-logging.js";

import { probePostgres } from "./postgres-probe.js";
import { probeRedis } from "./redis-probe.js";

export type ProbeResult = {
  ok: boolean;
  status: number | null;
  error: string | null;
  ms: number;
  kind?: "http" | "postgres" | "redis";
};

export async function runProbe(
  id: ProbeTargetId,
  fetchImpl: typeof fetch = fetch,
  requestId?: string,
): Promise<ProbeResult> {
  if (id === "postgres") {
    return probePostgres();
  }
  if (id === "redis") {
    return probeRedis();
  }

  const url = probeTargetUrl(id);
  const relayTarget = id;
  const start = performance.now();
  logOutboundRequest("GET", url, relayTarget, undefined, requestId);
  let loggedResponse = false;
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      redirect: "follow",
      signal: AbortSignal.timeout(15_000),
      headers: outboundRequestHeaders(requestId),
    });
    const rawBody = await response.text();
    const ms = Math.round(performance.now() - start);
    logOutboundResponse(
      "GET",
      url,
      response.status,
      ms,
      relayTarget,
      response.headers,
      rawBody,
      requestId,
    );
    loggedResponse = true;
    return {
      ok: response.ok,
      status: response.status,
      error: response.ok ? null : response.statusText || `HTTP ${response.status}`,
      ms,
      kind: "http",
    };
  } catch (error) {
    const ms = Math.round(performance.now() - start);
    const message = error instanceof Error ? error.message : String(error);
    if (!loggedResponse) {
      logOutboundFailure("GET", url, ms, relayTarget, message, requestId);
    }
    return {
      ok: false,
      status: null,
      error: message,
      ms,
      kind: "http",
    };
  }
}

export async function probeById(
  rawId: string,
  fetchImpl: typeof fetch = fetch,
  requestId?: string,
): Promise<ProbeResult | { error: string; status: number }> {
  const id = rawId.trim().toLowerCase();
  if (!isProbeTargetId(id)) {
    return { error: "unknown probe target", status: 404 };
  }
  return runProbe(id, fetchImpl, requestId);
}
