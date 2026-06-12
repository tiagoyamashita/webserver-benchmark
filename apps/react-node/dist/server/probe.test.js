import { describe, expect, it, vi } from "vitest";
import { probeById, runProbe } from "./probe.js";
vi.mock("./postgres-probe.js", () => ({
    probePostgres: vi.fn().mockResolvedValue({
        ok: true,
        status: null,
        error: null,
        ms: 3,
        kind: "postgres",
    }),
}));
describe("runProbe", () => {
    it("returns ok for successful response", async () => {
        const fetchImpl = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            statusText: "OK",
        });
        const result = await runProbe("java", fetchImpl);
        expect(result.ok).toBe(true);
        expect(result.status).toBe(200);
        expect(result.error).toBeNull();
    });
    it("returns error for failed fetch", async () => {
        const fetchImpl = vi.fn().mockRejectedValue(new Error("connection refused"));
        const result = await runProbe("java", fetchImpl);
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
    it("probes postgres without HTTP fetch", async () => {
        const fetchImpl = vi.fn();
        const result = await probeById("postgres", fetchImpl);
        expect(result).toMatchObject({ ok: true, kind: "postgres" });
        expect(fetchImpl).not.toHaveBeenCalled();
    });
});
