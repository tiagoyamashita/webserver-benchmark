import type { Item, ProbeResult } from "./types";
import { apiRequest } from "./api-request";

export async function probeService(id: string): Promise<ProbeResult> {
  const { headers } = apiRequest();
  const response = await fetch(`/api/probe/${encodeURIComponent(id)}`, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers,
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

export async function fetchItems(): Promise<Item[]> {
  const { headers } = apiRequest();
  const response = await fetch("/api/items", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers,
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(body.error ?? response.statusText);
  }
  return (await response.json()) as Item[];
}

export async function createItem(name: string): Promise<Item> {
  const { headers } = apiRequest({ "Content-Type": "application/json" });
  const response = await fetch("/api/items", {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify({ name }),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(body.error ?? response.statusText);
  }
  return (await response.json()) as Item;
}
