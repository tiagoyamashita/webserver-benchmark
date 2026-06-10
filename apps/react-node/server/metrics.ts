/** Prometheus scrape endpoint — same series as Python / Rust (`exercises_http_requests_total`). */

import client from "prom-client";
import type { NextFunction, Request, Response } from "express";

export const httpRequestsTotal = new client.Counter({
  name: "exercises_http_requests_total",
  help: "HTTP requests handled by the exercises Express app",
  labelNames: ["method", "endpoint"],
});

export function metricsMiddleware(req: Request, res: Response, next: NextFunction): void {
  res.on("finish", () => {
    httpRequestsTotal.labels(req.method, endpointLabel(req)).inc();
  });
  next();
}

function endpointLabel(req: Request): string {
  const route = req.route as { path?: string } | undefined;
  if (route?.path) {
    const combined = `${req.baseUrl ?? ""}${route.path}`;
    return combined || "unknown";
  }
  return req.path || "unknown";
}

export async function metricsHandler(_req: Request, res: Response): Promise<void> {
  res.set("Content-Type", client.register.contentType);
  res.end(await client.register.metrics());
}
