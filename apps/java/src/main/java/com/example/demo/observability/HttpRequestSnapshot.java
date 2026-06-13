package com.example.demo.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.util.ContentCachingRequestWrapper;

/** Safe request headers and body maps for structured HTTP access logs. */
public final class HttpRequestSnapshot {

  private static final Set<String> HEADER_ALLOW =
      Set.of(
          "x-request-id",
          "x-request-origin",
          "x-dashboard-page",
          "x-session-id",
          "cookie",
          "authorization",
          "content-type",
          "accept",
          "user-agent",
          "host");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpRequestSnapshot() {}

  public static Map<String, String> headers(HttpServletRequest request) {
    Map<String, String> out = new LinkedHashMap<>();
    for (var names = request.getHeaderNames(); names.hasMoreElements(); ) {
      String name = names.nextElement();
      if (HEADER_ALLOW.contains(name.toLowerCase(Locale.ROOT))) {
        out.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
      }
    }
    return out;
  }

  public static Map<String, Object> urlParams(HttpServletRequest request) {
    String query = request.getQueryString();
    if (query == null || query.isBlank()) {
      return Collections.emptyMap();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (String pair : query.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String key = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, StandardCharsets.UTF_8);
      if (key.isEmpty()) {
        continue;
      }
      String value =
          eq >= 0
              ? URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)
              : "";
      out.put(key, value);
    }
    return out;
  }

  public static Map<String, Object> body(HttpServletRequest request) {
    Map<String, Object> queryKeys = urlParams(request);
    if (request instanceof ContentCachingRequestWrapper wrapper) {
      byte[] buf = wrapper.getContentAsByteArray();
      if (buf.length > 0) {
        try {
          String raw = new String(buf, StandardCharsets.UTF_8).trim();
          if (raw.startsWith("{")) {
            return MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
          }
          if (raw.startsWith("[")) {
            return Map.of("_json", MAPPER.readValue(raw, Object.class));
          }
          if (!raw.isEmpty()) {
            return Map.of("_raw", raw);
          }
        } catch (Exception ignored) {
          return Map.of("_raw", new String(buf, StandardCharsets.UTF_8));
        }
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, values) -> {
              if (values == null || values.length == 0 || queryKeys.containsKey(key)) {
                return;
              }
              out.put(key, values.length == 1 ? values[0] : List.of(values));
            });
    return out.isEmpty() ? Collections.emptyMap() : out;
  }
}
