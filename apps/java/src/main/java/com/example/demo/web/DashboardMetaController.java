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
        "version", 5,
        "features", "sidebar,connectivity-ping,ping-all,user-create,user-list,rust-item-relay");
  }
}
