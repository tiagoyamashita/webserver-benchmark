package com.example.demo.exercises;

import org.springframework.stereotype.Service;

@Service
public class TestAnswerTemplateService {

  public TestTemplatePayload build(RequiredTestEntry entry) {
    String fqcn = entry.targetFqcn();
    String writeFqcn = ExerciseFileSaveService.appendResponseClassName(fqcn);
    String path = ExerciseFileSaveService.relativeTestJavaPath(writeFqcn);
    int last = fqcn.lastIndexOf('.');
    String pkg = last < 0 ? "" : fqcn.substring(0, last);
    String testSimple = last < 0 ? fqcn : fqcn.substring(last + 1);
    String responseSimple = testSimple + "Response";
    String hintLine = escapeComment(entry.hint());

    String java =
        switch (templateKind(fqcn, pkg)) {
          case ITEM_REST -> itemRestTemplate(responseSimple, hintLine);
          case TESTING_ENDPOINT -> testingEndpointTemplate(responseSimple, hintLine);
          case DEMO_ROOT -> demoRootTemplate(responseSimple, hintLine);
          case GENERIC -> genericTemplate(pkg, responseSimple, hintLine);
        };
    return new TestTemplatePayload(entry.title(), path, java);
  }

  private enum Kind {
    ITEM_REST,
    TESTING_ENDPOINT,
    DEMO_ROOT,
    GENERIC
  }

  private static Kind templateKind(String fqcn, String pkg) {
    if ("com.example.demo.exercises.controller.item".equals(pkg)) {
      return Kind.ITEM_REST;
    }
    if ("com.example.demo.exercises.controller.TestingEndpointAvailabilityTest".equals(fqcn)) {
      return Kind.TESTING_ENDPOINT;
    }
    if ("com.example.demo.IntentionalFailureTest".equals(fqcn)) {
      return Kind.DEMO_ROOT;
    }
    return Kind.GENERIC;
  }

  /** Saved next to REST tests as {@code …TestResponse} (not the test source). */
  private static String itemRestTemplate(String responseClassName, String hintLine) {
    return "package com.example.demo.exercises.controller.item;\n"
        + "\n"
        + "/**\n"
        + " * Exercise answer for the paired test — "
        + hintLine
        + "\n"
        + " */\n"
        + "class "
        + responseClassName
        + " {\n"
        + "}\n";
  }

  private static String testingEndpointTemplate(String responseClassName, String hintLine) {
    return "package com.example.demo.exercises.controller;\n"
        + "\n"
        + "/**\n"
        + " * Exercise answer — "
        + hintLine
        + "\n"
        + " */\n"
        + "class "
        + responseClassName
        + " {\n"
        + "}\n";
  }

  private static String demoRootTemplate(String responseClassName, String hintLine) {
    return "package com.example.demo;\n"
        + "\n"
        + "/**\n"
        + " * Exercise answer — "
        + hintLine
        + "\n"
        + " */\n"
        + "class "
        + responseClassName
        + " {\n"
        + "}\n";
  }

  private static String genericTemplate(String javaPackage, String responseClassName, String hintLine) {
    return "package "
        + javaPackage
        + ";\n"
        + "\n"
        + "/**\n"
        + " * Exercise answer — "
        + hintLine
        + "\n"
        + " */\n"
        + "class "
        + responseClassName
        + " {\n"
        + "}\n";
  }

  private static String escapeComment(String hint) {
    if (hint == null || hint.isEmpty()) {
      return "Implement this exercise.";
    }
    return hint.replace("*/", "* /").replace("\r\n", "\n").replace('\n', ' ');
  }
}
