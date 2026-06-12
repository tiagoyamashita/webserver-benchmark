package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard/stack-ping")
public class StackPingController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/StackPingController.java";
  private static final Logger log = LoggerFactory.getLogger(StackPingController.class);

  private final StackPingService stackPingService;

  public StackPingController(StackPingService stackPingService) {
    this.stackPingService = stackPingService;
  }

  @GetMapping("/postgres")
  public Map<String, Object> pingPostgres() {
    return ping("postgres", stackPingService::pingPostgres);
  }

  @GetMapping("/redis")
  public Map<String, Object> pingRedis() {
    return ping("redis", stackPingService::pingRedis);
  }

  @GetMapping("/rust")
  public Map<String, Object> pingRust() {
    return ping("rust", stackPingService::pingRust);
  }

  @GetMapping("/python")
  public Map<String, Object> pingPython() {
    return ping("python", stackPingService::pingPython);
  }

  @GetMapping("/prometheus")
  public Map<String, Object> pingPrometheus() {
    return ping("prometheus", stackPingService::pingPrometheus);
  }

  @GetMapping("/grafana")
  public Map<String, Object> pingGrafana() {
    return ping("grafana", stackPingService::pingGrafana);
  }

  @GetMapping("/elasticsearch")
  public Map<String, Object> pingElasticsearch() {
    return ping("elasticsearch", stackPingService::pingElasticsearch);
  }

  @GetMapping("/kibana")
  public Map<String, Object> pingKibana() {
    return ping("kibana", stackPingService::pingKibana);
  }

  @GetMapping("/react-node")
  public Map<String, Object> pingReactNode() {
    return ping("react-node", stackPingService::pingReactNode);
  }

  @GetMapping("/all")
  public Map<String, Object> pingAll() {
    log.info(
        "StackPingController.pingAll request received",
        kv("source", SOURCE),
        kv("controller", "StackPingController"),
        kv("method", "GET"),
        kv("path", "/dashboard/stack-ping/all"),
        kv("ui_event", "dashboard.ui"),
        kv("action", "stack-ping-all"));
    Map<String, Object> result = stackPingService.pingAll();
    log.info(
        "StackPingController.pingAll succeeded",
        kv("source", SOURCE),
        kv("resultCount", result.get("results") instanceof java.util.List<?> list ? list.size() : null));
    return result;
  }

  private Map<String, Object> ping(String target, java.util.function.Supplier<Map<String, Object>> run) {
    log.info(
        "StackPingController.ping request received",
        kv("source", SOURCE),
        kv("controller", "StackPingController"),
        kv("method", "GET"),
        kv("path", "/dashboard/stack-ping/" + target),
        kv("target", target),
        kv("ui_event", "dashboard.ui"),
        kv("action", "stack-ping"));
    Map<String, Object> result = run.get();
    Object ok = result.get("ok");
    if (Boolean.FALSE.equals(ok)) {
      log.warn(
          "StackPingController.ping downstream unreachable",
          kv("source", SOURCE),
          kv("target", target),
          kv("ok", ok),
          kv("status", result.get("status")),
          kv("error", result.get("error")),
          kv("url", result.get("url")));
    } else {
      log.info(
          "StackPingController.ping succeeded",
          kv("source", SOURCE),
          kv("target", target),
          kv("ok", ok),
          kv("status", result.get("status")));
    }
    return result;
  }
}
