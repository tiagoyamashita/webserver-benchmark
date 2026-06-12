package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.messaging.CreateUserEvent;
import com.example.demo.messaging.CreateUserEventPublisher;
import com.example.demo.observability.DashboardPageContext;
import com.example.demo.observability.RequestIdContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CreateUserKafkaService {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/CreateUserKafkaService.java";
  private static final Logger log = LoggerFactory.getLogger(CreateUserKafkaService.class);

  private final CreateUserEventPublisher createUserEventPublisher;

  public CreateUserKafkaService(CreateUserEventPublisher createUserEventPublisher) {
    this.createUserEventPublisher = createUserEventPublisher;
  }

  /**
   * Validates input and publishes a {@code create-user} Kafka event. Java and Rust consumers insert
   * into Postgres {@code users}.
   */
  public Map<String, Object> publishCreateUserEvent(String name, String email) {
    String trimmedName = name == null ? "" : name.trim();
    String trimmedEmail = email == null ? "" : email.trim();
    String requestId = RequestIdContext.get();

    if (trimmedName.isEmpty()) {
      log.warn(
          "CreateUserKafkaService.publishCreateUserEvent validation failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaService"),
          kv("method", "POST"),
          kv("path", "/dashboard/users/publish-create-user"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-user"),
          kv("reason", "blank-name"));
      return Map.of(
          "ok",
          false,
          "error",
          "name must not be blank",
          "requestId",
          requestId != null ? requestId : "");
    }
    if (trimmedEmail.isEmpty()) {
      log.warn(
          "CreateUserKafkaService.publishCreateUserEvent validation failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaService"),
          kv("method", "POST"),
          kv("path", "/dashboard/users/publish-create-user"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-user"),
          kv("reason", "blank-email"));
      return Map.of(
          "ok",
          false,
          "error",
          "email must not be blank",
          "requestId",
          requestId != null ? requestId : "");
    }

    log.info(
        "CreateUserKafkaService.publishCreateUserEvent publishing",
        kv("source", SOURCE),
        kv("controller", "CreateUserKafkaService"),
        kv("method", "POST"),
        kv("path", "/dashboard/users/publish-create-user"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("kafka_event", CreateUserEvent.EVENT_TYPE),
        kv("name", trimmedName),
        kv("email", trimmedEmail),
        kv("ui_event", "dashboard.ui"),
        kv("action", "publish-create-user"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requestId", requestId);
    out.put("event", CreateUserEvent.EVENT_TYPE);
    out.put("name", trimmedName);
    out.put("email", trimmedEmail);

    try {
      createUserEventPublisher.publishCreateUser(trimmedName, trimmedEmail);
      out.put("ok", true);
      out.put("topic", "create-user");
      log.info(
          "CreateUserKafkaService.publishCreateUserEvent succeeded",
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaService"),
          kv("kafka_event", CreateUserEvent.EVENT_TYPE),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-user"));
      return out;
    } catch (JsonProcessingException e) {
      String error = "failed to serialize create-user event";
      log.error(
          "CreateUserKafkaService.publishCreateUserEvent failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaService"),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-user"));
      out.put("ok", false);
      out.put("error", error);
      return out;
    } catch (RuntimeException e) {
      String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn(
          "CreateUserKafkaService.publishCreateUserEvent failed",
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaService"),
          kv("name", trimmedName),
          kv("email", trimmedEmail),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-user"));
      out.put("ok", false);
      out.put("error", error);
      return out;
    }
  }
}
