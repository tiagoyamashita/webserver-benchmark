package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.observability.DashboardPageContext;
import com.example.demo.observability.RequestIdContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard/users")
public class CreateUserKafkaApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/CreateUserKafkaApiController.java";
  private static final Logger log = LoggerFactory.getLogger(CreateUserKafkaApiController.class);

  private final CreateUserKafkaService createUserKafkaService;

  public CreateUserKafkaApiController(CreateUserKafkaService createUserKafkaService) {
    this.createUserKafkaService = createUserKafkaService;
  }

  /**
   * AJAX from dashboard: publishes a {@code create-user} Kafka event. Java and Rust Kafka consumers
   * insert into Postgres {@code users}.
   */
  @PostMapping(value = "/publish-create-user", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> publishCreateUser(
      @RequestParam("name") String name, @RequestParam("email") String email) {
    String requestId = RequestIdContext.get();
    String idForLog = requestId != null ? requestId : "";
    log.info(
        "CreateUserKafkaApiController.publishCreateUser request received request_id={}",
        idForLog,
        kv("source", SOURCE),
        kv("controller", "CreateUserKafkaApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/users/publish-create-user"),
        kv("request_id", requestId),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("email", email),
        kv("ui_event", "dashboard.ui"),
        kv("action", "publish-create-user"));
    Map<String, Object> result = createUserKafkaService.publishCreateUserEvent(name, email);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "CreateUserKafkaApiController.publishCreateUser failed request_id={}",
          idForLog,
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaApiController"),
          kv("request_id", requestId),
          kv("name", name),
          kv("email", email),
          kv("error", result.get("error")));
    } else {
      log.info(
          "CreateUserKafkaApiController.publishCreateUser succeeded request_id={}",
          idForLog,
          kv("source", SOURCE),
          kv("controller", "CreateUserKafkaApiController"),
          kv("request_id", requestId),
          kv("name", name),
          kv("email", email),
          kv("topic", result.get("topic")));
    }
    return result;
  }
}
