import request from "supertest";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "./app.js";

const listItemsFromPostgres = vi.fn();
const insertItemIntoPostgres = vi.fn();

vi.mock("./postgres-items.js", () => ({
  DatabaseNotConfiguredError: class DatabaseNotConfiguredError extends Error {
    constructor(message: string) {
      super(message);
      this.name = "DatabaseNotConfiguredError";
    }
  },
  listItemsFromPostgres: (...args: unknown[]) => listItemsFromPostgres(...args),
  insertItemIntoPostgres: (...args: unknown[]) => insertItemIntoPostgres(...args),
}));

describe("postgres items API", () => {
  beforeEach(() => {
    listItemsFromPostgres.mockReset();
    insertItemIntoPostgres.mockReset();
  });

  it("GET /api/items returns items from Postgres", async () => {
    listItemsFromPostgres.mockResolvedValueOnce([
      { id: 1, name: "Widget", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    const app = createApp({ isProduction: false });
    const response = await request(app).get("/api/items");
    expect(response.status).toBe(200);
    expect(response.body).toEqual([
      { id: 1, name: "Widget", createdAt: "2026-01-01T00:00:00Z" },
    ]);
  });

  it("POST /api/items persists via Postgres", async () => {
    insertItemIntoPostgres.mockResolvedValueOnce({
      id: 2,
      name: "New item",
      createdAt: "2026-01-02T00:00:00Z",
    });
    const app = createApp({ isProduction: false });
    const response = await request(app).post("/api/items").send({ name: "New item" });
    expect(response.status).toBe(201);
    expect(response.body.name).toBe("New item");
    expect(insertItemIntoPostgres).toHaveBeenCalledWith("New item", undefined);
  });

  it("POST /api/items returns 400 for blank name", async () => {
    const app = createApp({ isProduction: false });
    const response = await request(app).post("/api/items").send({ name: "   " });
    expect(response.status).toBe(400);
    expect(insertItemIntoPostgres).not.toHaveBeenCalled();
  });
});
