package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class DashboardMetaController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/DashboardMetaController.java";
  private static final Logger log = LoggerFactory.getLogger(DashboardMetaController.class);

  @GetMapping("/api/dashboard-meta")
  public Map<String, Object> meta() {
    log.info(
        "DashboardMetaController.meta request received",
        kv("source", SOURCE),
        kv("controller", "DashboardMetaController"),
        kv("method", "GET"),
        kv("path", "/api/dashboard-meta"));
    Map<String, Object> body =
        Map.of(
            "title", "Java APP",
            "template", "index.html",
            "path", "/",
            "version", 8,
            "features",
                "sidebar,connectivity-ping,ping-all,session-auth,user-create,user-list,item-list,item-create,rust-item-relay,openapi");
    log.info(
        "DashboardMetaController.meta succeeded",
        kv("source", SOURCE),
        kv("version", body.get("version")));
    return body;
  }
}
