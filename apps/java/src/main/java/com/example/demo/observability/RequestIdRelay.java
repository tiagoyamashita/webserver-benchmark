package com.example.demo.observability;

import org.springframework.web.client.RestClient;

/** Forward inbound API request ids to downstream HTTP services for log correlation. */
public final class RequestIdRelay {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String ORIGIN_HEADER = "X-Request-Origin";
  public static final String SERVICE = "exercises-java";

  private RequestIdRelay() {}

  public static void applyOutbound(RestClient.RequestHeadersSpec<?> request) {
    String requestId = RequestIdContext.get();
    if (requestId != null) {
      request.header(REQUEST_ID_HEADER, requestId);
      request.header(ORIGIN_HEADER, SERVICE);
    }
  }
}
