const STORAGE_KEY = "exercises_session_id";

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

function newRequestId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function storedSessionId(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

export function setStoredSessionId(sessionId: string): void {
  try {
    if (sessionId) {
      localStorage.setItem(STORAGE_KEY, sessionId);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    /* ignore */
  }
}

export function withSessionHeaders(headers: Record<string, string> = {}): Record<string, string> {
  const next = { ...headers };
  const sessionId = storedSessionId();
  if (sessionId) {
    next["X-Session-ID"] = sessionId;
    next.Authorization = `Bearer ${sessionId}`;
  }
  return next;
}

export async function ensureSession(): Promise<SessionData> {
  const sessionId = storedSessionId();
  const headers: Record<string, string> = {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Request-ID": newRequestId(),
  };
  if (sessionId) {
    headers["X-Session-ID"] = sessionId;
    headers.Authorization = `Bearer ${sessionId}`;
  }
  const response = await fetch("/api/auth/ensure", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify(sessionId ? { sessionId } : {}),
  });
  const text = await response.text();
  let data: unknown = text;
  try {
    data = JSON.parse(text);
  } catch {
    /* plain error */
  }
  if (!response.ok || !data || typeof data !== "object" || !("sessionId" in data)) {
    setStoredSessionId("");
    throw new Error(`Session ensure failed: HTTP ${response.status} ${String(text)}`);
  }
  const session = data as SessionData;
  setStoredSessionId(session.sessionId);
  return session;
}

export async function fetchCurrentSession(): Promise<SessionData> {
  const response = await fetch("/api/auth/session", {
    method: "GET",
    credentials: "same-origin",
    headers: withSessionHeaders({ Accept: "application/json" }),
  });
  const data = (await response.json()) as SessionData & { error?: string };
  if (!response.ok) {
    throw new Error(data.error ?? response.statusText);
  }
  setStoredSessionId(data.sessionId);
  return data;
}

export async function loginSession(body: {
  email?: string;
  userId?: number;
}): Promise<SessionData> {
  const response = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "same-origin",
    headers: withSessionHeaders({
      Accept: "application/json",
      "Content-Type": "application/json",
    }),
    body: JSON.stringify(body),
  });
  const data = (await response.json()) as SessionData & { error?: string };
  if (!response.ok) {
    throw new Error(data.error ?? response.statusText);
  }
  setStoredSessionId(data.sessionId);
  return data;
}

export async function logoutSession(): Promise<void> {
  const response = await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "same-origin",
    headers: withSessionHeaders({ Accept: "application/json" }),
  });
  setStoredSessionId("");
  if (!response.ok && response.status !== 204) {
    const data = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(data.error ?? response.statusText);
  }
}
