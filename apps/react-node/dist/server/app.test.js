import request from "supertest";
import { describe, expect, it } from "vitest";
import { createApp } from "./app.js";
describe("createApp", () => {
    it("exposes health endpoint", async () => {
        const app = createApp({ isProduction: false });
        const response = await request(app).get("/api/health");
        expect(response.status).toBe(200);
        expect(response.body).toEqual({ ok: true, service: "react-node" });
    });
    it("returns 404 for unknown probe target", async () => {
        const app = createApp({ isProduction: false });
        const response = await request(app).get("/api/probe/unknown");
        expect(response.status).toBe(404);
        expect(response.body.error).toBe("unknown probe target");
    });
    it("exposes Prometheus metrics at /metrics", async () => {
        const app = createApp({ isProduction: false });
        await request(app).get("/api/health");
        const response = await request(app).get("/metrics");
        expect(response.status).toBe(200);
        expect(response.text).toContain("exercises_http_requests_total");
        expect(response.text).toContain('endpoint="/api/health"');
    });
    it("serves OpenAPI JSON for items routes", async () => {
        const app = createApp({ isProduction: false });
        const response = await request(app).get("/api-docs/openapi.json");
        expect(response.status).toBe(200);
        expect(response.body.openapi).toBe("3.0.3");
        expect(response.body.paths["/api/items"]).toBeDefined();
        expect(response.body.paths["/java/api/items"]).toBeDefined();
        expect(response.body.paths["/api/health"]).toBeUndefined();
    });
});
