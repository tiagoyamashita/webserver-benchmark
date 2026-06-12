import { createClient } from "redis";

import {
  redisUrlFromEnv,
  type SessionConfig,
  type SharedSession,
  sessionFromJson,
  sessionToJson,
  redisKey,
} from "./session.js";

type RedisClient = ReturnType<typeof createClient>;

export type AuthState = {
  client: RedisClient;
  config: SessionConfig;
};

export async function connectAuthState(config: SessionConfig): Promise<AuthState | null> {
  const url = redisUrlFromEnv();
  if (!url) {
    return null;
  }
  const client = createClient({ url });
  client.on("error", (error) => {
    console.warn("[auth] redis client error", error);
  });
  await client.connect();
  await client.ping();
  return { client, config };
}

export async function findSessionById(
  auth: AuthState,
  sessionId: string,
): Promise<SharedSession | null> {
  const trimmed = sessionId.trim();
  if (!trimmed) {
    return null;
  }
  const raw = await auth.client.get(redisKey(auth.config, trimmed));
  if (!raw) {
    return null;
  }
  return sessionFromJson(JSON.parse(raw) as Record<string, unknown>);
}

export async function saveSession(auth: AuthState, session: SharedSession): Promise<void> {
  await auth.client.setEx(
    redisKey(auth.config, session.sessionId),
    auth.config.ttlSecs,
    JSON.stringify(sessionToJson(session)),
  );
}

export async function deleteSession(auth: AuthState, sessionId: string): Promise<void> {
  const trimmed = sessionId.trim();
  if (!trimmed) {
    return;
  }
  await auth.client.del(redisKey(auth.config, trimmed));
}
