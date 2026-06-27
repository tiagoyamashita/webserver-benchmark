package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.observability.DashboardPageContext;
import com.example.demo.observability.OutboundHttpLogger;
import com.example.demo.observability.RequestIdContext;
import com.example.demo.observability.RequestIdRelay;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ReactNodeItemRelayService {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/ReactNodeItemRelayService.java";
  private static final Logger log = LoggerFactory.getLogger(ReactNodeItemRelayService.class);
  private static final String RELAY_TARGET = "webserver-benchmark-react-node";

  private final RestClient restClient;
  private final StackPingProperties properties;
  private final ObjectMapper objectMapper;

  public ReactNodeItemRelayService(
      @Qualifier("stackPingRestClient") RestClient stackPingRestClient,
      StackPingProperties properties,
      ObjectMapper objectMapper) {
    this.restClient = stackPingRestClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * Calls React Node {@code POST /api/items} with JSON {@code {"name": "…"}}; React Node inserts
   * into Postgres {@code items} (Flyway schema).
   */
  public Map<String, Object> addItemViaReactNode(String name) {
    String trimmed = name == null ? "" : name.trim();
    String requestId = RequestIdContext.get();
    if (trimmed.isEmpty()) {
      log.warn(
          "ReactNodeItemRelayService.addItemViaReactNode validation failed",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayService"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-react-node"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-react-node"),
          kv("reason", "blank-name"));
      return Map.of(
          "ok",
          false,
          "error",
          "name must not be blank",
          "requestId",
          requestId != null ? requestId : "");
    }
    log.info(
        "ReactNodeItemRelayService.addItemViaReactNode calling React Node",
        kv("source", SOURCE),
        kv("controller", "ReactNodeItemRelayService"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-react-node"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("relay_target", RELAY_TARGET),
        kv("relay_origin", RequestIdRelay.SERVICE),
        kv("name", trimmed),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-react-node"));
    String base = properties.getReactNodeBaseUrl().trim().replaceAll("/+$", "");
    URI uri = URI.create(base + "/api/items");
    OutboundHttpLogger.logRequest("POST", uri, RELAY_TARGET, Map.of("name", trimmed));
    long start = System.nanoTime();
    try {
      ResponseEntity<String> res =
          RequestIdRelay
              .applyOutbound(
                  restClient
                      .post()
                      .uri(uri)
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(Map.of("name", trimmed)))
              .retrieve()
              .toEntity(String.class);
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String rawBody = res.getBody() != null ? res.getBody() : "";
      OutboundHttpLogger.logResponse(
          "POST", uri, res.getStatusCode().value(), ms, RELAY_TARGET, res.getHeaders(), rawBody);
      Map<String, Object> out = new LinkedHashMap<>();
      boolean ok = res.getStatusCode().is2xxSuccessful();
      out.put("ok", ok);
      out.put("requestId", requestId);
      out.put("reactNodeUrl", uri.toString());
      out.put("status", res.getStatusCode().value());
      out.put("body", rawBody);
      parseJsonBody(rawBody).ifPresent(reactNode -> out.put("reactNode", reactNode));
      if (ok) {
        log.info(
            "ReactNodeItemRelayService.addItemViaReactNode succeeded",
            kv("source", SOURCE),
            kv("controller", "ReactNodeItemRelayService"),
            kv("relay_target", RELAY_TARGET),
            kv("relay_origin", RequestIdRelay.SERVICE),
            kv("name", trimmed),
            kv("reactNodeStatus", res.getStatusCode().value()),
            kv("reactNodeUrl", uri.toString()),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-react-node"));
      } else {
        String responseError =
            OutboundHttpLogger.resolveResponseError(res.getStatusCode().value(), rawBody);
        log.warn(
            "ReactNodeItemRelayService.addItemViaReactNode failed",
            kv("source", SOURCE),
            kv("controller", "ReactNodeItemRelayService"),
            kv("name", trimmed),
            kv("reactNodeStatus", res.getStatusCode().value()),
            kv("reactNodeUrl", uri.toString()),
            kv("error", responseError),
            kv("reactNodeBody", rawBody),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-react-node"));
      }
      return out;
    } catch (RestClientResponseException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String rawBody = e.getResponseBodyAsString();
      String error = OutboundHttpLogger.resolveResponseError(e.getStatusCode().value(), rawBody);
      OutboundHttpLogger.logResponse(
          "POST",
          uri,
          e.getStatusCode().value(),
          ms,
          RELAY_TARGET,
          e.getResponseHeaders(),
          rawBody);
      log.warn(
          "ReactNodeItemRelayService.addItemViaReactNode failed",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayService"),
          kv("name", trimmed),
          kv("reactNodeStatus", e.getStatusCode().value()),
          kv("reactNodeUrl", uri.toString()),
          kv("error", error),
          kv("reactNodeBody", rawBody),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-react-node"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "reactNodeUrl",
          uri.toString(),
          "status",
          e.getStatusCode().value(),
          "error",
          error);
    } catch (RestClientException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      OutboundHttpLogger.logFailure("POST", uri, ms, RELAY_TARGET, error);
      log.warn(
          "ReactNodeItemRelayService.addItemViaReactNode failed",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayService"),
          kv("name", trimmed),
          kv("reactNodeUrl", uri.toString()),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-react-node"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "reactNodeUrl",
          uri.toString(),
          "error",
          error);
    }
  }

  private java.util.Optional<Map<String, Object>> parseJsonBody(String rawBody) {
    if (rawBody == null || rawBody.isBlank()) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(
          objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {}));
    } catch (Exception ignored) {
      return java.util.Optional.empty();
    }
  }
}
