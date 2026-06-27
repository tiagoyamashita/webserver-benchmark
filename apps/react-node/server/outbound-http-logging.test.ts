import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { requestContext } from "./request-context.js";
import {
  logOutboundRequest,
  logOutboundResponse,
  resolveResponseError,
} from "./outbound-http-logging.js";

describe("outbound-http-logging", () => {
  beforeEach(() => {
    vi.stubEnv("WEBSERVER_BENCHMARK_OBSERVABILITY", "true");
    vi.stubEnv("LOG_PATH", "logs");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("resolveResponseError reads JSON error field", () => {
    expect(resolveResponseError(502, '{"error":"downstream unavailable"}')).toBe(
      "downstream unavailable",
    );
  });

  it("logOutboundRequest includes origin_path from request context", () => {
    const infoSpy = vi.spyOn(console, "info").mockImplementation(() => {});
    requestContext.run(
      {
        requestId: "req-12345678",
        inboundMethod: "POST",
        inboundPath: "/java/api/items",
      },
      () => {
        logOutboundRequest(
          "POST",
          "http://java:8080/api/items",
          "webserver-benchmark-java",
          { name: "Widget" },
          "req-12345678",
        );
      },
    );
    expect(infoSpy).toHaveBeenCalled();
    const line = JSON.parse(String(infoSpy.mock.calls[0]?.[0])) as Record<string, unknown>;
    expect(line.logger).toBe("http.client");
    expect(line.phase).toBe("outbound_request");
    expect(line.origin_path).toBe("/java/api/items");
    expect(line.origin_method).toBe("POST");
    expect(line.relay_target).toBe("webserver-benchmark-java");
    expect(line.request_id).toBe("req-12345678");
    infoSpy.mockRestore();
  });

  it("logOutboundResponse marks non-2xx as WARN", () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    logOutboundResponse(
      "GET",
      "http://java:8080/api/items",
      503,
      12,
      "webserver-benchmark-java",
      new Headers({ "content-type": "application/json" }),
      '{"error":"Postgres not configured"}',
      "req-12345678",
    );
    expect(warnSpy).toHaveBeenCalled();
    const line = JSON.parse(String(warnSpy.mock.calls[0]?.[0])) as Record<string, unknown>;
    expect(line.level).toBe("WARN");
    expect(line.phase).toBe("outbound_response");
    expect(line.error).toBe("Postgres not configured");
    warnSpy.mockRestore();
  });
});
