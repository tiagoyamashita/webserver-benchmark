import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  root: ".",
  publicDir: "public",
  build: {
    outDir: "dist/client",
    emptyOutDir: true,
  },
  server: {
    port: 5174,
    strictPort: true,
    host: true,
    // Compose stack pings use Host: react-node:5174 (Java/Python/Rust server-side GET).
    allowedHosts: true,
    // Podman/Docker bind mounts on Windows often miss file events without polling.
    watch: {
      usePolling: true,
      interval: 500,
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["client/src/test-setup.ts"],
    include: ["client/src/**/*.test.{ts,tsx}", "server/**/*.test.ts"],
  },
});
