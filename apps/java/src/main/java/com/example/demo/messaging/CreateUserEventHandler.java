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
      log.warn(
          "CreateUserEventHandler.handle skipped",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", RequestIdRelay.resolveKafkaRequestId(null, requestIdHeader)),
          kv("reason", "empty-payload"));
      return;
    }

    final CreateUserEvent event;
    try {
      event = objectMapper.readValue(payload, CreateUserEvent.class);
    } catch (JsonProcessingException e) {
      log.error(
          "CreateUserEventHandler.handle parse failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", RequestIdRelay.resolveKafkaRequestId(null, requestIdHeader)),
          kv("error", e.getMessage()));
      return;
    }

    String effectiveRequestId =
        RequestIdRelay.resolveKafkaRequestId(event.requestId(), requestIdHeader);
    RequestIdContext.set(effectiveRequestId);
    try {
      handleEvent(event, effectiveRequestId);
    } finally {
      RequestIdContext.clear();
    }
  }

  private void handleEvent(CreateUserEvent event, String effectiveRequestId) {
    if (!CreateUserEvent.EVENT_TYPE.equals(event.event())) {
      log.warn(
          "CreateUserEventHandler.handle skipped",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
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
          kv("request_id", effectiveRequestId),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("reason", "blank-name-or-email"));
      return;
    }

    log.info(
        "CreateUserEventHandler.handle received",
        kv("source", SOURCE),
        kv("controller", "CreateUserEventHandler"),
        kv("request_id", effectiveRequestId),
        kv("kafka_event", event.event()),
        kv("name", trimmedName),
        kv("email", trimmedEmail));

    try {
      User saved = users.save(new User(trimmedName, trimmedEmail));
      log.info(
          "CreateUserEventHandler.handle succeeded",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
          kv("id", saved.getId()),
          kv("name", saved.getName()),
          kv("email", saved.getEmail()));
    } catch (RuntimeException e) {
      log.error(
          "CreateUserEventHandler.handle failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("error", e.getMessage()));
    }
  }
}
