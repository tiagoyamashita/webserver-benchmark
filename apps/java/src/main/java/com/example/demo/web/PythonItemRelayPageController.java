package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dashboard/items")
public class PythonItemRelayPageController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/PythonItemRelayPageController.java";
  private static final Logger log = LoggerFactory.getLogger(PythonItemRelayPageController.class);

  private final PythonItemRelayService pythonItemRelayService;

  public PythonItemRelayPageController(PythonItemRelayService pythonItemRelayService) {
    this.pythonItemRelayService = pythonItemRelayService;
  }

  /** HTML form submit from the home page (redirects back with a flash message). */
  @PostMapping(value = "/add-via-python", consumes = "application/x-www-form-urlencoded")
  public String addViaPythonForm(@RequestParam("name") String name, RedirectAttributes redirect) {
    log.info(
        "PythonItemRelayPageController.addViaPythonForm request received",
        kv("source", SOURCE),
        kv("controller", "PythonItemRelayPageController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-python"),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-python"),
        kv("transport", "form"));
    var result = pythonItemRelayService.addItemViaPython(name);
    if (Boolean.TRUE.equals(result.get("ok"))) {
      log.info(
          "PythonItemRelayPageController.addViaPythonForm succeeded",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayPageController"),
          kv("name", name.trim()));
      redirect.addFlashAttribute("itemAddMessage", "Added via Python: " + name.trim());
    } else {
      Object err = result.get("error");
      log.warn(
          "PythonItemRelayPageController.addViaPythonForm failed",
          kv("source", SOURCE),
          kv("controller", "PythonItemRelayPageController"),
          kv("name", name),
          kv("error", err));
      redirect.addFlashAttribute(
          "itemAddError", err != null ? err.toString() : "Could not add item via Python");
    }
    return "redirect:/";
  }
}
