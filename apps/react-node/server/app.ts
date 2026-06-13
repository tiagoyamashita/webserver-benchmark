import express, { type Express, type NextFunction, type Request, type Response } from "express";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  logError,
  logReceivedFromRequest,
  logSucceeded,
  logTrace,
  logWarn,
} from "./controller-logging.js";
import { createItem, fetchItems } from "./items.js";
import {
  DatabaseNotConfiguredError,
  insertItemIntoPostgres,
  listItemsFromPostgres,
} from "./postgres-items.js";
import { registerAuthRoutes, sessionMiddleware, type AuthRuntime } from "./auth/routes.js";
import { requireApiSession } from "./auth/require-api-session.js";
import {
  observabilityEnabled,
  writeLog,
} from "./observability-logging.js";
import { metricsHandler, metricsMiddleware } from "./metrics.js";
import { registerOpenApiRoutes } from "./openapi.js";
import { probeById } from "./probe.js";
import { requestIdMiddleware } from "./request-id.js";
import { requestContext } from "./request-context.js";
import { requestBody, requestHeaders, requestUrlParams } from "./request-snapshot.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SOURCE = "server/app.ts";
const POSTGRES_ITEMS_SOURCE = "server/postgres-items.ts";

function httpAccessSessionFields(req: Request): { session_id?: string } {
  const sessionId = req.sharedSession?.sessionId;
  return sessionId ? { session_id: sessionId } : {};
}

export type CreateAppOptions = {
  isProduction?: boolean;
  fetchImpl?: typeof fetch;
  authRuntime?: AuthRuntime;
};

/** Built client assets (Vite outDir: dist/client). */
function resolveClientDir(): string | null {
  const candidates = [
    path.resolve(__dirname, "../client"),
    path.resolve(__dirname, "../../dist/client"),
  ];
  for (const dir of candidates) {
    if (existsSync(path.join(dir, "index.html"))) {
      return dir;
    }
  }
  return null;
}

