package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RustItemRelayService {

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
    if (trimmed.isEmpty()) {
      log.warn(
          "Dashboard UI add-via-rust rejected",
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-rust"),
          kv("ok", false),
          kv("reason", "blank-name"));
      return Map.of("ok", false, "error", "name must not be blank");
    }
    log.info(
        "Dashboard UI button click",
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-rust"),
        kv("itemName", trimmed));
    String base = properties.getRustBaseUrl().trim().replaceAll("/+$", "");
    URI uri =
        UriComponentsBuilder.fromUriString(base + "/api/items")
            .queryParam("name", trimmed)
            .encode(StandardCharsets.UTF_8)
            .build()
            .toUri();
    try {
      ResponseEntity<String> res =
          restClient.post().uri(uri).retrieve().toEntity(String.class);
      String rawBody = res.getBody() != null ? res.getBody() : "";
      Map<String, Object> out = new LinkedHashMap<>();
      boolean ok = res.getStatusCode().is2xxSuccessful();
      out.put("ok", ok);
      out.put("rustUrl", uri.toString());
      out.put("status", res.getStatusCode().value());
      out.put("body", rawBody);
      parseRustJsonBody(rawBody).ifPresent(rust -> out.put("rust", rust));
      if (ok) {
        log.info(
            "Dashboard UI add-via-rust completed",
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-rust"),
            kv("itemName", trimmed),
            kv("ok", true),
            kv("rustStatus", res.getStatusCode().value()),
            kv("rustUrl", uri.toString()));
      } else {
        log.warn(
            "Dashboard UI add-via-rust failed",
            kv("ui_event", "dashboard.ui"),
            kv("action", "add-item-via-rust"),
            kv("itemName", trimmed),
            kv("ok", false),
            kv("rustStatus", res.getStatusCode().value()),
            kv("rustUrl", uri.toString()));
      }
      return out;
    } catch (RestClientException e) {
      String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn(
          "Dashboard UI add-via-rust failed",
          kv("ui_event", "dashboard.ui"),
          kv("action", "add-item-via-rust"),
          kv("itemName", trimmed),
          kv("ok", false),
          kv("rustUrl", uri.toString()),
          kv("error", error));
      return Map.of(
          "ok",
          false,
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
