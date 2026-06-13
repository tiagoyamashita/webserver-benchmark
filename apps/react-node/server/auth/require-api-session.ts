import type { NextFunction, Request, Response } from "express";
import type { AuthState } from "./repository.js";
import { isSessionExpired } from "./session.js";

const TRUSTED_ORIGINS = new Set([
  "exercises-java",
  "exercises-python",
  "exercises-rust",
]);

/** Require Redis session for browser `/api/*` data routes when auth is configured. */
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
    next();
  };
}
