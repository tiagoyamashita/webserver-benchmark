/** Structured controller logging (see .cursor/skills/controller-logging/). */

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
  const extra = { source, controller: handler, ...fields };
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

export function logReceived(
  handler: string,
  source: string,
  method: string,
  path: string,
  fields?: Fields,
): void {
  emit("INFO", `${handler} request received`, handler, source, { method, path, ...fields });
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
