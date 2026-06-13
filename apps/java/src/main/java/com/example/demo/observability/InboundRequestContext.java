package com.example.demo.observability;

/** Current inbound HTTP method/path for outbound relay correlation. */
public final class InboundRequestContext {

  private static final ThreadLocal<String> METHOD = new ThreadLocal<>();
  private static final ThreadLocal<String> PATH = new ThreadLocal<>();

  private InboundRequestContext() {}

  public static void set(String method, String path) {
    METHOD.set(method);
    PATH.set(path);
  }

  public static String method() {
    return METHOD.get();
  }

  public static String path() {
    return PATH.get();
  }

  public static void clear() {
    METHOD.remove();
    PATH.remove();
  }
}
