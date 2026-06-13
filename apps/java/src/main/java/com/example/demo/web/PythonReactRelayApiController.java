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
@RequestMapping("/dashboard/relays")
public class PythonReactRelayApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/PythonReactRelayApiController.java";
  private static final Logger log = LoggerFactory.getLogger(PythonReactRelayApiController.class);

  private final PythonReactRelayService pythonReactRelayService;

  public PythonReactRelayApiController(PythonReactRelayService pythonReactRelayService) {
    this.pythonReactRelayService = pythonReactRelayService;
  }

  /** AJAX: calls Python {@code POST /api/relay/react} (Python → React Node Postgres items). */
  @PostMapping(
      value = "/python-react",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> relayViaPythonToReact(@Valid @RequestBody CreateItemRequest body) {
    String name = body.name();
    log.info(
        "PythonReactRelayApiController.relayViaPythonToReact request received",
        kv("source", SOURCE),
        kv("controller", "PythonReactRelayApiController"),
        kv("method", "POST"),
        kv("path", "/dashboard/relays/python-react"),
        kv("dashboard_page", DashboardPageContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "relay-item-python-react"));
    Map<String, Object> result = pythonReactRelayService.addItemViaPythonReactRelay(name);
    if (Boolean.FALSE.equals(result.get("ok"))) {
      log.warn(
          "PythonReactRelayApiController.relayViaPythonToReact failed",
          kv("source", SOURCE),
          kv("controller", "PythonReactRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/relays/python-react"),
          kv("name", name),
          kv("error", result.get("error")),
          kv("pythonRelayUrl", result.get("pythonRelayUrl")),
          kv("status", result.get("status")));
    } else {
      log.info(
          "PythonReactRelayApiController.relayViaPythonToReact succeeded",
          kv("source", SOURCE),
          kv("controller", "PythonReactRelayApiController"),
          kv("method", "POST"),
          kv("path", "/dashboard/relays/python-react"),
          kv("name", name),
          kv("pythonRelayUrl", result.get("pythonRelayUrl")),
          kv("status", result.get("status")));
    }
    return result;
  }
}
