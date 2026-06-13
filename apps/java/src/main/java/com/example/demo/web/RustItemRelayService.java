package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.observability.DashboardPageContext;
import com.example.demo.observability.OutboundHttpLogger;
import com.example.demo.observability.RequestIdContext;
import com.example.demo.observability.RequestIdRelay;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RustItemRelayService {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/RustItemRelayService.java";
  private static final Logger log = LoggerFactory.getLogger(RustItemRelayService.class);

  private final RestClient restClient;
  private final StackPingProperties properties;
  private final ObjectMapper objectMapper;

  public RustItemRelayService(
      @Qualifier("stackPingRestClient") RestClient stackPingRestClient,
      StackPingProperties properties,
      ObjectMapper objectMapper) {
    this.restClient = stackPingRestClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * Calls Rust {@code POST /api/items?name=…}; Rust inserts into Postgres {@code items} (Flyway schema).
   */
  public Map<String, Object> addItemViaRust(String name) {
    String trimmed = name == null ? "" : name.trim();
    String requestId = RequestIdContext.get();
    if (trimmed.isEmpty()) {
      log.warn(
          "RustItemRelayService.addItemViaRust validation failed",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayService"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-rust"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-rust"),
          kv("reason", "blank-name"));
      return Map.of("ok", false, "error", "name must not be blank", "requestId", requestId != null ? requestId : "");
    }
    log.info(
        "RustItemRelayService.addItemViaRust calling Rust",
        kv("source", SOURCE),
        kv("controller", "RustItemRelayService"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-rust"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("relay_target", "exercises-rust"),
        kv("relay_origin", RequestIdRelay.SERVICE),
        kv("name", trimmed),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-rust"));
    String base = properties.getRustBaseUrl().trim().replaceAll("/+$", "");
    URI uri =
        UriComponentsBuilder.fromUriString(base + "/api/items")
            .queryParam("name", trimmed)
            .encode(StandardCharsets.UTF_8)
            .build()
            .toUri();
    OutboundHttpLogger.logRequest("POST", uri, "exercises-rust", Map.of("name", trimmed));
    long start = System.nanoTime();
    try {
      ResponseEntity<String> res =
          RequestIdRelay.applyOutbound(restClient.post().uri(uri)).retrieve().toEntity(String.class);
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String rawBody = res.getBody() != null ? res.getBody() : "";
      OutboundHttpLogger.logResponse(
          "POST",
          uri,
          res.getStatusCode().value(),
          ms,
          "exercises-rust",
          res.getHeaders(),
          rawBody);
      Map<String, Object> out = new LinkedHashMap<>();
      boolean ok = res.getStatusCode().is2xxSuccessful();
      out.put("ok", ok);
      out.put("requestId", requestId);
      out.put("rustUrl", uri.toString());
      out.put("status", res.getStatusCode().value());
      out.put("body", rawBody);
      parseRustJsonBody(rawBody).ifPresent(rust -> out.put("rust", rust));
      if (ok) {
        log.info(
            "RustItemRelayService.addItemViaRust succeeded",
            kv("source", SOURCE),
            kv("controller", "RustItemRelayService"),
            kv("relay_target", "exercises-rust"),
            kv("relay_origin", RequestIdRelay.SERVICE),
            kv("name", trimmed),
            kv("rustStatus", res.getStatusCode().value()),
            kv("rustUrl", uri.toString()),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-rust"));
      } else {
        String responseError = OutboundHttpLogger.resolveResponseError(res.getStatusCode().value(), rawBody);
        log.warn(
            "RustItemRelayService.addItemViaRust failed",
            kv("source", SOURCE),
            kv("controller", "RustItemRelayService"),
            kv("name", trimmed),
            kv("rustStatus", res.getStatusCode().value()),
            kv("rustUrl", uri.toString()),
            kv("error", responseError),
            kv("rustBody", rawBody),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-rust"));
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
          "exercises-rust",
          e.getResponseHeaders(),
          rawBody);
      log.warn(
          "RustItemRelayService.addItemViaRust failed",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayService"),
          kv("name", trimmed),
          kv("rustStatus", e.getStatusCode().value()),
          kv("rustUrl", uri.toString()),
          kv("error", error),
          kv("rustBody", rawBody),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-rust"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "rustUrl",
          uri.toString(),
          "status",
          e.getStatusCode().value(),
          "error",
          error);
    } catch (RestClientException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      OutboundHttpLogger.logFailure("POST", uri, ms, "exercises-rust", error);
      log.warn(
          "RustItemRelayService.addItemViaRust failed",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayService"),
          kv("name", trimmed),
          kv("rustUrl", uri.toString()),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-rust"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "rustUrl",
          uri.toString(),
          "error",
          error);
    }
  }

  private java.util.Optional<Map<String, Object>> parseRustJsonBody(String rawBody) {
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
