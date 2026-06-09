package com.example.demo.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardMetaController {

  @GetMapping("/api/dashboard-meta")
  public Map<String, Object> meta() {
    return Map.of(
        "title", "Java APP",
        "template", "index.html",
        "path", "/",
        "version", 3,
        "features", "connectivity-ping,ping-all,rust-item-relay");
  }
}
