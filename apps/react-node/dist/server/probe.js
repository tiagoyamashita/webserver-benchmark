import { isProbeTargetId, probeTargetUrl } from "./targets.js";
import { probePostgres } from "./postgres-probe.js";
import { probeRedis } from "./redis-probe.js";
export async function runProbe(id, fetchImpl = fetch) {
    if (id === "postgres") {
        return probePostgres();
    }
    if (id === "redis") {
        return probeRedis();
    }
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
            kind: "http",
        };
    }
    catch (error) {
        const ms = Math.round(performance.now() - start);
        return {
            ok: false,
            status: null,
            error: error instanceof Error ? error.message : String(error),
            ms,
            kind: "http",
        };
    }
}
export async function probeById(rawId, fetchImpl = fetch) {
    const id = rawId.trim().toLowerCase();
    if (!isProbeTargetId(id)) {
        return { error: "unknown probe target", status: 404 };
    }
    return runProbe(id, fetchImpl);
}
