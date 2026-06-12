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
public class RustItemRelayApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/RustItemRelayApiController.java";
  private static final Logger log = LoggerFactory.getLogger(RustItemRelayApiController.class);

  private final RustItemRelayService rustItemRelayService;

  public RustItemRelayApiController(RustItemRelayService rustItemRelayService) {
    this.rustItemRelayService = rustItemRelayService;
  }

  /** AJAX from home page: calls Rust {@code POST /api/items?name=…} and returns JSON for display. */
  @PostMapping(value = "/add-via-rust", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> addViaRustJson(@RequestParam("name") String name) {
    log.info(
        "RustItemRelayApiController.addViaRustJson request received",
        kv("source", SOURCE),
        kv("controller", "RustItemRelayApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-rust"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-rust"));
    Map<String, Object> result = rustItemRelayService.addItemViaRust(name);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "RustItemRelayApiController.addViaRustJson failed",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-rust"),
          kv("name", name),
          kv("error", result.get("error")),
          kv("rustUrl", result.get("rustUrl")),
          kv("status", result.get("status")));
    } else {
      log.info(
          "RustItemRelayApiController.addViaRustJson succeeded",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-rust"),
          kv("name", name),
          kv("rustUrl", result.get("rustUrl")),
          kv("status", result.get("status")));
    }
    return result;
  }
}
