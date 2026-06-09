import { describe, expect, it, vi } from "vitest";
import { probeById, runProbe } from "./probe.js";

describe("runProbe", () => {
  it("returns ok for successful response", async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      statusText: "OK",
    });

    const result = await runProbe("java", fetchImpl as typeof fetch);
    expect(result.ok).toBe(true);
    expect(result.status).toBe(200);
    expect(result.error).toBeNull();
  });

  it("returns error for failed fetch", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("connection refused"));

    const result = await runProbe("java", fetchImpl as typeof fetch);
    expect(result.ok).toBe(false);
    expect(result.status).toBeNull();
    expect(result.error).toContain("connection refused");
  });
});

describe("probeById", () => {
  it("rejects unknown targets", async () => {
    const result = await probeById("not-a-service");
    expect(result).toEqual({ error: "unknown probe target", status: 404 });
  });
});
