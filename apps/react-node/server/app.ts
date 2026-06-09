import express, { type Express, type NextFunction, type Request, type Response } from "express";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createItem, fetchItems } from "./items.js";
import {
  observabilityEnabled,
  writeLog,
} from "./observability-logging.js";
import { probeById } from "./probe.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

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

  if (observabilityEnabled()) {
    app.use((req: Request, res: Response, next: NextFunction) => {
      const start = Date.now();
      res.on("finish", () => {
        writeLog("INFO", `${req.method} ${req.originalUrl} ${res.statusCode}`, {
          method: req.method,
          path: req.originalUrl,
          status: res.statusCode,
          ms: Date.now() - start,
        });
      });
      next();
    });
  }

  app.get("/api/health", (_req: Request, res: Response) => {
    res.json({ ok: true, service: "react-node" });
  });

  app.get("/api/observability/sample-log", (_req: Request, res: Response) => {
    writeLog(
      "INFO",
      "Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)",
    );
    res.send("logged");
  });

  app.get("/api/probe/:id", async (req: Request, res: Response) => {
    const rawId = req.params.id;
    const id = Array.isArray(rawId) ? rawId[0] : rawId;
    const result = await probeById(id ?? "", fetchImpl);
    if ("status" in result && "error" in result && !("ok" in result)) {
      res.status(result.status).json({ error: result.error });
      return;
    }
    res.json(result);
  });

  app.get("/api/items", async (_req: Request, res: Response) => {
    try {
      const items = await fetchItems(fetchImpl);
      res.json(items);
    } catch (error) {
      res.status(502).json({
        error: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.post("/api/items", async (req: Request, res: Response) => {
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    if (!name) {
      res.status(400).json({ error: "name must not be blank" });
      return;
    }
    try {
      const item = await createItem(name, fetchImpl);
      res.status(201).json(item);
    } catch (error) {
      res.status(502).json({
        error: error instanceof Error ? error.message : String(error),
      });
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
    app.get("*", (_req: Request, res: Response) => {
      res.sendFile(path.join(clientDir, "index.html"));
    });
  }

  return app;
}
