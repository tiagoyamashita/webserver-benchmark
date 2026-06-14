package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
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

  /**
   * Dev workspace (Compose mount at {@code /workspace}): serve HTML from {@code src/…} so edits
   * apply without {@code mvn resources:resources}. Production JAR has no {@code src/} tree.
   */
  private static Resource pageResource(String fileName) {
    Path devFile = Path.of("src/main/resources/static", fileName);
    if (Files.isRegularFile(devFile)) {
      return new FileSystemResource(devFile);
    }
    return new ClassPathResource("static/" + fileName);
  }

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
        .body(pageResource("index.html"));
  }

  /** Login page at {@code GET /login} (classpath {@code static/login.html}). */
  @GetMapping("/login")
  public ResponseEntity<Resource> loginPage() {
    log.info(
        "HomeController.loginPage request received",
        kv("source", SOURCE),
        kv("controller", "HomeController"),
        kv("method", "GET"),
        kv("path", "/login"));
    log.info(
        "HomeController.loginPage succeeded",
        kv("source", SOURCE),
        kv("template", "static/login.html"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.TEXT_HTML)
        .body(pageResource("login.html"));
  }
}
