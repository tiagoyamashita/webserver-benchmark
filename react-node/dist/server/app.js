import express from "express";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { probeById } from "./probe.js";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
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
        const clientDir = path.resolve(__dirname, "../client");
        app.use(express.static(clientDir));
        app.get("*", (_req, res) => {
            res.sendFile(path.join(clientDir, "index.html"));
        });
    }
    return app;
}
