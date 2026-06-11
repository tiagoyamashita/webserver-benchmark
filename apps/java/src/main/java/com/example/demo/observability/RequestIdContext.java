package com.example.demo.observability;

import java.util.concurrent.atomic.AtomicInteger;

/** Per-request correlation id for logs and Postgres {@code application_name}. */
public final class RequestIdContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<AtomicInteger> LOG_SEQ = new ThreadLocal<>();

  private RequestIdContext() {}

  public static void set(String requestId) {
    CURRENT.set(requestId);
    LOG_SEQ.set(new AtomicInteger(0));
  }

  public static String get() {
    return CURRENT.get();
  }

  /** Monotonic line order within one HTTP request (controller before service before access log). */
  public static int nextLogSeq() {
    AtomicInteger seq = LOG_SEQ.get();
    return seq != null ? seq.incrementAndGet() : 0;
  }

  public static void clear() {
    CURRENT.remove();
    LOG_SEQ.remove();
  }
}
