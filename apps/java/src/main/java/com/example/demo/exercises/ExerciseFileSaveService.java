package com.example.demo.exercises;

import com.example.demo.ProjectLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ExerciseFileSaveService {

  /** Allows nested packages such as {@code com.example.demo.web.ItemController.GetCollectionRestTest}. */
  private static final Pattern ALLOWED_FQCN =
      Pattern.compile("^com\\.example\\.demo(\\.[\\w]+)+$");

  private final ProjectLayout layout;

  public ExerciseFileSaveService(ProjectLayout layout) {
    this.layout = layout;
  }

  /**
   * Where {@link #save(String, String)} would write: test FQCNs map to a {@code *Response} file next to
   * the test; others map to {@code src/main/java}.
   */
  public WriteTarget resolveWriteTarget(String fqcn) {
    String trimmed = fqcn == null ? "" : fqcn.trim();
    validateFqcn(trimmed);
    boolean testSources = isTestSourceName(trimmed);
    String writeFqcn = testSources ? appendResponseClassName(trimmed) : trimmed;
    validateFqcn(writeFqcn);
    String relative =
        testSources
            ? relativeTestJavaPath(writeFqcn)
            : relativeMainSrcPath(writeFqcn);
    return new WriteTarget(trimmed, writeFqcn, relative, minimalJavaStub(writeFqcn));
  }

  /**
   * Writes Java under the resolved module: {@code src/test/java} for test FQCNs ({@code *Test}),
   * otherwise {@code src/main/java}.
   *
   * <p>For <strong>test</strong> classes, the file name is the test simple name with {@code
   * Response} appended (e.g. {@code GetCollectionRestTest} → {@code GetCollectionRestTestResponse.java})
   * in the same package as the test.
   */
  public SaveOutcome save(String fqcn, String content) throws IOException {
    WriteTarget writeTarget = resolveWriteTarget(fqcn);
    String relative = writeTarget.relativePath();
    boolean testSources = isTestSourceName(writeTarget.requestedFqcn());
    Path root = layout.resolveProjectRoot().normalize();
    Path filePath = root.resolve(relative).normalize();
    Path allowedRoot =
        root.resolve(testSources ? "src/test/java" : "src/main/java").normalize();
    if (!filePath.startsWith(allowedRoot)) {
      throw new IllegalArgumentException("Refusing to write outside " + allowedRoot);
    }
    Files.createDirectories(filePath.getParent());
    Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
    return new SaveOutcome(relative, filePath);
  }

  private static String minimalJavaStub(String writeFqcn) {
    int last = writeFqcn.lastIndexOf('.');
    String pkg = last < 0 ? "" : writeFqcn.substring(0, last);
    String simple = last < 0 ? writeFqcn : writeFqcn.substring(last + 1);
    String nl = "\n";
    StringBuilder sb = new StringBuilder();
    if (!pkg.isEmpty()) {
      sb.append("package ").append(pkg).append(";").append(nl).append(nl);
    }
    sb.append("/** Exercise answer (same path as Save to project). */").append(nl);
    sb.append("class ").append(simple).append(" {").append(nl).append("}").append(nl);
    return sb.toString();
  }

  /**
   * @param requestedFqcn FQCN from the dashboard (usually the test class)
   * @param writeFqcn File/class name used on disk ({@code *Response} for tests)
   */
  public record WriteTarget(
      String requestedFqcn, String writeFqcn, String relativePath, String minimalJavaStub) {}

  private static void validateFqcn(String fqcn) {
    if (fqcn.isBlank()) {
      throw new IllegalArgumentException("fqcn is required");
    }
    if (fqcn.contains("..") || fqcn.contains("/") || fqcn.contains("\\")) {
      throw new IllegalArgumentException("Invalid fqcn");
    }
    if (!ALLOWED_FQCN.matcher(fqcn).matches()) {
      throw new IllegalArgumentException("Only com.example.demo.* classes can be written");
    }
  }

  /** True for test sources ({@code *Test} top-level class name). */
  public static boolean isTestSourceName(String fqcn) {
    int last = fqcn.lastIndexOf('.');
    String simple = last < 0 ? fqcn : fqcn.substring(last + 1);
    return simple.endsWith("Test");
  }

  /**
   * {@code com.pkg.FooTest} → {@code com.pkg.FooTestResponse} (same package, file name gains {@code
   * Response} before {@code .java}).
   */
  static String appendResponseClassName(String fqcn) {
    int last = fqcn.lastIndexOf('.');
    String pkg = last < 0 ? "" : fqcn.substring(0, last);
    String simple = last < 0 ? fqcn : fqcn.substring(last + 1);
    String withSuffix = simple + "Response";
    return pkg.isEmpty() ? withSuffix : pkg + "." + withSuffix;
  }

  /** {@code com.foo.BarTest} → {@code src/test/java/com/foo/BarTest.java}. */
  public static String relativeTestJavaPath(String fqcn) {
    int last = fqcn.lastIndexOf('.');
    String pkgPath = last < 0 ? "" : fqcn.substring(0, last).replace('.', '/');
    String simple = last < 0 ? fqcn : fqcn.substring(last + 1);
    if (pkgPath.isEmpty()) {
      return "src/test/java/" + simple + ".java";
    }
    return "src/test/java/" + pkgPath + "/" + simple + ".java";
  }

  /** {@code com.foo.Bar} → {@code src/main/java/com/foo/Bar.java}. */
  public static String relativeMainSrcPath(String fqcn) {
    int last = fqcn.lastIndexOf('.');
    String pkgPath = last < 0 ? "" : fqcn.substring(0, last).replace('.', '/');
    String simple = last < 0 ? fqcn : fqcn.substring(last + 1);
    if (pkgPath.isEmpty()) {
      return "src/main/java/" + simple + ".java";
    }
    return "src/main/java/" + pkgPath + "/" + simple + ".java";
  }

  public record SaveOutcome(String relativePath, Path absolutePath) {}
}
