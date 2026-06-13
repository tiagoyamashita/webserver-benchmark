import request from "supertest";
import { describe, expect, it, vi } from "vitest";
import { createApp } from "./app.js";

describe("items proxy", () => {
  it("proxies GET /java/api/items to Java", async () => {
    const fetchImpl = vi.fn(async () =>
      Response.json([{ id: 1, name: "Demo widget", createdAt: "2026-01-01T00:00:00Z" }]),
    );
    const app = createApp({ isProduction: false, fetchImpl });
    const response = await request(app).get("/java/api/items");
    expect(response.status).toBe(200);
    expect(response.body).toEqual([
      { id: 1, name: "Demo widget", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    expect(fetchImpl).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/items$/),
      expect.objectContaining({ method: "GET" }),
    );
  });

  it("proxies POST /java/api/items to Java", async () => {
    const fetchImpl = vi.fn(async () =>
      Response.json({ id: 2, name: "New item", createdAt: "2026-01-02T00:00:00Z" }, { status: 201 }),
    );
    const app = createApp({ isProduction: false, fetchImpl });
    const response = await request(app).post("/java/api/items").send({ name: "New item" });
    expect(response.status).toBe(201);
    expect(response.body.name).toBe("New item");
  });

  it("returns 400 for blank item name", async () => {
    const app = createApp({ isProduction: false });
    const response = await request(app).post("/java/api/items").send({ name: "   " });
    expect(response.status).toBe(400);
  });
});
