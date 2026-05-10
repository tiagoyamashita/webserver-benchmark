package com.example.demo.web;

import com.example.demo.exercises.RequiredTestCatalog;
import com.example.demo.exercises.RequiredTestEntry;
import com.example.demo.exercises.TestAnswerTemplateService;
import com.example.demo.exercises.TestTemplatePayload;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class CurriculumTemplateController {

  private final RequiredTestCatalog catalog;
  private final TestAnswerTemplateService templateService;

  public CurriculumTemplateController(
      RequiredTestCatalog catalog, TestAnswerTemplateService templateService) {
    this.catalog = catalog;
    this.templateService = templateService;
  }

  /**
   * Starter Java source for a curriculum exercise (only {@code required-tests.json} entries).
   */
  @GetMapping("/test-template")
  public ResponseEntity<TestTemplatePayload> testTemplate(@RequestParam("fqcn") String fqcn) {
    if (fqcn == null || fqcn.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    Optional<RequiredTestEntry> entry = catalog.findByTargetFqcn(fqcn.trim());
    if (entry.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(templateService.build(entry.get()));
  }
}
