import { withSessionHeaders } from "./session";

function newRequestId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

let lastRequestId = "—";
const listeners = new Set<(id: string) => void>();

export function subscribeLastRequestId(listener: (id: string) => void): () => void {
  listeners.add(listener);
  listener(lastRequestId);
  return () => listeners.delete(listener);
}

function notifyRequestId(requestId: string): void {
  lastRequestId = requestId;
  listeners.forEach((listener) => listener(requestId));
}

/** Fresh X-Request-ID per API call; updates the dashboard "Last request ID" display. */
export function apiRequest(
  extra?: Record<string, string>,
  dashboardPage?: string,
): { requestId: string; headers: Record<string, string> } {
  const requestId = newRequestId();
  notifyRequestId(requestId);
  const headers = withSessionHeaders({
    "X-Request-ID": requestId,
    Accept: "application/json",
    ...(dashboardPage ? { "X-Dashboard-Page": dashboardPage } : {}),
    ...extra,
  });
  return { requestId, headers };
}
