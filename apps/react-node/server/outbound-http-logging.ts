import { writeLog } from "./observability-logging.js";
import {
  currentInboundMethod,
  currentInboundPath,
  currentRequestId,
  currentSessionId,
} from "./request-context.js";
import { outboundRequestHeaders, resolveOutboundRequestId } from "./request-id.js";

const LOGGER = "http.client";
const RELAY_ORIGIN = "exercises-react-node";

const HEADER_ALLOW = new Set([
  "x-request-id",
  "x-request-origin",
  "content-type",
  "content-length",
  "server",
  "date",
  "location",
]);

function pathWithQuery(url: string): string {
  try {
    const parsed = new URL(url);
    const pathname = parsed.pathname || "/";
    return parsed.search ? `${pathname}${parsed.search}` : pathname;
  } catch {
    return url;
  }
}

function resolveRequestIdForLog(requestId?: string): string {
  const explicit = requestId?.trim();
  if (explicit) {
    return explicit;
  }
  const inbound = currentRequestId();
  if (inbound) {
    return inbound;
  }
  return resolveOutboundRequestId();
}

function parseBody(raw: string | null | undefined): Record<string, unknown> {
  if (!raw?.trim()) {
    return {};
  }
  const text = raw.trim();
  try {
    if (text.startsWith("{")) {
      return JSON.parse(text) as Record<string, unknown>;
    }
    if (text.startsWith("[")) {
      return { _json: JSON.parse(text) as unknown };
    }
  } catch {
    // fall through to raw string
  }
  return { _raw: text };
}

export function resolveResponseError(
  status: number,
  responseBody: string | null | undefined,
): string | null {
  if (status >= 200 && status < 300) {
    return null;
  }
  const parsed = parseBody(responseBody);
  const err = parsed.error;
  if (typeof err === "string" && err.trim()) {
    return err.trim();
  }
  const message = parsed.message;
  if (typeof message === "string" && message.trim()) {
    return message.trim();
  }
  const raw = parsed._raw;
  if (typeof raw === "string" && raw.trim()) {
    return raw.trim();
  }
  if (Object.keys(parsed).length > 0) {
    return JSON.stringify(parsed);
  }
  return `HTTP ${status}`;
}

function filterResponseHeaders(headers: Headers): Record<string, string> {
  const out: Record<string, string> = {};
  headers.forEach((value, name) => {
    if (HEADER_ALLOW.has(name.toLowerCase())) {
      out[name.toLowerCase()] = value;
    }
  });
  return out;
}

function emit(level: "INFO" | "WARN", message: string, fields: Record<string, unknown>): void {
  writeLog(level, message, fields, LOGGER);
  const line = JSON.stringify({ level, message, logger: LOGGER, ...fields });
  if (level === "WARN") {
    console.warn(line);
  } else {
    console.info(line);
  }
}

function baseFields(
  method: string,
  url: string,
  relayTarget: string,
  requestId: string,
  phase: string,
  extra?: Record<string, unknown>,
): Record<string, unknown> {
  const sessionId = currentSessionId();
  const originMethod = currentInboundMethod();
  const originPath = currentInboundPath();
  return {
    method,
    path: pathWithQuery(url),
    request_id: requestId,
    ...(sessionId ? { session_id: sessionId } : {}),
    phase,
    relay_target: relayTarget,
    relay_origin: RELAY_ORIGIN,
    ...(originMethod ? { origin_method: originMethod } : {}),
    ...(originPath ? { origin_path: originPath } : {}),
    ...extra,
  };
}

/** Log an outbound HTTP call before it is sent (links relay to originating inbound request). */
export function logOutboundRequest(
  method: string,
  url: string,
  relayTarget: string,
  requestBody?: Record<string, unknown>,
  requestId?: string,
): void {
  const id = resolveRequestIdForLog(requestId);
  const originMethod = currentInboundMethod() ?? "";
  const originPath = currentInboundPath() ?? "";
  const fields = baseFields(method, url, relayTarget, id, "outbound_request", {
    headers: outboundRequestHeaders(id),
    ...(requestBody && Object.keys(requestBody).length > 0 ? { body: requestBody } : {}),
  });
  emit(
    "INFO",
    `${method} ${pathWithQuery(url)} outbound request request_id=${id} origin=${originMethod} ${originPath}`,
    fields,
  );
}

export function logOutboundResponse(
  method: string,
  url: string,
  status: number,
  ms: number,
  relayTarget: string,
  responseHeaders: Headers,
  responseBody: string | null,
  requestId?: string,
): void {
  const id = resolveRequestIdForLog(requestId);
  const sessionId = currentSessionId() ?? "";
  const ok = status >= 200 && status < 300;
  const fields = baseFields(method, url, relayTarget, id, "outbound_response", {
    status,
    ms,
    headers: filterResponseHeaders(responseHeaders),
    body: parseBody(responseBody),
    ...(ok ? {} : { error: resolveResponseError(status, responseBody) }),
  });
  const message = ok
    ? `${method} ${pathWithQuery(url)} ${status} outbound response request_id=${id} session_id=${sessionId}`
    : `${method} ${pathWithQuery(url)} ${status} outbound response request_id=${id} session_id=${sessionId} error=${resolveResponseError(status, responseBody) ?? ""}`;
  emit(ok ? "INFO" : "WARN", message, fields);
}

export function logOutboundFailure(
  method: string,
  url: string,
  ms: number,
  relayTarget: string,
  error: string,
  requestId?: string,
): void {
  const id = resolveRequestIdForLog(requestId);
  const sessionId = currentSessionId() ?? "";
  const fields = baseFields(method, url, relayTarget, id, "outbound_failed", {
    ms,
    error,
  });
  emit(
    "WARN",
    `${method} ${pathWithQuery(url)} outbound failed request_id=${id} session_id=${sessionId}`,
    fields,
  );
}
