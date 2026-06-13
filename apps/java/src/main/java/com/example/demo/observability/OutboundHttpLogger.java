package com.example.demo.observability;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.auth.SessionContext;
import com.example.demo.auth.SharedSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
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
    String requestId = resolveRequestIdForLog();
    String sessionId = resolveSessionId();
    boolean ok = status >= 200 && status < 300;
    Object[] fields =
        buildFields(
            method,
            uri,
            status,
            ms,
            relayTarget,
            requestId,
            sessionId,
            responseHeaders,
            responseBody,
            null);
    if (ok) {
      log.info(
          "{} {} {} outbound response request_id={} session_id={}",
          method,
          pathWithQuery(uri),
          status,
          requestId,
          sessionId != null ? sessionId : "",
          fields);
    } else {
      String responseError = resolveResponseError(status, responseBody);
      log.warn(
          "{} {} {} outbound response request_id={} session_id={} error={}",
          method,
          pathWithQuery(uri),
          status,
          requestId,
          sessionId != null ? sessionId : "",
          responseError != null ? responseError : "",
          fields);
    }
  }

  public static void logFailure(String method, URI uri, long ms, String relayTarget, String error) {
    String requestId = resolveRequestIdForLog();
    String sessionId = resolveSessionId();
    Object[] fields =
        buildFields(method, uri, null, ms, relayTarget, requestId, sessionId, null, null, error);
    log.warn(
        "{} {} outbound failed request_id={} session_id={}",
        method,
        pathWithQuery(uri),
        requestId,
        sessionId != null ? sessionId : "",
        fields);
  }

  private static Object[] buildFields(
      String method,
      URI uri,
      Integer status,
      long ms,
      String relayTarget,
      String requestId,
      String sessionId,
      HttpHeaders responseHeaders,
      String responseBody,
      String error) {
    List<Object> fields = new ArrayList<>();
    fields.add(kv("method", method));
    fields.add(kv("path", pathWithQuery(uri)));
    if (status != null) {
      fields.add(kv("status", status));
    }
    fields.add(kv("ms", ms));
    fields.add(kv("request_id", requestId));
    if (sessionId != null) {
      fields.add(kv("session_id", sessionId));
    }
    fields.add(kv("phase", status != null ? "outbound_response" : "outbound_failed"));
    fields.add(kv("relay_target", relayTarget));
    fields.add(kv("relay_origin", RequestIdRelay.SERVICE));
    if (responseHeaders != null) {
      fields.add(kv("headers", responseHeaders(responseHeaders)));
    }
    if (responseBody != null) {
      fields.add(kv("body", body(responseBody)));
    }
    if (status != null && (status < 200 || status >= 300)) {
      String responseError = resolveResponseError(status, responseBody);
      if (responseError != null) {
        fields.add(kv("error", responseError));
      }
    } else if (error != null) {
      fields.add(kv("error", error));
    }
    return fields.toArray();
  }

  /** Extract a replay-friendly error from a non-2xx response body. */
  public static String resolveResponseError(int status, String responseBody) {
    if (status >= 200 && status < 300) {
      return null;
    }
    if (responseBody != null && !responseBody.isBlank()) {
      Map<String, Object> parsed = body(responseBody);
      Object err = parsed.get("error");
      if (err != null && !String.valueOf(err).isBlank()) {
        return String.valueOf(err);
      }
      Object message = parsed.get("message");
      if (message != null && !String.valueOf(message).isBlank()) {
        return String.valueOf(message);
      }
      Object raw = parsed.get("_raw");
      if (raw != null && !String.valueOf(raw).isBlank()) {
        return String.valueOf(raw);
      }
      if (!parsed.isEmpty()) {
        return parsed.toString();
      }
    }
    return "HTTP " + status;
  }

  /** Prefer inbound dashboard request id; fall back to the outbound relay id. */
  private static String resolveRequestIdForLog() {
    String inbound = RequestIdContext.get();
    if (inbound != null && !inbound.isBlank()) {
      return inbound;
    }
    return RequestIdRelay.resolveOutboundRequestId();
  }

  private static String resolveSessionId() {
    SharedSession session = SessionContext.get();
    if (session == null) {
      return null;
    }
    String sessionId = session.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    return sessionId;
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
