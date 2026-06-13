package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.observability.DashboardPageContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard/items")
public class CreateItemKafkaApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/CreateItemKafkaApiController.java";
  private static final Logger log = LoggerFactory.getLogger(CreateItemKafkaApiController.class);

  private final CreateItemKafkaService createItemKafkaService;

  public CreateItemKafkaApiController(CreateItemKafkaService createItemKafkaService) {
    this.createItemKafkaService = createItemKafkaService;
  }

  /**
   * AJAX from dashboard: publishes a {@code create-item} Kafka event. Only the Python consumer
   * inserts into Postgres {@code items}.
   */
  @PostMapping(value = "/publish-create-item", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> publishCreateItem(@RequestParam("name") String name) {
    log.info(
        "CreateItemKafkaApiController.publishCreateItem request received",
        kv("source", SOURCE),
        kv("controller", "CreateItemKafkaApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/publish-create-item"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "publish-create-item"));
    Map<String, Object> result = createItemKafkaService.publishCreateItemEvent(name);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "CreateItemKafkaApiController.publishCreateItem failed",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaApiController"),
          kv("name", name),
          kv("error", result.get("error")));
    } else {
      log.info(
          "CreateItemKafkaApiController.publishCreateItem succeeded",
          kv("source", SOURCE),
          kv("controller", "CreateItemKafkaApiController"),
          kv("name", name),
          kv("topic", result.get("topic")));
    }
    return result;
  }
}
