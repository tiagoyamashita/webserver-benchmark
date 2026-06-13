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

  /** Log an outbound HTTP call before it is sent (links relay to originating inbound request). */
  public static void logRequest(
      String method, URI uri, String relayTarget, Map<String, Object> requestBody) {
    String requestId = resolveRequestIdForLog();
    String sessionId = resolveSessionId();
    String originMethod = InboundRequestContext.method();
    String originPath = InboundRequestContext.path();
    String dashboardPage = DashboardPageContext.get();
    Object[] fields =
        buildRequestFields(
            method,
            uri,
            relayTarget,
            requestId,
            sessionId,
            originMethod,
            originPath,
            dashboardPage,
            requestBody);
    logWithFields(
        true,
        "{} {} outbound request request_id={} origin={} {}",
        new Object[] {
          method,
          pathWithQuery(uri),
          requestId,
          originMethod != null ? originMethod : "",
          originPath != null ? originPath : ""
        },
        fields);
  }

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
      logWithFields(
          true,
          "{} {} {} outbound response request_id={} session_id={}",
          new Object[] {
            method,
            pathWithQuery(uri),
            status,
            requestId,
            sessionId != null ? sessionId : ""
          },
          fields);
    } else {
      String responseError = resolveResponseError(status, responseBody);
      logWithFields(
          false,
          "{} {} {} outbound response request_id={} session_id={} error={}",
          new Object[] {
            method,
            pathWithQuery(uri),
            status,
            requestId,
            sessionId != null ? sessionId : "",
            responseError != null ? responseError : ""
          },
          fields);
    }
  }

  public static void logFailure(String method, URI uri, long ms, String relayTarget, String error) {
    String requestId = resolveRequestIdForLog();
    String sessionId = resolveSessionId();
    Object[] fields =
        buildFields(method, uri, null, ms, relayTarget, requestId, sessionId, null, null, error);
    logWithFields(
        false,
        "{} {} outbound failed request_id={} session_id={}",
        new Object[] {
          method, pathWithQuery(uri), requestId, sessionId != null ? sessionId : ""
        },
        fields);
  }

  private static void logWithFields(
      boolean info, String pattern, Object[] messageArgs, Object[] fieldArgs) {
    Object[] args = concat(messageArgs, fieldArgs);
    if (info) {
      logInfoSpread(pattern, args);
    } else {
      logWarnSpread(pattern, args);
    }
  }

  private static void logInfoSpread(String pattern, Object[] args) {
    switch (args.length) {
      case 0 -> log.info(pattern);
      case 1 -> log.info(pattern, args[0]);
      case 2 -> log.info(pattern, args[0], args[1]);
      case 3 -> log.info(pattern, args[0], args[1], args[2]);
      case 4 -> log.info(pattern, args[0], args[1], args[2], args[3]);
      case 5 -> log.info(pattern, args[0], args[1], args[2], args[3], args[4]);
      case 6 -> log.info(pattern, args[0], args[1], args[2], args[3], args[4], args[5]);
      case 7 -> log.info(pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
      case 8 ->
          log.info(pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
      case 9 ->
          log.info(
              pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
      case 10 ->
          log.info(
              pattern,
              args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
      case 11 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10]);
      case 12 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11]);
      case 13 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12]);
      case 14 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13]);
      case 15 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14]);
      case 16 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14],
              args[15]);
      case 17 ->
          log.info(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14],
              args[15],
              args[16]);
      default ->
          throw new IllegalArgumentException("too many structured log arguments: " + args.length);
    }
  }

  private static void logWarnSpread(String pattern, Object[] args) {
    switch (args.length) {
      case 0 -> log.warn(pattern);
      case 1 -> log.warn(pattern, args[0]);
      case 2 -> log.warn(pattern, args[0], args[1]);
      case 3 -> log.warn(pattern, args[0], args[1], args[2]);
      case 4 -> log.warn(pattern, args[0], args[1], args[2], args[3]);
      case 5 -> log.warn(pattern, args[0], args[1], args[2], args[3], args[4]);
      case 6 -> log.warn(pattern, args[0], args[1], args[2], args[3], args[4], args[5]);
      case 7 -> log.warn(pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6]);
      case 8 ->
          log.warn(pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7]);
      case 9 ->
          log.warn(
              pattern, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8]);
      case 10 ->
          log.warn(
              pattern,
              args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9]);
      case 11 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10]);
      case 12 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11]);
      case 13 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12]);
      case 14 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13]);
      case 15 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14]);
      case 16 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14],
              args[15]);
      case 17 ->
          log.warn(
              pattern,
              args[0],
              args[1],
              args[2],
              args[3],
              args[4],
              args[5],
              args[6],
              args[7],
              args[8],
              args[9],
              args[10],
              args[11],
              args[12],
              args[13],
              args[14],
              args[15],
              args[16]);
      default ->
          throw new IllegalArgumentException("too many structured log arguments: " + args.length);
    }
  }

  private static Object[] concat(Object[] first, Object[] second) {
    Object[] out = new Object[first.length + second.length];
    System.arraycopy(first, 0, out, 0, first.length);
    System.arraycopy(second, 0, out, first.length, second.length);
    return out;
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

  private static Object[] buildRequestFields(
      String method,
      URI uri,
      String relayTarget,
      String requestId,
      String sessionId,
      String originMethod,
      String originPath,
      String dashboardPage,
      Map<String, Object> requestBody) {
    List<Object> fields = new ArrayList<>();
    fields.add(kv("method", method));
    fields.add(kv("path", pathWithQuery(uri)));
    fields.add(kv("request_id", requestId));
    if (sessionId != null) {
      fields.add(kv("session_id", sessionId));
    }
    fields.add(kv("phase", "outbound_request"));
    fields.add(kv("relay_target", relayTarget));
    fields.add(kv("relay_origin", RequestIdRelay.SERVICE));
    if (originMethod != null && !originMethod.isBlank()) {
      fields.add(kv("origin_method", originMethod));
    }
    if (originPath != null && !originPath.isBlank()) {
      fields.add(kv("origin_path", originPath));
    }
    if (dashboardPage != null && !dashboardPage.isBlank()) {
      fields.add(kv("dashboard_page", dashboardPage));
    }
    Map<String, String> outboundHeaders = new LinkedHashMap<>();
    outboundHeaders.put("x-request-id", RequestIdRelay.resolveOutboundRequestId());
    outboundHeaders.put("x-request-origin", RequestIdRelay.SERVICE);
    fields.add(kv("headers", outboundHeaders));
    if (requestBody != null && !requestBody.isEmpty()) {
      fields.add(kv("body", requestBody));
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
