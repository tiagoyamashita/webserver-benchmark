package com.example.demo.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RustItemRelayService {

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
      return Map.of("ok", false, "error", "name must not be blank");
    }
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
      out.put("ok", res.getStatusCode().is2xxSuccessful());
      out.put("rustUrl", uri.toString());
      out.put("status", res.getStatusCode().value());
      out.put("body", rawBody);
      parseRustJsonBody(rawBody).ifPresent(rust -> out.put("rust", rust));
      return out;
    } catch (RestClientException e) {
      return Map.of(
          "ok",
          false,
          "rustUrl",
          uri.toString(),
          "error",
          e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
