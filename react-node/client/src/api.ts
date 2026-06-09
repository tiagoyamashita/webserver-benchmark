import type { Item, ProbeResult } from "./types";

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

export async function fetchItems(): Promise<Item[]> {
  const response = await fetch("/api/items", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(body.error ?? response.statusText);
  }
  return (await response.json()) as Item[];
}

export async function createItem(name: string): Promise<Item> {
  const response = await fetch("/api/items", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    throw new Error(body.error ?? response.statusText);
  }
  return (await response.json()) as Item;
}
