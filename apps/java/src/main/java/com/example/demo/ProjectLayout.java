package com.example.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the Java module root (directory containing {@code pom.xml}) for reading and writing
 * files under {@code src/}.
 */
@Component
public class ProjectLayout {

  @Value("${app.project.module-dir:}")
  private String configuredModuleDir;

  public Path resolveProjectRoot() {
    if (configuredModuleDir != null && !configuredModuleDir.isBlank()) {
      Path p = Path.of(configuredModuleDir).toAbsolutePath().normalize();
      if (Files.isRegularFile(p.resolve("pom.xml"))) {
        return p;
      }
    }
    Path cwd = Path.of("").toAbsolutePath().normalize();
    if (Files.isRegularFile(cwd.resolve("java/pom.xml"))) {
      return cwd.resolve("java");
    }
    if (Files.isRegularFile(cwd.resolve("pom.xml"))) {
      return cwd;
    }
    for (Path dir : listAncestors(cwd)) {
      if (Files.isRegularFile(dir.resolve("pom.xml"))) {
        return dir;
      }
    }
    return cwd;
  }

  private static List<Path> listAncestors(Path start) {
    List<Path> out = new ArrayList<>();
    for (Path d = start; d != null; d = d.getParent()) {
      out.add(d);
    }
    return out;
  }
}
