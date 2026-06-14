package com.example.demo.observability;

import java.util.Map;
import java.util.Set;

/** Skip noisy http.request access lines for routine probe/scrape traffic (log failures only). */
public final class HttpAccessLogging {

  private static final Set<String> QUIET_GET_PATHS =
      Set.of("/actuator/prometheus", "/api/observability/health");

  private static final Map<String, Set<Integer>> QUIET_POST_STATUSES =
      Map.of("/api/auth/ensure", Set.of(200));

  private HttpAccessLogging() {}

  static String requestPathname(String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }
    int query = path.indexOf('?');
    return query == -1 ? path : path.substring(0, query);
  }

  /** When false, skip both received and completed http.request lines for this request. */
  public static boolean shouldLogHttpAccess(String method, String path, int status) {
    String pathname = requestPathname(path);
    if (method != null && "GET".equalsIgnoreCase(method)) {
      if (QUIET_GET_PATHS.contains(pathname)) {
        return status != 200;
      }
      return true;
    }
    if (method != null && "POST".equalsIgnoreCase(method)) {
      Set<Integer> quietStatuses = QUIET_POST_STATUSES.get(pathname);
      if (quietStatuses != null) {
        return !quietStatuses.contains(status);
      }
    }
    return true;
  }
}
