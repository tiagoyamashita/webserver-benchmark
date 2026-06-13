/** Skip noisy access logs for routine probe/health traffic (log failures only). */

const QUIET_GET_PATHS = new Set(["/api/health", "/metrics"]);

export function requestPathname(path: string): string {
  const queryIndex = path.indexOf("?");
  let pathname = queryIndex === -1 ? path : path.slice(0, queryIndex);
  if (pathname.length > 1 && pathname.endsWith("/")) {
    pathname = pathname.slice(0, -1);
  }
  return pathname;
}

/**
 * When false, skip http.request access lines.
 * GET /api/health and GET /metrics: omit on 200; log received + completed only when status != 200.
 */
export function shouldLogHttpAccess(method: string, path: string, status?: number): boolean {
  if (method.toUpperCase() !== "GET") {
    return true;
  }
  if (!QUIET_GET_PATHS.has(requestPathname(path))) {
    return true;
  }
  if (status === undefined) {
    return false;
  }
  return status !== 200;
}
