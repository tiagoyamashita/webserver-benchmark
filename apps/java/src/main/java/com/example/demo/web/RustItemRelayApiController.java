package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
public class RustItemRelayApiController {

  private static final Logger log = LoggerFactory.getLogger(RustItemRelayApiController.class);

  private final RustItemRelayService rustItemRelayService;

  public RustItemRelayApiController(RustItemRelayService rustItemRelayService) {
    this.rustItemRelayService = rustItemRelayService;
  }

  /** AJAX from home page: calls Rust {@code POST /api/items?name=…} and returns JSON for display. */
  @PostMapping(value = "/add-via-rust", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> addViaRustJson(@RequestParam("name") String name) {
    log.debug(
        "Dashboard UI add-via-rust API request",
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-rust"),
        kv("transport", "json"));
    return rustItemRelayService.addItemViaRust(name);
  }
}
