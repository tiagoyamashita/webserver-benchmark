package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.config.KafkaAppProperties;
import com.example.demo.messaging.CreateItemEvent;
import com.example.demo.messaging.CreateItemEventPublisher;
import com.example.demo.observability.DashboardPageContext;
import com.example.demo.observability.RequestIdContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CreateItemKafkaService {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/CreateItemKafkaService.java";
  private static final Logger log = LoggerFactory.getLogger(CreateItemKafkaService.class);

  private final CreateItemEventPublisher createItemEventPublisher;
  private final KafkaAppProperties kafkaAppProperties;

  public CreateItemKafkaService(
      CreateItemEventPublisher createItemEventPublisher, KafkaAppProperties kafkaAppProperties) {
    this.createItemEventPublisher = createItemEventPublisher;
    this.kafkaAppProperties = kafkaAppProperties;
  }

  /** Validates input and publishes a {@code create-item} Kafka event for the Python consumer. */
  public Map<String, Object> publishCreateItemEvent(String name) {
    String trimmedName = name == null ? "" : name.trim();
    String requestId = RequestIdContext.get();

    if (trimmedName.isEmpty()) {
      log.warn(
          "CreateItemKafkaService.publishCreateItemEvent validation failed",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaService"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/publish-create-item"),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-item"),
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
        "CreateItemKafkaService.publishCreateItemEvent publishing",
        kv("source", SOURCE),
        kv("controller", "CreateItemKafkaService"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/publish-create-item"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("kafka_event", CreateItemEvent.EVENT_TYPE),
        kv("name", trimmedName),
        kv("ui_event", "dashboard.ui"),
        kv("action", "publish-create-item"));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requestId", requestId);
    out.put("event", CreateItemEvent.EVENT_TYPE);
    out.put("name", trimmedName);
    out.put("topic", kafkaAppProperties.getCreateItemTopic());
    out.put("consumer", "python");

    try {
      createItemEventPublisher.publishCreateItem(trimmedName);
      out.put("ok", true);
      log.info(
          "CreateItemKafkaService.publishCreateItemEvent succeeded",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaService"),
          kv("kafka_event", CreateItemEvent.EVENT_TYPE),
          kv("name", trimmedName),
          kv("topic", kafkaAppProperties.getCreateItemTopic()),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-item"));
      return out;
    } catch (JsonProcessingException e) {
      String error = "failed to serialize create-item event";
      log.error(
          "CreateItemKafkaService.publishCreateItemEvent failed",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaService"),
          kv("name", trimmedName),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-item"));
      out.put("ok", false);
      out.put("error", error);
      return out;
    } catch (RuntimeException e) {
      String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn(
          "CreateItemKafkaService.publishCreateItemEvent failed",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaService"),
          kv("name", trimmedName),
          kv("error", error),
          kv("ui_event", "dashboard.ui"),
          kv("action", "publish-create-item"));
      out.put("ok", false);
      out.put("error", error);
      return out;
    }
  }
}
