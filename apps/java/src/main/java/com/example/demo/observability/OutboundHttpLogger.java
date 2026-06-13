package com.example.demo.observability;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/** Structured outbound HTTP response lines for Grafana / Kibana correlation. */
public final class OutboundHttpLogger {

  private static final Logger log = LoggerFactory.getLogger("http.client");
  private static final Set<String> HEADER_ALLOW =
      Set.of(
          "x-request-id",
          "x-request-origin",
          "content-type",
          "content-length",
          "server",
          "date",
          "location");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OutboundHttpLogger() {}

  public static void logResponse(
      String method,
      URI uri,
      int status,
      long ms,
      String relayTarget,
      HttpHeaders responseHeaders,
      String responseBody) {
    String requestId = RequestIdRelay.resolveOutboundRequestId();
    String idForLog = requestId;
    boolean ok = status >= 200 && status < 300;
    var fields =
        new Object[] {
          kv("method", method),
          kv("path", pathWithQuery(uri)),
          kv("status", status),
          kv("ms", ms),
          kv("request_id", requestId),
          kv("phase", "outbound_response"),
          kv("relay_target", relayTarget),
          kv("relay_origin", RequestIdRelay.SERVICE),
          kv("headers", responseHeaders(responseHeaders)),
          kv("body", body(responseBody))
        };
    if (ok) {
      log.info(
          "{} {} {} outbound response request_id={}",
          method,
          pathWithQuery(uri),
          status,
          idForLog,
          fields);
    } else {
      log.warn(
          "{} {} {} outbound response request_id={}",
          method,
          pathWithQuery(uri),
          status,
          idForLog,
          fields);
    }
  }

  public static void logFailure(String method, URI uri, long ms, String relayTarget, String error) {
    String requestId = RequestIdRelay.resolveOutboundRequestId();
    String idForLog = requestId;
    log.warn(
        "{} {} outbound failed request_id={}",
        method,
        pathWithQuery(uri),
        idForLog,
        kv("method", method),
        kv("path", pathWithQuery(uri)),
        kv("ms", ms),
        kv("request_id", requestId),
        kv("phase", "outbound_failed"),
        kv("relay_target", relayTarget),
        kv("relay_origin", RequestIdRelay.SERVICE),
        kv("error", error));
  }

  private static String pathWithQuery(URI uri) {
    String path = uri.getRawPath();
    if (path == null || path.isEmpty()) {
      path = "/";
    }
    String query = uri.getRawQuery();
    return query == null || query.isBlank() ? path : path + "?" + query;
  }

  private static Map<String, String> responseHeaders(HttpHeaders headers) {
    if (headers == null || headers.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> out = new LinkedHashMap<>();
    headers.forEach(
        (name, values) -> {
          if (name != null && HEADER_ALLOW.contains(name.toLowerCase(Locale.ROOT))) {
            out.put(name.toLowerCase(Locale.ROOT), String.join(", ", values));
          }
        });
    return out;
  }

  private static Map<String, Object> body(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) {
      return Collections.emptyMap();
    }
    String raw = rawBody.trim();
    try {
      if (raw.startsWith("{")) {
        return MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
      }
      if (raw.startsWith("[")) {
        return Map.of("_json", MAPPER.readValue(raw, Object.class));
      }
    } catch (Exception ignored) {
      // fall through to raw string
    }
    return Map.of("_raw", raw);
  }
}
