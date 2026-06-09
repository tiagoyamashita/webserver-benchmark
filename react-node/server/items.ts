export type Item = {
  id: number;
  name: string;
  createdAt: string;
};

function itemsBaseUrl(): string {
  const value = (process.env.ITEMS_BASE_URL ?? process.env.PROBE_JAVA_URL ?? "http://127.0.0.1:8080").trim();
  return value.replace(/\/$/, "");
}

export async function fetchItems(fetchImpl: typeof fetch = fetch): Promise<Item[]> {
  const response = await fetchImpl(`${itemsBaseUrl()}/api/items`, {
    method: "GET",
    redirect: "follow",
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    const body = (await response.text()).trim();
    throw new Error(body || response.statusText || `HTTP ${response.status}`);
  }
  return (await response.json()) as Item[];
}

export async function createItem(
  name: string,
  fetchImpl: typeof fetch = fetch,
): Promise<Item> {
  const response = await fetchImpl(`${itemsBaseUrl()}/api/items`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
    redirect: "follow",
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    const body = (await response.text()).trim();
    throw new Error(body || response.statusText || `HTTP ${response.status}`);
  }
  return (await response.json()) as Item;
}
