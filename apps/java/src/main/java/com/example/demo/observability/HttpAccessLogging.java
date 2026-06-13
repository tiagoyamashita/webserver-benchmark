package com.example.demo.observability;

import java.util.Set;

/** Skip noisy http.request access lines for routine probe/scrape traffic (log failures only). */
public final class HttpAccessLogging {

  private static final Set<String> QUIET_GET_PATHS = Set.of("/actuator/prometheus");

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
    if (method == null || !"GET".equalsIgnoreCase(method)) {
      return true;
    }
    if (!QUIET_GET_PATHS.contains(requestPathname(path))) {
      return true;
    }
    return status != 200;
  }
}
