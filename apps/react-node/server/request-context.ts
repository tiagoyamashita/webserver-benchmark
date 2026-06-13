import { AsyncLocalStorage } from "node:async_hooks";

type RequestContext = {
  sessionId?: string;
  requestId?: string;
  inboundMethod?: string;
  inboundPath?: string;
};

export const requestContext = new AsyncLocalStorage<RequestContext>();

export function currentSessionId(): string | undefined {
  return requestContext.getStore()?.sessionId;
}

export function currentRequestId(): string | undefined {
  return requestContext.getStore()?.requestId;
}

export function currentInboundMethod(): string | undefined {
  return requestContext.getStore()?.inboundMethod;
}

export function currentInboundPath(): string | undefined {
  return requestContext.getStore()?.inboundPath;
}
