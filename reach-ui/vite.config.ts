import { defineConfig } from "vite";

const docker = process.env.VITE_PROXY_DOCKER === "1";

function probeTarget(service: string, port: string): string {
  return docker ? `http://${service}:${port}` : `http://127.0.0.1:${port}`;
}

const probeProxy = {
  "/api/probe/java": { target: probeTarget("java", "8080"), changeOrigin: true, rewrite: () => "/" },
  "/api/probe/rust": { target: probeTarget("rust", "8082"), changeOrigin: true, rewrite: () => "/" },
  "/api/probe/python": { target: probeTarget("python", "5000"), changeOrigin: true, rewrite: () => "/" },
  "/api/probe/prometheus": {
    target: probeTarget("prometheus", "9090"),
    changeOrigin: true,
    rewrite: () => "/",
  },
  "/api/probe/grafana": { target: probeTarget("grafana", "3000"), changeOrigin: true, rewrite: () => "/" },
  "/api/probe/elasticsearch": {
    target: probeTarget("elasticsearch", "9200"),
    changeOrigin: true,
    rewrite: () => "/",
  },
};

export default defineConfig({
  server: {
    port: 5174,
    strictPort: false,
    host: true,
    proxy: probeProxy,
  },
});
