import type { Request } from "express";

import type { SessionConfig } from "./session.js";

export function sessionCookieValue(config: SessionConfig, sessionId: string): string {
  return `${config.cookieName}=${sessionId}; HttpOnly; Path=/; Max-Age=${config.ttlSecs}; SameSite=Lax`;
}

export function clearSessionCookieValue(config: SessionConfig): string {
  return `${config.cookieName}=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax`;
}

function parseBearer(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed.startsWith("Bearer ")) {
    return null;
  }
  const token = trimmed.slice(7).trim();
  return token || null;
}

function readCookie(req: Request, name: string): string | null {
  const raw = req.headers.cookie;
  if (!raw) {
    return null;
  }
  for (const part of raw.split(";")) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf("=");
    if (eq <= 0) {
      continue;
    }
    const key = trimmed.slice(0, eq);
    const value = trimmed.slice(eq + 1).trim();
    if (key === name && value) {
      return value;
    }
  }
  return null;
}

export function sessionIdCandidates(req: Request, cookieName: string): string[] {
  const candidates: string[] = [];
  const authHeader = req.header("authorization");
  if (authHeader) {
    const token = parseBearer(authHeader);
    if (token) {
      candidates.push(token);
    }
  }
  const headerId = req.header("x-session-id")?.trim();
  if (headerId) {
    candidates.push(headerId);
  }
  const cookie = readCookie(req, cookieName);
  if (cookie) {
    candidates.push(cookie);
  }
  return candidates;
}
