import fs from "node:fs";
import path from "node:path";

const SERVICE = "exercises-react-node";

export function observabilityEnabled(): boolean {
  const value = (process.env.EXERCISES_OBSERVABILITY ?? "").trim().toLowerCase();
  return value === "1" || value === "true" || value === "yes";
}

function logFilePath(): string {
  const logDir = process.env.LOG_PATH ?? "logs";
  fs.mkdirSync(logDir, { recursive: true });
  return path.join(logDir, "demo-app.json.log");
}

export function writeLog(
  level: string,
  message: string,
  extra?: Record<string, unknown>,
): void {
  if (!observabilityEnabled()) {
    return;
  }
  const payload = {
    "@timestamp": new Date().toISOString(),
    level,
    message,
    service: SERVICE,
    ...extra,
  };
  fs.appendFileSync(logFilePath(), `${JSON.stringify(payload)}\n`, "utf-8");
}

export function installObservabilityLogging(): void {
  if (!observabilityEnabled()) {
    return;
  }
  writeLog("INFO", "react-node observability JSON logging enabled");
}
