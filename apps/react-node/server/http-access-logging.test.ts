import { describe, expect, it } from "vitest";
import { requestPathname, shouldLogHttpAccess } from "./http-access-logging.js";

describe("http-access-logging", () => {
  it("skips GET /api/health when status is 200 or unknown", () => {
    expect(shouldLogHttpAccess("GET", "/api/health", 200)).toBe(false);
    expect(shouldLogHttpAccess("GET", "/api/health")).toBe(false);
  });

  it("logs GET /api/health when status is not 200", () => {
    expect(shouldLogHttpAccess("GET", "/api/health", 503)).toBe(true);
  });

  it("skips GET /metrics when status is 200 or unknown", () => {
    expect(shouldLogHttpAccess("GET", "/metrics", 200)).toBe(false);
    expect(shouldLogHttpAccess("GET", "/metrics")).toBe(false);
    expect(shouldLogHttpAccess("GET", "/metrics/", 200)).toBe(false);
  });

  it("logs GET /metrics when status is not 200", () => {
    expect(shouldLogHttpAccess("GET", "/metrics", 500)).toBe(true);
  });

  it("still logs other routes", () => {
    expect(shouldLogHttpAccess("GET", "/api/items", 200)).toBe(true);
    expect(shouldLogHttpAccess("POST", "/api/health", 200)).toBe(true);
  });

  it("strips query string from path", () => {
    expect(requestPathname("/api/health?verbose=1")).toBe("/api/health");
    expect(shouldLogHttpAccess("GET", "/api/health?verbose=1", 200)).toBe(false);
  });
});
