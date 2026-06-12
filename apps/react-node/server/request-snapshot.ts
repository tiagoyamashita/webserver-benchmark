import type { Request } from "express";

const HEADER_ALLOW = new Set([
  "x-request-id",
  "x-request-origin",
  "x-dashboard-page",
  "x-session-id",
  "content-type",
  "accept",
  "user-agent",
  "host",
]);

export function requestHeaders(req: Request): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(req.headers)) {
    const lowered = key.toLowerCase();
    if (!HEADER_ALLOW.has(lowered)) {
      continue;
    }
    if (typeof value === "string") {
      out[lowered] = value;
    } else if (Array.isArray(value) && value.length > 0) {
      out[lowered] = value[0];
    }
  }
  return out;
}

export function requestUrlParams(req: Request): Record<string, unknown> | undefined {
  const query = req.query as Record<string, unknown>;
  return Object.keys(query).length > 0 ? query : undefined;
}

export function requestBody(req: Request): Record<string, unknown> | undefined {
  if (req.method === "GET" || req.method === "HEAD") {
    return undefined;
  }
  if (req.body && typeof req.body === "object" && !Array.isArray(req.body)) {
    const body = req.body as Record<string, unknown>;
    return Object.keys(body).length > 0 ? body : undefined;
  }
  return undefined;
}
