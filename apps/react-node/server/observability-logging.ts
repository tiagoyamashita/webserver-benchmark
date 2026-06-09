import fs from "node:fs";
import path from "node:path";

const SERVICE = "exercises-react-node";
const LOG_FILE = "demo-app.json.log";

let configured = false;
let logStream: fs.WriteStream | null = null;

export function observabilityEnabled(): boolean {
  const value = (process.env.EXERCISES_OBSERVABILITY ?? "").trim().toLowerCase();
  return value === "1" || value === "true" || value === "yes";
}

function logDir(): string {
  return process.env.LOG_PATH ?? "logs";
}

function ensureStream(): fs.WriteStream | null {
  if (!observabilityEnabled()) {
    return null;
  }
  if (!logStream) {
    const dir = logDir();
    fs.mkdirSync(dir, { recursive: true });
    logStream = fs.createWriteStream(path.join(dir, LOG_FILE), {
      flags: "a",
      encoding: "utf-8",
    });
  }
  return logStream;
}

export function writeLog(
  level: string,
  message: string,
  extra?: Record<string, unknown>,
  logger: string = SERVICE,
): void {
  const stream = ensureStream();
  if (!stream) {
    return;
  }
  const payload = {
    "@timestamp": new Date().toISOString(),
    level,
    logger,
    message,
    service: SERVICE,
    ...extra,
  };
  stream.write(`${JSON.stringify(payload)}\n`);
}

function mirrorConsole(): void {
  const methods = ["log", "info", "warn", "error"] as const;
  for (const method of methods) {
    const original = console[method].bind(console);
    console[method] = (...args: unknown[]) => {
      original(...args);
      const message = args
        .map((arg) => (typeof arg === "string" ? arg : JSON.stringify(arg)))
        .join(" ");
      const level =
        method === "error" ? "ERROR" : method === "warn" ? "WARN" : "INFO";
      writeLog(level, message, undefined, "console");
    };
  }
}

/** Mirrors Python configure_observability_logging — JSON lines at ${LOG_PATH}/demo-app.json.log */
export function configureObservabilityLogging(): void {
  if (!observabilityEnabled() || configured) {
    return;
  }
  configured = true;
  mirrorConsole();
  writeLog("INFO", "react-node observability JSON logging enabled");
}

/** @deprecated use configureObservabilityLogging */
export function installObservabilityLogging(): void {
  configureObservabilityLogging();
}
