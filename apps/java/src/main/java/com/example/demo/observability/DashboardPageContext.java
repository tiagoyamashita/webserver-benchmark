package com.example.demo.observability;

/** Active dashboard view from {@code X-Dashboard-Page} (browser session navigation). */
public final class DashboardPageContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private DashboardPageContext() {}

  public static void set(String page) {
    CURRENT.set(page);
  }

  public static String get() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
