import type { Request, Response } from "express";
import { describe, expect, it, vi } from "vitest";
import { requireApiSession } from "./require-api-session.js";
import type { AuthState } from "./repository.js";

function mockReq(overrides: Partial<Request> & { headers?: Record<string, string> } = {}): Request {
  const { headers = {}, ...rest } = overrides;
  return {
    header(name: string) {
      return headers[name.toLowerCase()];
    },
    sharedSession: undefined,
    ...rest,
  } as Request;
}

function mockRes(): Response & { statusCode: number; body: unknown } {
  const res = {
    statusCode: 200,
    body: undefined as unknown,
    status(code: number) {
      res.statusCode = code;
      return res;
    },
    json(payload: unknown) {
      res.body = payload;
      return res;
    },
  };
  return res as Response & { statusCode: number; body: unknown };
}

const auth = { config: { cookieName: "webserver_benchmark_session", redisKeyPrefix: "p:", ttlSecs: 3600 } } as AuthState;

describe("requireApiSession", () => {
  it("allows requests when auth is not configured", () => {
    const next = vi.fn();
    requireApiSession(null)(mockReq(), mockRes(), next);
    expect(next).toHaveBeenCalled();
  });

  it("returns 401 when auth is configured and session is missing", () => {
    const next = vi.fn();
    const res = mockRes();
    requireApiSession(auth)(mockReq(), res, next);
    expect(res.statusCode).toBe(401);
    expect(next).not.toHaveBeenCalled();
  });

  it("allows trusted stack relay origin without session", () => {
    const next = vi.fn();
    requireApiSession(auth)(
      mockReq({ headers: { "x-request-origin": "webserver-benchmark-java" } }),
      mockRes(),
      next,
    );
    expect(next).toHaveBeenCalled();
  });
});
