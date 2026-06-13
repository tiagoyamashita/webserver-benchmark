package com.example.demo.observability;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;

/** Per-request correlation id for logs and Postgres {@code application_name}. */
public final class RequestIdContext {

  public static final String MDC_REQUEST_ID = "request_id";

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<AtomicInteger> LOG_SEQ = new ThreadLocal<>();

  private RequestIdContext() {}

  public static void set(String requestId) {
    CURRENT.set(requestId);
    LOG_SEQ.set(new AtomicInteger(0));
    if (requestId != null && !requestId.isBlank()) {
      MDC.put(MDC_REQUEST_ID, requestId.trim());
    } else {
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  public static String get() {
    String requestId = CURRENT.get();
    if (requestId != null) {
      return requestId;
    }
    return MDC.get(MDC_REQUEST_ID);
  }

  /** Monotonic line order within one HTTP request (controller before service before access log). */
  public static int nextLogSeq() {
    AtomicInteger seq = LOG_SEQ.get();
    return seq != null ? seq.incrementAndGet() : 0;
  }

  public static void clear() {
    CURRENT.remove();
    LOG_SEQ.remove();
    MDC.remove(MDC_REQUEST_ID);
  }
}
