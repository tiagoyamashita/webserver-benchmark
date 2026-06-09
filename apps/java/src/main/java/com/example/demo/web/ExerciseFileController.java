package com.example.demo.web;

import com.example.demo.exercises.ExerciseFileSaveService;
import com.example.demo.exercises.ExerciseFileSaveService.SaveOutcome;
import com.example.demo.exercises.ExerciseFileSaveService.WriteTarget;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class ExerciseFileController {

  private final ExerciseFileSaveService exerciseFileSaveService;

  public ExerciseFileController(ExerciseFileSaveService exerciseFileSaveService) {
    this.exerciseFileSaveService = exerciseFileSaveService;
  }

  /**
   * Target path and minimal stub for “Complete exercise” when there is no curriculum row, or for UI
   * preview — same rules as {@link #saveExerciseFile(SaveExerciseRequest)}.
   */
  @GetMapping("/exercise-save-target")
  public ResponseEntity<?> exerciseSaveTarget(@RequestParam("fqcn") String fqcn) {
    if (fqcn == null || fqcn.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "fqcn required"));
    }
    try {
      WriteTarget t = exerciseFileSaveService.resolveWriteTarget(fqcn.trim());
      return ResponseEntity.ok(
          Map.of(
              "relativePath",
              t.relativePath(),
              "writeFqcn",
              t.writeFqcn(),
              "minimalJavaStub",
              t.minimalJavaStub()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Saves exercise editor content to {@code src/test/java} (names ending in {@code Test}) or {@code
   * src/main/java} (everything else) under the resolved Maven module root.
   */
  @PostMapping("/exercise-file")
  public ResponseEntity<?> saveExerciseFile(@RequestBody SaveExerciseRequest body) {
    if (body == null || body.fqcn() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "fqcn required"));
    }
    try {
      SaveOutcome out = exerciseFileSaveService.save(body.fqcn(), body.content());
      return ResponseEntity.ok(
          Map.of(
              "relativePath",
              out.relativePath(),
              "absolutePath",
              out.absolutePath().toString(),
              "ok",
              true));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IOException e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("error", "Could not write file: " + e.getMessage()));
    }
  }

  public record SaveExerciseRequest(String fqcn, String content) {}
}
