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
public class PythonItemRelayService {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/PythonItemRelayService.java";
  private static final Logger log = LoggerFactory.getLogger(PythonItemRelayService.class);
  private static final String RELAY_TARGET = "exercises-python";

  private final RestClient restClient;
  private final StackPingProperties properties;
  private final ObjectMapper objectMapper;

  public PythonItemRelayService(
      @Qualifier("stackPingRestClient") RestClient stackPingRestClient,
      StackPingProperties properties,
      ObjectMapper objectMapper) {
    this.restClient = stackPingRestClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * Calls Python {@code POST /api/items} with JSON {@code {"name": "…"}}; Python inserts into
   * Postgres {@code items} (Flyway schema).
   */
  public Map<String, Object> addItemViaPython(String name) {
    String trimmed = name == null ? "" : name.trim();
    String requestId = RequestIdContext.get();
    if (trimmed.isEmpty()) {
      log.warn(
          "PythonItemRelayService.addItemViaPython validation failed",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayService"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-python"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-python"),
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
        "PythonItemRelayService.addItemViaPython calling Python",
        kv("source", SOURCE),
        kv("controller", "PythonItemRelayService"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-python"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("relay_target", RELAY_TARGET),
        kv("relay_origin", RequestIdRelay.SERVICE),
        kv("name", trimmed),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-python"));
    String base = properties.getPythonBaseUrl().trim().replaceAll("/+$", "");
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
      out.put("pythonUrl", uri.toString());
      out.put("status", res.getStatusCode().value());
      out.put("body", rawBody);
      parseJsonBody(rawBody).ifPresent(python -> out.put("python", python));
      if (ok) {
        log.info(
            "PythonItemRelayService.addItemViaPython succeeded",
            kv("source", SOURCE),
            kv("controller", "PythonItemRelayService"),
            kv("relay_target", RELAY_TARGET),
            kv("relay_origin", RequestIdRelay.SERVICE),
            kv("name", trimmed),
            kv("pythonStatus", res.getStatusCode().value()),
            kv("pythonUrl", uri.toString()),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-python"));
      } else {
        String responseError = OutboundHttpLogger.resolveResponseError(res.getStatusCode().value(), rawBody);
        log.warn(
            "PythonItemRelayService.addItemViaPython failed",
            kv("source", SOURCE),
            kv("controller", "PythonItemRelayService"),
            kv("name", trimmed),
            kv("pythonStatus", res.getStatusCode().value()),
            kv("pythonUrl", uri.toString()),
            kv("error", responseError),
            kv("pythonBody", rawBody),
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-python"));
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
          "PythonItemRelayService.addItemViaPython failed",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayService"),
          kv("name", trimmed),
          kv("pythonStatus", e.getStatusCode().value()),
          kv("pythonUrl", uri.toString()),
          kv("error", error),
          kv("pythonBody", rawBody),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-python"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "pythonUrl",
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
          "PythonItemRelayService.addItemViaPython failed",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayService"),
          kv("name", trimmed),
          kv("pythonUrl", uri.toString()),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-python"));
      return Map.of(
          "ok",
          false,
          "requestId",
          requestId != null ? requestId : "",
          "pythonUrl",
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
