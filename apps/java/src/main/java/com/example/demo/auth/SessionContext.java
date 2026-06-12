package com.example.demo.auth;

/** Active shared session for the current HTTP request (set by {@link SessionAuthFilter}). */
public final class SessionContext {

  private static final ThreadLocal<SharedSession> CURRENT = new ThreadLocal<>();

  private SessionContext() {}

  public static void set(SharedSession session) {
    CURRENT.set(session);
  }

  public static SharedSession get() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
