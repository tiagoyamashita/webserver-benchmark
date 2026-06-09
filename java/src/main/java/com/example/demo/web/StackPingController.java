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

  private static final Logger log = LoggerFactory.getLogger(StackPingController.class);

  private final StackPingService stackPingService;

  public StackPingController(StackPingService stackPingService) {
    this.stackPingService = stackPingService;
  }

  @GetMapping("/postgres")
  public Map<String, Object> pingPostgres() {
    return ping("postgres", stackPingService::pingPostgres);
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
        "Dashboard UI button click",
        kv("ui_event", "dashboard.ui"),
        kv("action", "stack-ping-all"));
    return stackPingService.pingAll();
  }

  private Map<String, Object> ping(String target, java.util.function.Supplier<Map<String, Object>> run) {
    log.info(
        "Dashboard UI button click",
        kv("ui_event", "dashboard.ui"),
        kv("action", "stack-ping"),
        kv("target", target));
    return run.get();
  }
}
