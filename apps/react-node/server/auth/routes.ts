import type { Express, NextFunction, Request, Response } from "express";

import { logError, logReceivedFromRequest, logSucceeded, logWarn } from "../controller-logging.js";
import type { AuthState } from "./repository.js";
import { sessionIdCandidates } from "./cookies.js";
import {
  AuthServiceError,
  ensureSession,
  login,
  logout,
  resolveSharedSession,
} from "./service.js";
import {
  isSessionExpired,
  redisKey,
  sessionConfigFromEnv,
  toSessionResponse,
  type SharedSession,
} from "./session.js";
import { clearSessionCookieValue, sessionCookieValue } from "./cookies.js";

const SOURCE = "server/auth/routes.ts";

declare module "express-serve-static-core" {
  interface Request {
    sharedSession?: SharedSession;
  }
}

export type AuthRuntime = {
  auth: AuthState | null;
};

export function sessionMiddleware(auth: AuthState | null) {
  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    if (!auth) {
      next();
      return;
    }
    try {
      const candidates = sessionIdCandidates(req, auth.config.cookieName);
      req.sharedSession = (await resolveSharedSession(auth, candidates)) ?? undefined;
    } catch (error) {
      console.warn("[auth] session resolution failed", error);
    }
    next();
  };
}

export function registerAuthRoutes(app: Express, runtime: AuthRuntime): void {
  app.post("/api/auth/ensure", async (req, res) => {
    logReceivedFromRequest(req, "authEnsure", SOURCE, "POST", "/api/auth/ensure");
    if (!runtime.auth) {
      logWarn("authEnsure", SOURCE, "redis not configured");
      res.status(503).json({ error: "Redis session store not configured" });
      return;
    }
    const body = (req.body ?? {}) as { sessionId?: string };
    try {
      const result = await ensureSession(
        runtime.auth,
        body.sessionId,
        req.sharedSession ?? null,
      );
      const payload = toSessionResponse(
        result.session,
        redisKey(runtime.auth.config, result.session.sessionId),
      );
      res
        .status(result.created ? 201 : 200)
        .setHeader("Set-Cookie", sessionCookieValue(runtime.auth.config, result.session.sessionId))
        .json(payload);
      logSucceeded("authEnsure", SOURCE, {
        session_id: result.session.sessionId,
        created: result.created,
        user_id: result.session.userId,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logError("authEnsure", SOURCE, "authEnsure failed", {
        error: message,
      });
      res.status(503).json({ error: message });
    }
  });

  app.post("/api/auth/login", async (req, res) => {
    logReceivedFromRequest(req, "authLogin", SOURCE, "POST", "/api/auth/login");
    if (!runtime.auth) {
      res.status(503).json({ error: "Redis session store not configured" });
      return;
    }
    const body = (req.body ?? {}) as { email?: string; userId?: number };
    try {
      const session = await login(runtime.auth, runtime.auth.config, body, req.requestId);
      const payload = toSessionResponse(session, redisKey(runtime.auth.config, session.sessionId));
      res
        .status(201)
        .setHeader("Set-Cookie", sessionCookieValue(runtime.auth.config, session.sessionId))
        .json(payload);
      logSucceeded("authLogin", SOURCE, {
        session_id: session.sessionId,
        user_id: session.userId,
      });
    } catch (error) {
      if (error instanceof AuthServiceError) {
        logWarn("authLogin", SOURCE, "authLogin rejected", {
          error: error.message,
        });
        res.status(error.status).json({ error: error.message });
        return;
      }
      const message = error instanceof Error ? error.message : String(error);
      logError("authLogin", SOURCE, "authLogin failed", {
        error: message,
      });
      res.status(503).json({ error: message });
    }
  });

  app.post("/api/auth/logout", async (req, res) => {
    logReceivedFromRequest(req, "authLogout", SOURCE, "POST", "/api/auth/logout");
    if (!runtime.auth) {
      res.status(503).json({ error: "Redis session store not configured" });
      return;
    }
    const session = req.sharedSession;
    if (!session) {
      res.status(401).json({ error: "No active session" });
      return;
    }
    try {
      await logout(runtime.auth, session.sessionId);
    } catch (error) {
      logWarn("authLogout", SOURCE, "authLogout redis delete failed", {
        error: error instanceof Error ? error.message : String(error),
      });
    }
    res
      .status(204)
      .setHeader("Set-Cookie", clearSessionCookieValue(runtime.auth.config))
      .end();
    logSucceeded("authLogout", SOURCE, {
      session_id: session.sessionId,
    });
  });

  app.get("/api/auth/session", (req, res) => {
    logReceivedFromRequest(req, "authSession", SOURCE, "GET", "/api/auth/session");
    if (!runtime.auth) {
      res.status(503).json({ error: "Redis session store not configured" });
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
    const payload = toSessionResponse(session, redisKey(runtime.auth.config, session.sessionId));
    logSucceeded("authSession", SOURCE, {
      session_id: session.sessionId,
      user_id: session.userId,
    });
    res.json(payload);
  });
}

export async function createAuthRuntime(): Promise<AuthRuntime> {
  const config = sessionConfigFromEnv();
  try {
    const { connectAuthState } = await import("./repository.js");
    const auth = await connectAuthState(config);
    if (auth) {
      console.log("[auth] Redis session store connected");
    }
    return { auth };
  } catch (error) {
    console.warn("[auth] Redis session store unavailable:", error);
    return { auth: null };
  }
}
