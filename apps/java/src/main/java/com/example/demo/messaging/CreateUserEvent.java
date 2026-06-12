package com.example.demo.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON payload published to the {@code create-user} Kafka topic. */
public record CreateUserEvent(
    String event,
    String name,
    String email,
    @JsonProperty("requestId") String requestId) {

  public static final String EVENT_TYPE = "create-user";

  public static CreateUserEvent of(String name, String email, String requestId) {
    return new CreateUserEvent(EVENT_TYPE, name, email, requestId);
  }
}
