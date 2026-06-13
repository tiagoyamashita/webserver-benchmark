package com.example.demo.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON payload published to the {@code create-item} Kafka topic (Python consumer). */
public record CreateItemEvent(
    String event,
    String name,
    @JsonProperty("requestId") String requestId) {

  public static final String EVENT_TYPE = "create-item";

  public static CreateItemEvent of(String name, String requestId) {
    return new CreateItemEvent(EVENT_TYPE, name, requestId);
  }
}
