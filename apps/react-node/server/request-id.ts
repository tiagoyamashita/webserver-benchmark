import { randomUUID } from "node:crypto";
import type { NextFunction, Request, Response } from "express";

const HEADER = "x-request-id";
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

export function requestIdMiddleware(req: Request, res: Response, next: NextFunction): void {
  req.requestId = resolveRequestId(req.header(HEADER));
  res.setHeader("X-Request-ID", req.requestId);
  next();
}
