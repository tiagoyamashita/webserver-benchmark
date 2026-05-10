package com.example.demo.web;

import com.example.demo.maven.MavenTestRunService;
import com.example.demo.maven.MavenTestRunService.RunOutcome;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tests/api")
public class TestRunApiController {

  private final MavenTestRunService mavenTestRunService;

  public TestRunApiController(MavenTestRunService mavenTestRunService) {
    this.mavenTestRunService = mavenTestRunService;
  }

  /** Runs a single discovered test class via {@code mvn test -Dtest=Fqcn}. */
  @PostMapping(value = "/run", consumes = "application/json", produces = "application/json")
  public ResponseEntity<Map<String, Object>> runOne(@RequestBody(required = false) Map<String, String> body) {
    String fqcn = body == null ? "" : body.getOrDefault("fqcn", "").trim();
    if (fqcn.isEmpty()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "fqcn required", "passed", false, "exitCode", -1));
    }
    RunOutcome outcome = mavenTestRunService.runTestClasses(List.of(fqcn));
    boolean passed = outcome.allowedSelection() && outcome.exitCode() == 0;
    return ResponseEntity.ok(
        Map.of(
            "exitCode",
            outcome.exitCode(),
            "passed",
            passed,
            "output",
            outcome.combinedOutput(),
            "allowedSelection",
            outcome.allowedSelection()));
  }
}
