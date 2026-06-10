package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RustHelloApiController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/RustHelloApiController.java";
  private static final Logger log = LoggerFactory.getLogger(RustHelloApiController.class);

  @GetMapping("/hello-from-rust")
  public Map<String, Object> helloFromRust() {
    log.info(
        "RustHelloApiController.helloFromRust request received",
        kv("source", SOURCE),
        kv("controller", "RustHelloApiController"),
        kv("method", "GET"),
        kv("path", "/api/hello-from-rust"));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "Hello from Java");
    body.put("path", "/api/hello-from-rust");
    body.put("note", "Called by the Rust dashboard (server-side GET) or any HTTP client.");
    log.info(
        "RustHelloApiController.helloFromRust succeeded",
        kv("source", SOURCE),
        kv("path", "/api/hello-from-rust"));
    return body;
  }
}
