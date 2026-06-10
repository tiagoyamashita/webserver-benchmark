package com.example.demo.observability;

/** Per-request correlation id for logs and Postgres {@code application_name}. */
public final class RequestIdContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private RequestIdContext() {}

  public static void set(String requestId) {
    CURRENT.set(requestId);
  }

  public static String get() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
