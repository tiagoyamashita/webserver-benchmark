import { afterEach, describe, expect, it, vi } from "vitest";
import {
  DatabaseNotConfiguredError,
  insertItemIntoPostgres,
  listItemsFromPostgres,
} from "./postgres-items.js";

const query = vi.fn();
const connect = vi.fn().mockResolvedValue(undefined);
const end = vi.fn().mockResolvedValue(undefined);

vi.mock("pg", () => ({
  default: {
    Client: class {
      connect = connect;
      query = query;
      end = end;
    },
  },
}));

describe("postgres-items", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    query.mockReset();
    connect.mockClear();
    end.mockClear();
  });

  it("listItemsFromPostgres returns rows", async () => {
    vi.stubEnv("DB_HOST", "postgres");
    query.mockResolvedValueOnce({
      rows: [{ id: "1", name: "Widget", created_at: new Date("2026-01-01T00:00:00Z") }],
    });
    const items = await listItemsFromPostgres("req-12345678");
    expect(items).toEqual([
      { id: 1, name: "Widget", createdAt: "2026-01-01T00:00:00.000Z" },
    ]);
  });

  it("insertItemIntoPostgres returns created row", async () => {
    vi.stubEnv("DB_HOST", "postgres");
    query.mockResolvedValueOnce({
      rows: [{ id: "2", name: "New", created_at: new Date("2026-01-02T00:00:00Z") }],
    });
    const item = await insertItemIntoPostgres("New", "req-12345678");
    expect(item).toEqual({ id: 2, name: "New", createdAt: "2026-01-02T00:00:00.000Z" });
    expect(query).toHaveBeenCalledWith(
      expect.stringContaining("INSERT INTO items"),
      ["New"],
    );
  });

  it("throws DatabaseNotConfiguredError when DB_HOST is unset", async () => {
    await expect(listItemsFromPostgres()).rejects.toBeInstanceOf(DatabaseNotConfiguredError);
  });
});
