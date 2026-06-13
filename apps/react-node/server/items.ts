import {
  logOutboundFailure,
  logOutboundRequest,
  logOutboundResponse,
  resolveResponseError,
} from "./outbound-http-logging.js";
import { outboundRequestHeaders } from "./request-id.js";

export type Item = {
  id: number;
  name: string;
  createdAt: string;
};

const RELAY_TARGET = "exercises-java";

function itemsBaseUrl(): string {
  const value = (process.env.ITEMS_BASE_URL ?? process.env.PROBE_JAVA_URL ?? "http://127.0.0.1:8080").trim();
  return value.replace(/\/$/, "");
}

export async function fetchItems(
  fetchImpl: typeof fetch = fetch,
  requestId?: string,
): Promise<Item[]> {
  const url = `${itemsBaseUrl()}/api/items`;
  const start = performance.now();
  logOutboundRequest("GET", url, RELAY_TARGET, undefined, requestId);
  let loggedResponse = false;
  try {
    const response = await fetchImpl(url, {
      method: "GET",
      headers: outboundRequestHeaders(requestId),
      redirect: "follow",
      signal: AbortSignal.timeout(15_000),
    });
    const rawBody = await response.text();
    const ms = Math.round(performance.now() - start);
    logOutboundResponse(
      "GET",
      url,
      response.status,
      ms,
      RELAY_TARGET,
      response.headers,
      rawBody,
      requestId,
    );
    loggedResponse = true;
    if (!response.ok) {
      throw new Error(
        resolveResponseError(response.status, rawBody) ??
          response.statusText ??
          `HTTP ${response.status}`,
      );
    }
    return JSON.parse(rawBody) as Item[];
  } catch (error) {
    if (!loggedResponse) {
      const ms = Math.round(performance.now() - start);
      const message = error instanceof Error ? error.message : String(error);
      logOutboundFailure("GET", url, ms, RELAY_TARGET, message, requestId);
    }
    throw error instanceof Error ? error : new Error(String(error));
  }
}

export async function createItem(
  name: string,
  fetchImpl: typeof fetch = fetch,
  requestId?: string,
): Promise<Item> {
  const url = `${itemsBaseUrl()}/api/items`;
  const requestBody = { name };
  const start = performance.now();
  logOutboundRequest("POST", url, RELAY_TARGET, requestBody, requestId);
  let loggedResponse = false;
  try {
    const response = await fetchImpl(url, {
      method: "POST",
      headers: {
        ...outboundRequestHeaders(requestId),
        "Content-Type": "application/json",
      },
      body: JSON.stringify(requestBody),
      redirect: "follow",
      signal: AbortSignal.timeout(15_000),
    });
    const rawBody = await response.text();
    const ms = Math.round(performance.now() - start);
    logOutboundResponse(
      "POST",
      url,
      response.status,
      ms,
      RELAY_TARGET,
      response.headers,
      rawBody,
      requestId,
    );
    loggedResponse = true;
    if (!response.ok) {
      throw new Error(
        resolveResponseError(response.status, rawBody) ??
          response.statusText ??
          `HTTP ${response.status}`,
      );
    }
    return JSON.parse(rawBody) as Item;
  } catch (error) {
    if (!loggedResponse) {
      const ms = Math.round(performance.now() - start);
      const message = error instanceof Error ? error.message : String(error);
      logOutboundFailure("POST", url, ms, RELAY_TARGET, message, requestId);
    }
    throw error instanceof Error ? error : new Error(String(error));
  }
}
