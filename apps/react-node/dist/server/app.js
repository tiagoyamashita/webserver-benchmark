import express from "express";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { probeById } from "./probe.js";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
/** Built client assets (Vite outDir: dist/client). */
function resolveClientDir() {
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
export function createApp(options = {}) {
    const { isProduction = process.env.NODE_ENV === "production", fetchImpl } = options;
    const app = express();
    app.get("/api/health", (_req, res) => {
        res.json({ ok: true, service: "react-node" });
    });
    app.get("/api/probe/:id", async (req, res) => {
        const rawId = req.params.id;
        const id = Array.isArray(rawId) ? rawId[0] : rawId;
        const result = await probeById(id ?? "", fetchImpl);
        if ("status" in result && "error" in result && !("ok" in result)) {
            res.status(result.status).json({ error: result.error });
            return;
        }
        res.json(result);
    });
    if (isProduction) {
        const clientDir = resolveClientDir();
        if (!clientDir) {
            throw new Error("Production mode requires a built client (dist/client/index.html). Run npm run build first.");
        }
        app.use(express.static(clientDir));
        app.get("*", (_req, res) => {
            res.sendFile(path.join(clientDir, "index.html"));
        });
    }
    return app;
}
