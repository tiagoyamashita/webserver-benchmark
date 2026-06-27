import { randomUUID } from "node:crypto";
import type { NextFunction, Request, Response } from "express";

const HEADER = "x-request-id";
const ORIGIN_HEADER = "x-request-origin";
const OUTBOUND_ORIGIN = "webserver-benchmark-react-node";
const SAFE = /^[a-zA-Z0-9._-]{8,64}$/;

declare module "express-serve-static-core" {
  interface Request {
    requestId: string;
  }
}

export function resolveRequestId(header: string | undefined): string {
  const incoming = header?.trim();
  if (incoming && SAFE.test(incoming)) {
    return incoming;
  }
  return randomUUID();
}

/** Reuse inbound/current id when valid; generate when missing (outbound HTTP). */
export function resolveOutboundRequestId(current?: string): string {
  const trimmed = current?.trim();
  if (trimmed && SAFE.test(trimmed)) {
    return trimmed;
  }
  return randomUUID();
}

export function outboundRequestHeaders(current?: string): Record<string, string> {
  return {
    "X-Request-ID": resolveOutboundRequestId(current),
    "X-Request-Origin": OUTBOUND_ORIGIN,
  };
}

export function requestIdMiddleware(req: Request, res: Response, next: NextFunction): void {
  req.requestId = resolveRequestId(req.header(HEADER));
  res.setHeader("X-Request-ID", req.requestId);
  next();
}
