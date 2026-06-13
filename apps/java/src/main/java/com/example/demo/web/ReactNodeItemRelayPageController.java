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
public class ReactNodeItemRelayPageController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/ReactNodeItemRelayPageController.java";
  private static final Logger log = LoggerFactory.getLogger(ReactNodeItemRelayPageController.class);

  private final ReactNodeItemRelayService reactNodeItemRelayService;

  public ReactNodeItemRelayPageController(ReactNodeItemRelayService reactNodeItemRelayService) {
    this.reactNodeItemRelayService = reactNodeItemRelayService;
  }

  /** HTML form submit from the home page (redirects back with a flash message). */
  @PostMapping(value = "/add-via-react-node", consumes = "application/x-www-form-urlencoded")
  public String addViaReactNodeForm(
      @RequestParam("name") String name, RedirectAttributes redirect) {
    log.info(
        "ReactNodeItemRelayPageController.addViaReactNodeForm request received",
        kv("source", SOURCE),
        kv("controller", "ReactNodeItemRelayPageController"),
        kv("method", "POST"),
        kv("path", "/dashboard/items/add-via-react-node"),
        kv("name", name),
        kv("ui_event", "dashboard.ui"),
        kv("action", "add-item-via-react-node"),
        kv("transport", "form"));
    var result = reactNodeItemRelayService.addItemViaReactNode(name);
    if (Boolean.TRUE.equals(result.get("ok"))) {
      log.info(
          "ReactNodeItemRelayPageController.addViaReactNodeForm succeeded",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayPageController"),
          kv("name", name.trim()));
      redirect.addFlashAttribute("itemAddMessage", "Added via React Node: " + name.trim());
    } else {
      Object err = result.get("error");
      log.warn(
          "ReactNodeItemRelayPageController.addViaReactNodeForm failed",
          kv("source", SOURCE),
          kv("controller", "ReactNodeItemRelayPageController"),
          kv("name", name),
          kv("error", err));
      redirect.addFlashAttribute(
          "itemAddError", err != null ? err.toString() : "Could not add item via React Node");
    }
    return "redirect:/";
  }
}
