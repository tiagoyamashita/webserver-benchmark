import type { ProbeResult } from "./types";

export async function probeService(id: string): Promise<ProbeResult> {
  const response = await fetch(`/api/probe/${encodeURIComponent(id)}`, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    return {
      ok: false,
      status: response.status,
      error: body.error ?? response.statusText,
      ms: 0,
    };
  }
  return (await response.json()) as ProbeResult;
}
