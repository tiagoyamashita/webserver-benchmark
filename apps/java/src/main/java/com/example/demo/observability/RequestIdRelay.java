package com.example.demo.observability;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.client.RestClient;

/** Forward inbound API request ids to downstream HTTP services for log correlation. */
public final class RequestIdRelay {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String SESSION_ID_HEADER = "X-Session-ID";
  public static final String ORIGIN_HEADER = "X-Request-Origin";
  public static final String SERVICE = "webserver-benchmark-java";
  private static final Pattern SAFE = Pattern.compile("^[a-zA-Z0-9._-]{8,64}$");

  private RequestIdRelay() {}

  /**
   * Restore correlation id when consuming a Kafka message: prefer JSON {@code requestId}, then
   * {@code X-Request-ID} header; generate only when both are missing or invalid.
   */
  public static String resolveKafkaRequestId(String messageRequestId, String headerRequestId) {
    if (messageRequestId != null && !messageRequestId.isBlank()) {
      String trimmed = messageRequestId.trim();
      if (SAFE.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    if (headerRequestId != null && !headerRequestId.isBlank()) {
      String trimmed = headerRequestId.trim();
      if (SAFE.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString();
  }

  /** Use inbound request id when valid; otherwise generate one for this outbound call. */
  public static String resolveOutboundRequestId() {
    String requestId = RequestIdContext.get();
    if (requestId != null) {
      String trimmed = requestId.trim();
      if (!trimmed.isEmpty() && SAFE.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString();
  }

  public static RestClient.RequestHeadersSpec<?> applyOutbound(
      RestClient.RequestHeadersSpec<?> request) {
    RestClient.RequestHeadersSpec<?> spec =
        request
            .header(REQUEST_ID_HEADER, resolveOutboundRequestId())
            .header(ORIGIN_HEADER, SERVICE);
    String sessionId = PostgresCorrelation.resolveSessionId();
    if (sessionId != null) {
      spec = spec.header(SESSION_ID_HEADER, sessionId);
    }
    return spec;
  }
}
