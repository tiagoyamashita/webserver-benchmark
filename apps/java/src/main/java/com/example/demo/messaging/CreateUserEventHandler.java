package com.example.demo.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
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
    String requestId = requestIdHeader != null ? requestIdHeader : "";
    if (payload == null || payload.isBlank()) {
      log.warn(
          "CreateUserEventHandler.handle skipped request_id={}",
          requestId,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", requestIdHeader),
          kv("reason", "empty-payload"));
      return;
    }

    final CreateUserEvent event;
    try {
      event = objectMapper.readValue(payload, CreateUserEvent.class);
    } catch (JsonProcessingException e) {
      log.error(
          "CreateUserEventHandler.handle parse failed request_id={}",
          requestId,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", requestIdHeader),
          kv("error", e.getMessage()));
      return;
    }

    if (!CreateUserEvent.EVENT_TYPE.equals(event.event())) {
      log.warn(
          "CreateUserEventHandler.handle skipped request_id={}",
          requestId,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", requestIdHeader),
          kv("kafka_event", event.event()),
          kv("reason", "unexpected-event-type"));
      return;
    }

    String effectiveRequestId =
        event.requestId() != null && !event.requestId().isBlank()
            ? event.requestId()
            : requestIdHeader;
    String idForLog = effectiveRequestId != null ? effectiveRequestId : "";

    String trimmedName = event.name() == null ? "" : event.name().trim();
    String trimmedEmail = event.email() == null ? "" : event.email().trim();
    if (trimmedName.isEmpty() || trimmedEmail.isEmpty()) {
      log.warn(
          "CreateUserEventHandler.handle validation failed request_id={}",
          idForLog,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("reason", "blank-name-or-email"));
      return;
    }

    log.info(
        "CreateUserEventHandler.handle received request_id={}",
        idForLog,
        kv("source", SOURCE),
        kv("controller", "CreateUserEventHandler"),
        kv("request_id", effectiveRequestId),
        kv("kafka_event", event.event()),
        kv("name", trimmedName),
        kv("email", trimmedEmail));

    try {
      User saved = users.save(new User(trimmedName, trimmedEmail));
      log.info(
          "CreateUserEventHandler.handle succeeded request_id={}",
          idForLog,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
          kv("id", saved.getId()),
          kv("name", saved.getName()),
          kv("email", saved.getEmail()));
    } catch (RuntimeException e) {
      log.error(
          "CreateUserEventHandler.handle failed request_id={}",
          idForLog,
          kv("source", SOURCE),
          kv("controller", "CreateUserEventHandler"),
          kv("request_id", effectiveRequestId),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("error", e.getMessage()));
    }
  }
}
