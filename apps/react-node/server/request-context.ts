import { AsyncLocalStorage } from "node:async_hooks";

type RequestContext = {
  sessionId?: string;
};

export const requestContext = new AsyncLocalStorage<RequestContext>();

export function currentSessionId(): string | undefined {
  return requestContext.getStore()?.sessionId;
}
