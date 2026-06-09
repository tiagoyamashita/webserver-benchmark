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
});
