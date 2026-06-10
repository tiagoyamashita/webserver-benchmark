package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/HomeController.java";
  private static final Logger log = LoggerFactory.getLogger(HomeController.class);

  private static final Resource DASHBOARD = new ClassPathResource("static/index.html");

  /** Dashboard HTML at {@code GET /} (classpath {@code static/index.html}). */
  @GetMapping("/")
  public ResponseEntity<Resource> home() {
    log.info(
        "HomeController.home request received",
        kv("source", SOURCE),
        kv("controller", "HomeController"),
        kv("method", "GET"),
        kv("path", "/"));
    log.info(
        "HomeController.home succeeded",
        kv("source", SOURCE),
        kv("template", "static/index.html"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.TEXT_HTML)
        .body(DASHBOARD);
  }
}
