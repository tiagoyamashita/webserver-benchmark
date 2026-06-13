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
public class PythonItemRelayApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/PythonItemRelayApiController.java";
  private static final Logger log = LoggerFactory.getLogger(PythonItemRelayApiController.class);

  private final PythonItemRelayService pythonItemRelayService;

  public PythonItemRelayApiController(PythonItemRelayService pythonItemRelayService) {
    this.pythonItemRelayService = pythonItemRelayService;
  }

  /** AJAX: calls Python {@code POST /api/items} and returns JSON for display. */
  @PostMapping(value = "/add-via-python", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> addViaPythonJson(@RequestParam("name") String name) {
    log.info(
        "PythonItemRelayApiController.addViaPythonJson request received",
        kv("source", SOURCE),
        kv("controller", "PythonItemRelayApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-python"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-python"));
    Map<String, Object> result = pythonItemRelayService.addItemViaPython(name);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "PythonItemRelayApiController.addViaPythonJson failed",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-python"),
          kv("name", name),
          kv("error", result.get("error")),
          kv("pythonUrl", result.get("pythonUrl")),
          kv("status", result.get("status")));
    } else {
      log.info(
          "PythonItemRelayApiController.addViaPythonJson succeeded",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/items/add-via-python"),
          kv("name", name),
          kv("pythonUrl", result.get("pythonUrl")),
          kv("status", result.get("status")));
    }
    return result;
  }
}
