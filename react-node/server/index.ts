import { createServer as createViteServer } from "vite";
import { createApp } from "./app.js";

const port = Number(process.env.PORT ?? 5174);
const isProduction = process.env.NODE_ENV === "production";

async function start() {
  const app = createApp({ isProduction });

  if (!isProduction) {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "custom",
    });
    app.use(vite.middlewares);
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
