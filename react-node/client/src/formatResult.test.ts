import { describe, expect, it } from "vitest";
import { formatProbeResult, probeResultClassName } from "./formatResult";

describe("formatProbeResult", () => {
  it("formats success", () => {
    expect(
      formatProbeResult({ ok: true, status: 200, error: null, ms: 42 }),
    ).toBe("HTTP 200 · 42 ms");
  });

  it("formats postgres success", () => {
    expect(
      formatProbeResult({ ok: true, status: null, error: null, ms: 7, kind: "postgres" }),
    ).toBe("Postgres OK · 7 ms");
  });

  it("formats http error status", () => {
    expect(
      formatProbeResult({ ok: false, status: 503, error: "Service Unavailable", ms: 10 }),
    ).toBe("HTTP 503 · 10 ms");
  });

  it("formats network error", () => {
    expect(
      formatProbeResult({ ok: false, status: null, error: "fetch failed", ms: 5 }),
    ).toBe("fetch failed · 5 ms");
  });
});

describe("probeResultClassName", () => {
  it("returns ok class for success", () => {
    expect(probeResultClassName({ ok: true, status: 200, error: null, ms: 1 })).toBe("status-ok");
  });
});
