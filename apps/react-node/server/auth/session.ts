export type SharedSession = {
  sessionId: string;
  userId: number;
  email: string | null;
  name: string;
  issuedAt: string;
  expiresAt: string;
  issuer: string;
};

export type SessionResponse = SharedSession & {
  redisKey: string;
};

export type SessionConfig = {
  redisKeyPrefix: string;
  ttlSecs: number;
  cookieName: string;
};

export function readEnv(key: string, fallback: string): string {
  const value = process.env[key]?.trim();
  return value || fallback;
}

export function sessionConfigFromEnv(): SessionConfig {
  return {
    redisKeyPrefix: readEnv("WEBSERVER_BENCHMARK_SESSION_REDIS_PREFIX", "webserver-benchmark:session:"),
    ttlSecs: 86_400,
    cookieName: readEnv("WEBSERVER_BENCHMARK_SESSION_COOKIE", "webserver_benchmark_session"),
  };
}

export function redisUrlFromEnv(): string | null {
  const url = process.env.REDIS_URL?.trim();
  if (url) {
    return url;
  }
  const host = process.env.REDIS_HOST?.trim();
  if (!host) {
    return null;
  }
  const port = readEnv("REDIS_PORT", "6379");
  return `redis://${host}:${port}`;
}

export function redisKey(config: SessionConfig, sessionId: string): string {
  return `${config.redisKeyPrefix}${sessionId}`;
}

export function isSessionExpired(session: SharedSession, now = new Date()): boolean {
  return new Date(session.expiresAt).getTime() <= now.getTime();
}

export function toSessionResponse(session: SharedSession, key: string): SessionResponse {
  return { ...session, redisKey: key };
}

export function sessionToJson(session: SharedSession): Record<string, unknown> {
  const payload: Record<string, unknown> = {
    sessionId: session.sessionId,
    userId: session.userId,
    name: session.name,
    issuedAt: session.issuedAt,
    expiresAt: session.expiresAt,
    issuer: session.issuer,
  };
  if (session.email !== null) {
    payload.email = session.email;
  }
  return payload;
}

export function sessionFromJson(raw: Record<string, unknown>): SharedSession {
  return {
    sessionId: String(raw.sessionId),
    userId: Number(raw.userId),
    email: raw.email == null ? null : String(raw.email),
    name: String(raw.name),
    issuedAt: String(raw.issuedAt),
    expiresAt: String(raw.expiresAt),
    issuer: String(raw.issuer),
  };
}

export function formatInstant(date: Date): string {
  return date.toISOString();
}
