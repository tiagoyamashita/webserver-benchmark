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
@RequestMapping("/dashboard/relays")
public class PythonReactRelayPageController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/PythonReactRelayPageController.java";
  private static final Logger log = LoggerFactory.getLogger(PythonReactRelayPageController.class);

  private final PythonReactRelayService pythonReactRelayService;

  public PythonReactRelayPageController(PythonReactRelayService pythonReactRelayService) {
    this.pythonReactRelayService = pythonReactRelayService;
  }

  @PostMapping(value = "/python-react", consumes = "application/x-www-form-urlencoded")
  public String relayViaPythonToReactForm(
      @RequestParam("name") String name, RedirectAttributes redirect) {
    log.info(
        "PythonReactRelayPageController.relayViaPythonToReactForm request received",
        kv("source", SOURCE),
        kv("controller", "PythonReactRelayPageController"),
        kv("method", "POST"),
        kv("path", "/dashboard/relays/python-react"),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "relay-item-python-react"),
        kv("transport", "form"));
    var result = pythonReactRelayService.addItemViaPythonReactRelay(name);
    if (Boolean.TRUE.equals(result.get("ok"))) {
      log.info(
          "PythonReactRelayPageController.relayViaPythonToReactForm succeeded",
          kv("source", SOURCE),
          kv("controller", "PythonReactRelayPageController"),
          kv("name", name.trim()));
      redirect.addFlashAttribute(
          "itemAddMessage", "Added via Python → React relay: " + name.trim());
    } else {
      Object err = result.get("error");
      log.warn(
          "PythonReactRelayPageController.relayViaPythonToReactForm failed",
          kv("source", SOURCE),
          kv("controller", "PythonReactRelayPageController"),
          kv("name", name),
          kv("error", err));
      redirect.addFlashAttribute(
          "itemAddError",
          err != null ? err.toString() : "Could not relay item via Python → React");
    }
    return "redirect:/";
  }
}
