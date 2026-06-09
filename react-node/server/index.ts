import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createApp } from "./app.js";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const port = Number(process.env.PORT ?? 5174);
const isProduction = process.env.NODE_ENV === "production";

async function start() {
  const app = createApp({ isProduction });

  if (!isProduction) {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      root: rootDir,
      server: {
        middlewareMode: true,
        // Allow server-side probes from other containers (Host: react-node:5174).
        allowedHosts: true,
      },
      appType: "custom",
    });
    app.use(vite.middlewares);

    // Vite middlewareMode does not serve index.html for GET / — add SPA fallback.
    app.use("*", async (req, res, next) => {
      if (req.originalUrl.startsWith("/api/")) {
        next();
        return;
      }
      try {
        const template = await fs.readFile(path.join(rootDir, "index.html"), "utf-8");
        const html = await vite.transformIndexHtml(req.originalUrl, template);
        res.status(200).set({ "Content-Type": "text/html" }).end(html);
      } catch (error) {
        vite.ssrFixStacktrace(error as Error);
        next(error);
      }
    });
  }

  app.listen(port, "0.0.0.0", () => {
    console.log(
      `react-node listening on http://0.0.0.0:${port} (${isProduction ? "production" : "development"})`,
    );
  });
}

start().catch((error) => {
  console.error(error);
  process.exit(1);
});
