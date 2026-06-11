import express, { type Express, type NextFunction, type Request, type Response } from "express";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  logError,
  logReceived,
  logSucceeded,
  logTrace,
  logWarn,
} from "./controller-logging.js";
import { createItem, fetchItems } from "./items.js";
import {
  observabilityEnabled,
  writeLog,
} from "./observability-logging.js";
import { metricsHandler, metricsMiddleware } from "./metrics.js";
import { registerOpenApiRoutes } from "./openapi.js";
import { probeById } from "./probe.js";
import { requestIdMiddleware } from "./request-id.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SOURCE = "server/app.ts";

export type CreateAppOptions = {
  isProduction?: boolean;
  fetchImpl?: typeof fetch;
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
  const { isProduction = process.env.NODE_ENV === "production", fetchImpl } = options;
  const app = express();
  app.use(express.json());
  app.use(requestIdMiddleware);
  app.use(metricsMiddleware);

  registerOpenApiRoutes(app);

  if (observabilityEnabled()) {
    app.use((req: Request, res: Response, next: NextFunction) => {
      const start = Date.now();
      res.on("finish", () => {
        writeLog("INFO", `${req.method} ${req.originalUrl} ${res.statusCode}`, {
          method: req.method,
          path: req.originalUrl,
          status: res.statusCode,
          ms: Date.now() - start,
          request_id: req.requestId,
        });
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
    logReceived("probe", SOURCE, "GET", "/api/probe/{id}", { target });
    const result = await probeById(target, fetchImpl);
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

  app.get("/api/items", async (req: Request, res: Response) => {
    logReceived("listItems", SOURCE, "GET", "/api/items", {
      request_id: req.requestId,
    });
    try {
      const items = await fetchItems(fetchImpl, req.requestId);
      logSucceeded("listItems", SOURCE, { count: items.length, request_id: req.requestId });
      logTrace("listItems", SOURCE, "listItems result", { items, request_id: req.requestId });
      res.json(items);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logError("listItems", SOURCE, "listItems failed", {
        request_id: req.requestId,
        error: message,
      });
      res.status(502).json({ error: message });
    }
  });

  app.post("/api/items", async (req: Request, res: Response) => {
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    logReceived("createItem", SOURCE, "POST", "/api/items", {
      name,
      request_id: req.requestId,
    });
    if (!name) {
      logWarn("createItem", SOURCE, "createItem validation failed", {
        name: req.body?.name,
        reason: "blank-name",
        request_id: req.requestId,
      });
      res.status(400).json({ error: "name must not be blank" });
      return;
    }
    try {
      const item = await createItem(name, fetchImpl, req.requestId);
      logSucceeded("createItem", SOURCE, {
        id: item.id,
        name: item.name,
        request_id: req.requestId,
      });
      res.status(201).json(item);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logError("createItem", SOURCE, "createItem failed", {
        name,
        request_id: req.requestId,
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
