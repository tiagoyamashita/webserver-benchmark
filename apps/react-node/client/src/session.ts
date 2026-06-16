import { apiRequest } from "./api-request";

export type SessionData = {
  sessionId: string;
  userId: number;
  email: string | null;
  name: string;
  issuedAt: string;
  expiresAt: string;
  issuer: string;
  redisKey: string;
};

const LEGACY_STORAGE_KEY = "exercises_session_id";

function clearLegacyStoredSessionId(): void {
  try {
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

clearLegacyStoredSessionId();

/** Session id is sent only via HttpOnly cookie; do not add Bearer / X-Session-ID from JS. */
export function withSessionHeaders(headers: Record<string, string> = {}): Record<string, string> {
  return { ...headers };
}

export async function ensureSession(): Promise<SessionData> {
  clearLegacyStoredSessionId();
  const { headers } = apiRequest({ "Content-Type": "application/json" });
  const response = await fetch("/api/auth/ensure", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: "{}",
  });
  const text = await response.text();
  let data: unknown = text;
  try {
    data = JSON.parse(text);
  } catch {
    /* plain error */
  }
  if (!response.ok || !data || typeof data !== "object" || !("sessionId" in data)) {
    throw new Error(`Session ensure failed: HTTP ${response.status} ${String(text)}`);
  }
  return data as SessionData;
}

export async function fetchCurrentSession(): Promise<SessionData> {
  const { headers } = apiRequest();
  const response = await fetch("/api/auth/session", {
    method: "GET",
    credentials: "same-origin",
    headers,
  });
  const data = (await response.json()) as SessionData & { error?: string };
  if (!response.ok) {
    throw new Error(data.error ?? response.statusText);
  }
  return data;
}

export async function loginSession(body: {
  email?: string;
  userId?: number;
  password?: string;
}): Promise<SessionData> {
  const { headers } = apiRequest({ "Content-Type": "application/json" });
  const response = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify(body),
  });
  const data = (await response.json()) as SessionData & { error?: string };
  if (!response.ok) {
    throw new Error(data.error ?? response.statusText);
  }
  return data;
}

export async function logoutSession(): Promise<void> {
  const { headers } = apiRequest();
  const response = await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "same-origin",
    headers,
  });
  if (!response.ok && response.status !== 204) {
    const data = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(data.error ?? response.statusText);
  }
}

export async function refreshSessionId(): Promise<SessionData> {
  const { headers } = apiRequest({ "Content-Type": "application/json" });
  const response = await fetch("/api/auth/refresh", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: "{}",
  });
  const text = await response.text();
  let data: unknown = text;
  try {
    data = JSON.parse(text);
  } catch {
    /* plain error */
  }
  if (!response.ok || !data || typeof data !== "object" || !("sessionId" in data)) {
    throw new Error(`Session refresh failed: HTTP ${response.status} ${String(text)}`);
  }
  return data as SessionData;
}
