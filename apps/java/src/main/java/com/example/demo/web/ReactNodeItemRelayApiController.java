package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.validation.CreateItemRequest;
import com.example.demo.observability.DashboardPageContext;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard/items")
public class ReactNodeItemRelayApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/ReactNodeItemRelayApiController.java";
  private static final Logger log = LoggerFactory.getLogger(ReactNodeItemRelayApiController.class);

  private final ReactNodeItemRelayService reactNodeItemRelayService;

  public ReactNodeItemRelayApiController(ReactNodeItemRelayService reactNodeItemRelayService) {
    this.reactNodeItemRelayService = reactNodeItemRelayService;
  }

  /** AJAX: calls React Node {@code POST /api/items} with JSON {@code {"name": "…"}}. */
  @PostMapping(
      value = "/add-via-react-node",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> addViaReactNodeJson(@Valid @RequestBody CreateItemRequest body) {
    String name = body.name();
    log.info(
        "ReactNodeItemRelayApiController.addViaReactNodeJson request received",
        kv("source", SOURCE),
        kv("controller", "ReactNodeItemRelayApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-react-node"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-react-node"));
    Map<String, Object> result = reactNodeItemRelayService.addItemViaReactNode(name);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "ReactNodeItemRelayApiController.addViaReactNodeJson failed",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-react-node"),
          kv("name", name),
          kv("error", result.get("error")),
          kv("reactNodeUrl", result.get("reactNodeUrl")),
          kv("status", result.get("status")));
    } else {
      log.info(
          "ReactNodeItemRelayApiController.addViaReactNodeJson succeeded",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-react-node"),
          kv("name", name),
          kv("reactNodeUrl", result.get("reactNodeUrl")),
          kv("status", result.get("status")));
    }
    return result;
  }
}
