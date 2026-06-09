import { describe, expect, it, vi } from "vitest";
import { probePostgres } from "./postgres-probe.js";

vi.mock("pg", () => {
  class Client {
    connect = vi.fn().mockResolvedValue(undefined);
    query = vi.fn().mockResolvedValue({ rows: [{ "?column?": 1 }] });
    end = vi.fn().mockResolvedValue(undefined);
  }
  return { default: { Client } };
});

describe("probePostgres", () => {
  it("returns ok when SELECT 1 succeeds", async () => {
    const result = await probePostgres();
    expect(result.ok).toBe(true);
    expect(result.kind).toBe("postgres");
    expect(result.status).toBeNull();
  });
});
