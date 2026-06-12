/** Structured controller logging (see .cursor/skills/controller-logging/). */

import type { Request } from "express";

import { requestBody, requestHeaders, requestUrlParams } from "./request-snapshot.js";
import { currentSessionId } from "./request-context.js";
import { writeLog } from "./observability-logging.js";

type Fields = Record<string, unknown>;
type LogLevel = "INFO" | "WARN" | "ERROR" | "TRACE";

function emit(
  level: LogLevel,
  message: string,
  handler: string,
  source: string,
  fields?: Fields,
): void {
  const sessionId = currentSessionId();
  const extra = {
    source,
    controller: handler,
    ...(sessionId ? { session_id: sessionId } : {}),
    ...fields,
  };
  const fileLevel = level === "TRACE" ? "TRACE" : level;
  writeLog(fileLevel, message, extra);
  const line = JSON.stringify({ level: fileLevel, message, ...extra });
  if (level === "ERROR") {
    console.error(line);
  } else if (level === "WARN") {
    console.warn(line);
  } else {
    console.info(line);
  }
}

export function httpRequestFields(req: Request): Fields {
  return {
    headers: requestHeaders(req),
    url_params: requestUrlParams(req),
    body: requestBody(req),
  };
}

export function logReceived(
  handler: string,
  source: string,
  method: string,
  path: string,
  fields?: Fields,
): void {
  emit("INFO", `${handler} request received`, handler, source, { method, path, ...fields });
}

export function logReceivedFromRequest(
  req: Request,
  handler: string,
  source: string,
  method: string,
  path: string,
  fields?: Fields,
): void {
  logReceived(handler, source, method, path, {
    ...httpRequestFields(req),
    ...fields,
  });
}

export function logSucceeded(handler: string, source: string, fields?: Fields): void {
  emit("INFO", `${handler} succeeded`, handler, source, fields);
}

export function logWarn(
  handler: string,
  source: string,
  message: string,
  fields?: Fields,
): void {
  emit("WARN", message, handler, source, fields);
}

export function logError(
  handler: string,
  source: string,
  message: string,
  fields?: Fields,
): void {
  emit("ERROR", message, handler, source, fields);
}

export function logTrace(
  handler: string,
  source: string,
  message: string,
  fields?: Fields,
): void {
  emit("TRACE", message, handler, source, fields);
}
