package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.observability.RequestIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dashboard/items")
public class RustItemRelayPageController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/RustItemRelayPageController.java";
  private static final Logger log = LoggerFactory.getLogger(RustItemRelayPageController.class);

  private final RustItemRelayService rustItemRelayService;

  public RustItemRelayPageController(RustItemRelayService rustItemRelayService) {
    this.rustItemRelayService = rustItemRelayService;
  }

  /** HTML form submit from the home page (redirects back with a flash message). */
  @PostMapping(value = "/add-via-rust", consumes = "application/x-www-form-urlencoded")
  public String addViaRustForm(@RequestParam("name") String name, RedirectAttributes redirect) {
    log.info(
        "RustItemRelayPageController.addViaRustForm request received",
        kv("source", SOURCE),
        kv("controller", "RustItemRelayPageController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-rust"),
        kv("request_id", RequestIdContext.get()),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-rust"),
        kv("transport", "form"));
    var result = rustItemRelayService.addItemViaRust(name);
    if (Boolean.TRUE.equals(result.get("ok"))) {
      log.info(
          "RustItemRelayPageController.addViaRustForm succeeded",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayPageController"),
          kv("request_id", RequestIdContext.get()),
          kv("name", name.trim()));
      redirect.addFlashAttribute("itemAddMessage", "Added via Rust: " + name.trim());
    } else {
      Object err = result.get("error");
      log.warn(
          "RustItemRelayPageController.addViaRustForm failed",
          kv("source", SOURCE),
          kv("controller", "RustItemRelayPageController"),
          kv("request_id", RequestIdContext.get()),
          kv("name", name),
          kv("error", err));
      redirect.addFlashAttribute(
          "itemAddError", err != null ? err.toString() : "Could not add item via Rust");
    }
    return "redirect:/";
  }
}
