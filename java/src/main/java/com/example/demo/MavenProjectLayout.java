package com.example.demo;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MavenProjectLayout {

  @Value("${app.maven.module-dir:}")
  private String configuredModuleDir;

  /**
   * Directory containing {@code pom.xml} (Surefire reports and test sources live under it). Walks
   * up from the process working directory when no explicit {@code app.maven.module-dir} is set.
   */
  public Path resolveMavenProjectRoot() {
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
    List<Path> ancestors = listAncestors(cwd);
    for (Path dir : ancestors) {
      if (!Files.isRegularFile(dir.resolve("pom.xml"))) {
        continue;
      }
      Path reports = dir.resolve("target/surefire-reports");
      if (Files.isDirectory(reports) && directoryContainsTestReportXml(reports)) {
        return dir;
      }
    }
    for (Path dir : ancestors) {
      if (Files.isRegularFile(dir.resolve("pom.xml"))) {
        return dir;
      }
    }
    return cwd;
  }

  /**
   * Surefire output directory for this app. When the app runs from compiled {@code target/classes}
   * (typical IDE launch), this resolves next to that {@code target} and matches reports for the
   * same module regardless of {@code user.dir}.
   */
  public Path resolveSurefireReportsDir() {
    Path fromClasspath = tryResolveSurefireReportsFromClasspath();
    if (fromClasspath != null && Files.isDirectory(fromClasspath)) {
      return fromClasspath.normalize();
    }
    return resolveMavenProjectRoot().resolve("target/surefire-reports").normalize();
  }

  private static List<Path> listAncestors(Path start) {
    List<Path> out = new ArrayList<>();
    for (Path d = start; d != null; d = d.getParent()) {
      out.add(d);
    }
    return out;
  }

  private static Path tryResolveSurefireReportsFromClasspath() {
    try {
      URL url = MavenProjectLayout.class.getProtectionDomain().getCodeSource().getLocation();
      if (url == null) {
        return null;
      }
      Path loc = Path.of(url.toURI());
      if (!Files.isDirectory(loc)) {
        return null;
      }
      Path leaf = loc.getFileName();
      if (leaf == null || !"classes".equals(leaf.toString())) {
        return null;
      }
      Path target = loc.getParent();
      if (target == null || !"target".equals(target.getFileName().toString())) {
        return null;
      }
      return target.resolve("surefire-reports");
    } catch (URISyntaxException | RuntimeException e) {
      return null;
    }
  }

  private static boolean directoryContainsTestReportXml(Path reportsDir) {
    try (Stream<Path> s = Files.list(reportsDir)) {
      return s.anyMatch(
          p -> {
            String n = p.getFileName().toString();
            return Files.isRegularFile(p) && n.startsWith("TEST-") && n.endsWith(".xml");
          });
    } catch (IOException e) {
      return false;
    }
  }
}
