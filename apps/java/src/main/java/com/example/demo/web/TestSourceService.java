package com.example.demo.web;

import com.example.demo.ProjectLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TestSourceService {

  private static final int MAX_BYTES = 450_000;
  private static final Pattern SAFE_FQCN =
      Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.$]*$");

  private final ProjectLayout projectLayout;

  public TestSourceService(ProjectLayout projectLayout) {
    this.projectLayout = projectLayout;
  }

  /**
   * Reads the outermost test class {@code .java} file for an FQCN (supports {@code Outer$Nested} by
   * resolving {@code Outer.java}).
   */
  public Optional<TestSourcePayload> readTestClassSource(String className) {
    if (className == null || className.isBlank() || !SAFE_FQCN.matcher(className.strip()).matches()) {
      return Optional.empty();
    }
    String cn = className.strip();
    int dollar = cn.indexOf('$');
    String topLevel = dollar >= 0 ? cn.substring(0, dollar) : cn;
    if (topLevel.isEmpty() || topLevel.endsWith(".")) {
      return Optional.empty();
    }

    Path projectRoot = projectLayout.resolveProjectRoot();
    Path testJavaRoot = projectRoot.resolve("src/test/java").normalize();
    Path relative = Path.of(topLevel.replace('.', '/') + ".java");
    Path file = testJavaRoot.resolve(relative).normalize();
    if (!file.startsWith(testJavaRoot) || !Files.isRegularFile(file)) {
      return Optional.empty();
    }

    try {
      long size = Files.size(file);
      if (size > MAX_BYTES) {
        return Optional.of(
            new TestSourcePayload(
                file.toString(),
                "File is too large to show here (" + size + " bytes). Open it in your editor."));
      }
      String content = Files.readString(file, StandardCharsets.UTF_8);
      return Optional.of(new TestSourcePayload(file.toString(), content));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public record TestSourcePayload(String path, String content) {}
}
