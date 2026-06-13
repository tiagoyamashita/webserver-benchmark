package com.example.demo.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import com.example.demo.observability.RequestIdContext;
import com.example.demo.observability.RequestIdRelay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Inserts {@link User} rows from Kafka {@code create-user} events. */
@Service
public class CreateUserEventHandler {

  private static final String SOURCE =
      "src/main/java/com/example/demo/messaging/CreateUserEventHandler.java";
  private static final Logger log = LoggerFactory.getLogger(CreateUserEventHandler.class);

  private final UserRepository users;
  private final ObjectMapper objectMapper;

  public CreateUserEventHandler(UserRepository users, ObjectMapper objectMapper) {
    this.users = users;
    this.objectMapper = objectMapper;
  }

  public void handle(String payload, String requestIdHeader) {
    if (payload == null || payload.isBlank()) {
      withRequestId(RequestIdRelay.resolveKafkaRequestId(null, requestIdHeader), () ->
          log.warn(
              "CreateUserEventHandler.handle skipped",
              kv("source", SOURCE),
              kv("controller", "CreateUserEventHandler"),
              kv("reason", "empty-payload")));
      return;
    }

    final CreateUserEvent event;
    try {
      event = objectMapper.readValue(payload, CreateUserEvent.class);
    } catch (JsonProcessingException e) {
      withRequestId(RequestIdRelay.resolveKafkaRequestId(null, requestIdHeader), () ->
          log.error(
              "CreateUserEventHandler.handle parse failed",
              kv("source", SOURCE),
              kv("controller", "CreateUserEventHandler"),
              kv("error", e.getMessage())));
      return;
    }

    withRequestId(
        RequestIdRelay.resolveKafkaRequestId(event.requestId(), requestIdHeader),
        () -> handleEvent(event));
  }

  private void handleEvent(CreateUserEvent event) {
    if (!CreateUserEvent.EVENT_TYPE.equals(event.event())) {
      log.warn(
          "CreateUserEventHandler.handle skipped",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("kafka_event", event.event()),
          kv("reason", "unexpected-event-type"));
      return;
    }

    String trimmedName = event.name() == null ? "" : event.name().trim();
    String trimmedEmail = event.email() == null ? "" : event.email().trim();
    if (trimmedName.isEmpty() || trimmedEmail.isEmpty()) {
      log.warn(
          "CreateUserEventHandler.handle validation failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("reason", "blank-name-or-email"));
      return;
    }

    log.info(
        "CreateUserEventHandler.handle received",
        kv("source", SOURCE),
        kv("controller", "CreateUserEventHandler"),
        kv("kafka_event", event.event()),
        kv("name", trimmedName),
        kv("email", trimmedEmail));

    try {
      User saved = users.save(new User(trimmedName, trimmedEmail));
      log.info(
          "CreateUserEventHandler.handle succeeded",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("id", saved.getId()),
          kv("name", saved.getName()),
          kv("email", saved.getEmail()));
    } catch (RuntimeException e) {
      log.error(
          "CreateUserEventHandler.handle failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("error", e.getMessage()));
    }
  }

  private static void withRequestId(String requestId, Runnable action) {
    RequestIdContext.set(requestId);
    try {
      action.run();
    } finally {
      RequestIdContext.clear();
    }
  }
}