export function createApp(options: CreateAppOptions = {}): Express {
  const { isProduction = process.env.NODE_ENV === "production", fetchImpl, authRuntime = { auth: null } } =
    options;
  const app = express();
  const protectApiSession = requireApiSession(authRuntime.auth);
  app.use(express.json());
  app.use(requestIdMiddleware);
  app.use(sessionMiddleware(authRuntime.auth));
  app.use((req: Request, res: Response, next: NextFunction) => {
    requestContext.run(
      {
        sessionId: req.sharedSession?.sessionId,
        requestId: req.requestId,
        inboundMethod: req.method,
        inboundPath: req.path,
      },
      () => next(),
    );
  });
  app.use(metricsMiddleware);

  registerOpenApiRoutes(app);
  registerAuthRoutes(app, authRuntime);

  if (observabilityEnabled()) {
    app.use((req: Request, res: Response, next: NextFunction) => {
      const start = Date.now();
      const receivedFields = {
        method: req.method,
        path: req.originalUrl,
        request_id: req.requestId,
        phase: "received",
        ...httpAccessSessionFields(req),
        headers: requestHeaders(req),
        url_params: requestUrlParams(req),
        body: requestBody(req),
      };
      writeLog(
        "INFO",
        `${req.method} ${req.originalUrl} request received request_id=${req.requestId}`,
        receivedFields,
        "http.request",
      );
      console.info(
        JSON.stringify({
          level: "INFO",
          logger: "http.request",
          message: `${req.method} ${req.originalUrl} request received request_id=${req.requestId}`,
          ...receivedFields,
        }),
      );
      res.on("finish", () => {
        const completedFields = {
          method: req.method,
          path: req.originalUrl,
          status: res.statusCode,
          ms: Date.now() - start,
          request_id: req.requestId,
          phase: "completed",
          ...httpAccessSessionFields(req),
        };
        writeLog(
          "INFO",
          `${req.method} ${req.originalUrl} ${res.statusCode} request_id=${req.requestId}`,
          completedFields,
          "http.request",
        );
        console.info(
          JSON.stringify({
            level: "INFO",
            logger: "http.request",
            message: `${req.method} ${req.originalUrl} ${res.statusCode} request_id=${req.requestId}`,
            ...completedFields,
          }),
        );
      });
      next();
    });
  }

  app.get("/metrics", (req, res) => {
    void metricsHandler(req, res);
  });

  app.get("/api/health", (_req: Request, res: Response) => {
    res.json({ ok: true, service: "react-node" });
  });

  app.get("/api/observability/sample-log", (_req: Request, res: Response) => {
    logReceived("observabilitySampleLog", SOURCE, "GET", "/api/observability/sample-log");
    writeLog(
      "INFO",
      "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)",
      { source: SOURCE, controller: "observabilitySampleLog" },
    );
    logSucceeded("observabilitySampleLog", SOURCE);
    res.send("logged");
  });

  app.get("/api/probe/:id", async (req: Request, res: Response) => {
    const rawId = req.params.id;
    const id = Array.isArray(rawId) ? rawId[0] : rawId;
    const target = id ?? "";
    logReceivedFromRequest(req, "probe", SOURCE, "GET", "/api/probe/{id}", { target });
    const result = await probeById(target, fetchImpl, req.requestId);
    if ("status" in result && "error" in result && !("ok" in result)) {
      logWarn("probe", SOURCE, "probe unknown target", {
        target,
        status: result.status,
        error: result.error,
      });
      res.status(result.status).json({ error: result.error });
      return;
    }
    if (!result.ok) {
      logWarn("probe", SOURCE, "probe downstream unreachable", {
        target,
        ok: result.ok,
        status: result.status,
        error: result.error,
        ms: result.ms,
        kind: result.kind,
      });
    } else {
      logSucceeded("probe", SOURCE, {
        target,
        ok: result.ok,
        status: result.status,
        ms: result.ms,
        kind: result.kind,
      });
    }
    res.json(result);
  });

  app.get("/api/items", protectApiSession, async (req: Request, res: Response) => {
    logReceivedFromRequest(req, "list_items", POSTGRES_ITEMS_SOURCE, "GET", "/api/items");
    try {
      const items = await listItemsFromPostgres(req.requestId);
      logSucceeded("list_items", POSTGRES_ITEMS_SOURCE, { count: items.length });
      logTrace("list_items", POSTGRES_ITEMS_SOURCE, "list_items result", { items });
      res.json(items);
    } catch (error) {
      if (error instanceof DatabaseNotConfiguredError) {
        logWarn("list_items", POSTGRES_ITEMS_SOURCE, "list_items database not configured", {
          target_service: "postgres",
          error: error.message,
        });
        res.status(503).json({ error: error.message });
        return;
      }
      const message = error instanceof Error ? error.message : String(error);
      logError("list_items", POSTGRES_ITEMS_SOURCE, "list_items failed", {
        target_service: "postgres",
        error: message,
      });
      res.status(500).json({ error: "Internal server error" });
    }
  });

  app.post("/api/items", protectApiSession, async (req: Request, res: Response) => {
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    logReceivedFromRequest(req, "create_item", POSTGRES_ITEMS_SOURCE, "POST", "/api/items", {
      item_name: name || undefined,
    });
    if (!name) {
      logWarn("create_item", POSTGRES_ITEMS_SOURCE, "create_item validation failed", {
        item_name: req.body?.name,
        reason: "blank-name",
      });
      res.status(400).json({ error: "name must not be blank" });
      return;
    }
    try {
      const item = await insertItemIntoPostgres(name, req.requestId);
      logSucceeded("create_item", POSTGRES_ITEMS_SOURCE, {
        item_id: item.id,
        item_name: item.name,
      });
      res.status(201).json(item);
    } catch (error) {
      if (error instanceof DatabaseNotConfiguredError) {
        logWarn("create_item", POSTGRES_ITEMS_SOURCE, "create_item database not configured", {
          target_service: "postgres",
          item_name: name,
          error: error.message,
        });
        res.status(503).json({ error: error.message });
        return;
      }
      const message = error instanceof Error ? error.message : String(error);
      logError("create_item", POSTGRES_ITEMS_SOURCE, "create_item failed", {
        target_service: "postgres",
        item_name: name,
        error: message,
      });
      res.status(500).json({ error: "Internal server error" });
    }
  });

  app.get("/java/api/items", async (req: Request, res: Response) => {
    logReceivedFromRequest(req, "listItems", SOURCE, "GET", "/java/api/items");
    try {
      const items = await fetchItems(fetchImpl, req.requestId);
      logSucceeded("listItems", SOURCE, { count: items.length });
      logTrace("listItems", SOURCE, "listItems result", { items });
      res.json(items);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logError("listItems", SOURCE, "listItems failed", {
        error: message,
      });
      res.status(502).json({ error: message });
    }
  });

  app.post("/java/api/items", async (req: Request, res: Response) => {
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    logReceivedFromRequest(req, "createItem", SOURCE, "POST", "/java/api/items", { name });
    if (!name) {
      logWarn("createItem", SOURCE, "createItem validation failed", {
        name: req.body?.name,
        reason: "blank-name",
      });
      res.status(400).json({ error: "name must not be blank" });
      return;
    }
    try {
      const item = await createItem(name, fetchImpl, req.requestId);
      logSucceeded("createItem", SOURCE, {
        id: item.id,
        name: item.name,
      });
      res.status(201).json(item);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logError("createItem", SOURCE, "createItem failed", {
        name,
        error: message,
      });
      res.status(502).json({ error: message });
    }
  });

  if (isProduction) {
    const clientDir = resolveClientDir();
    if (!clientDir) {
      throw new Error(
        "Production mode requires a built client (dist/client/index.html). Run npm run build first.",
      );
    }
    app.use(express.static(clientDir));
    app.get("*", (req: Request, res: Response) => {
      logReceived("spaFallback", SOURCE, "GET", req.originalUrl, { template: "index.html" });
      res.sendFile(path.join(clientDir, "index.html"), (err) => {
        if (err) {
          logError("spaFallback", SOURCE, "spaFallback failed", {
            path: req.originalUrl,
            error: err.message,
          });
          return;
        }
        logSucceeded("spaFallback", SOURCE, { path: req.originalUrl });
      });
    });
  }

  return app;
}

export { createAuthRuntime } from "./auth/routes.js";
