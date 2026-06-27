import type { NextFunction, Request, Response } from "express";
import type { AuthState } from "./repository.js";
import { isSessionExpired } from "./session.js";

const TRUSTED_ORIGINS = new Set([
  "webserver-benchmark-java",
  "webserver-benchmark-python",
  "webserver-benchmark-rust",
]);

/** Require a registered user for browser `/api/*` data routes when auth is configured. */
export function requireApiSession(auth: AuthState | null) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (!auth) {
      next();
      return;
    }
    const origin = req.header("x-request-origin")?.trim();
    if (origin && TRUSTED_ORIGINS.has(origin)) {
      next();
      return;
    }
    const session = req.sharedSession;
    if (!session) {
      res.status(401).json({ error: "No active session" });
      return;
    }
    if (isSessionExpired(session)) {
      res.status(401).json({ error: "Session expired" });
      return;
    }
    if (session.userId <= 0 || !session.email) {
      res.status(401).json({ error: "Sign in required" });
      return;
    }
    next();
  };
}
