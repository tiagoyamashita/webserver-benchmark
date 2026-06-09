package com.example.demo.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RustHelloApiController {

  @GetMapping("/hello-from-rust")
  public Map<String, Object> helloFromRust() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "Hello from Java");
    body.put("path", "/api/hello-from-rust");
    body.put("note", "Called by the Rust dashboard (server-side GET) or any HTTP client.");
    return body;
  }
}
