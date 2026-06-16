import { randomUUID } from "node:crypto";

import { createUser, findUserAuthByEmail, findUserByEmail, findUserById, verifyPassword } from "../postgres-users.js";
import { deleteSession, findSessionById, saveSession, type AuthState } from "./repository.js";
import {
  formatInstant,
  isSessionExpired,
  type SessionConfig,
  type SharedSession,
} from "./session.js";

export type EnsureSessionResult = {
  session: SharedSession;
  created: boolean;
};

export class AuthServiceError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function ensureSession(
  auth: AuthState,
  clientSessionId: string | null | undefined,
  requestSession: SharedSession | null | undefined,
): Promise<EnsureSessionResult> {
  const now = new Date();
  if (requestSession && !isSessionExpired(requestSession, now)) {
    return { session: requestSession, created: false };
  }
  const trimmedClient = clientSessionId?.trim();
  if (trimmedClient) {
    const stored = await findSessionById(auth, trimmedClient);
    if (stored && !isSessionExpired(stored, now)) {
      return { session: stored, created: false };
    }
    if (stored) {
      await deleteSession(auth, stored.sessionId);
    }
  }
  const session = await createAnonymousSession(auth);
  return { session, created: true };
}

export async function login(
  auth: AuthState,
  config: SessionConfig,
  body: { email?: string; userId?: number; password?: string },
  requestId?: string,
): Promise<SharedSession> {
  const user = await resolveUser(body, requestId);
  const issuedAt = new Date();
  const expiresAt = new Date(issuedAt.getTime() + config.ttlSecs * 1000);
  const session: SharedSession = {
    sessionId: randomUUID(),
    userId: user.id,
    email: user.email,
    name: user.name,
    issuedAt: formatInstant(issuedAt),
    expiresAt: formatInstant(expiresAt),
    issuer: "react-node",
  };
  await saveSession(auth, session);
  return session;
}

export async function logout(auth: AuthState, sessionId: string): Promise<void> {
  await deleteSession(auth, sessionId);
}

export async function logoutAndCreateGuest(
  auth: AuthState,
  sessionId: string | null | undefined,
): Promise<SharedSession> {
  const trimmed = sessionId?.trim();
  if (trimmed) {
    await deleteSession(auth, trimmed);
  }
  return createAnonymousSession(auth);
}

export async function refreshSession(
  auth: AuthState,
  current: SharedSession | null | undefined,
): Promise<SharedSession> {
  if (current) {
    await deleteSession(auth, current.sessionId);
  }
  const issuedAt = new Date();
  const expiresAt = new Date(issuedAt.getTime() + auth.config.ttlSecs * 1000);
  const session: SharedSession =
    current && current.userId > 0
      ? {
          sessionId: randomUUID(),
          userId: current.userId,
          email: current.email,
          name: current.name,
          issuedAt: formatInstant(issuedAt),
          expiresAt: formatInstant(expiresAt),
          issuer: "react-node",
        }
      : {
          sessionId: randomUUID(),
          userId: 0,
          email: null,
          name: "Guest",
          issuedAt: formatInstant(issuedAt),
          expiresAt: formatInstant(expiresAt),
          issuer: "react-node",
        };
  await saveSession(auth, session);
  return session;
}

async function createAnonymousSession(auth: AuthState): Promise<SharedSession> {
  const issuedAt = new Date();
  const expiresAt = new Date(issuedAt.getTime() + auth.config.ttlSecs * 1000);
  const session: SharedSession = {
    sessionId: randomUUID(),
    userId: 0,
    email: null,
    name: "Guest",
    issuedAt: formatInstant(issuedAt),
    expiresAt: formatInstant(expiresAt),
    issuer: "react-node",
  };
  await saveSession(auth, session);
  return session;
}

async function resolveUser(
  body: { email?: string; userId?: number; password?: string },
  requestId?: string,
): Promise<{ id: number; name: string; email: string }> {
  const email = body.email?.trim();
  if (email) {
    const authUser = await findUserAuthByEmail(email, requestId);
    if (!authUser) {
      throw new AuthServiceError(404, `No user with email ${email}`);
    }
    if (body.password) {
      if (!verifyPassword(body.password, authUser.passwordHash)) {
        throw new AuthServiceError(401, "Invalid email or password");
      }
    }
    return authUser;
  }
  if (body.userId != null) {
    const user = await findUserById(body.userId, requestId);
    if (!user) {
      throw new AuthServiceError(404, `No user with id ${body.userId}`);
    }
    return user;
  }
  throw new AuthServiceError(400, "email or userId is required");
}

export async function resolveSharedSession(
  auth: AuthState,
  candidates: string[],
): Promise<SharedSession | null> {
  const now = new Date();
  for (const sessionId of candidates) {
    const session = await findSessionById(auth, sessionId);
    if (!session) {
      continue;
    }
    if (isSessionExpired(session, now)) {
      await deleteSession(auth, session.sessionId);
      continue;
    }
    return session;
  }
  return null;
}
