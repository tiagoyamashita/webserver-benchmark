package com.example.demo.maven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Assigns each discovered test class to a {@link TestUiCategory} for the dashboard. */
public final class TestCategoryClassifier {

  private TestCategoryClassifier() {}

  /**
   * Groups tests into non-empty category sections, ordered: Controller, Database, Log, Other.
   */
  public static List<DiscoveredTestCategoryGroup> groupByCategory(List<DiscoveredTestClass> flat) {
    Map<TestUiCategory, List<DiscoveredTestClass>> buckets = new EnumMap<>(TestUiCategory.class);
    for (TestUiCategory c : TestUiCategory.values()) {
      buckets.put(c, new ArrayList<>());
    }
    for (DiscoveredTestClass t : flat) {
      buckets.get(categorize(t)).add(t);
    }
    List<DiscoveredTestCategoryGroup> out = new ArrayList<>();
    for (TestUiCategory c : TestUiCategory.values()) {
      List<DiscoveredTestClass> list = buckets.get(c);
      if (list.isEmpty()) {
        continue;
      }
      list.sort(Comparator.comparing(DiscoveredTestClass::fqcn));
      out.add(new DiscoveredTestCategoryGroup(c, List.copyOf(list)));
    }
    return out;
  }

  static TestUiCategory categorize(DiscoveredTestClass t) {
    String fq = t.fqcn();
    String simple = t.simpleName();
    if ("TestingEndpointAvailabilityTest".equals(simple)) {
      return TestUiCategory.LOG;
    }
    if (fq.contains(".test.log.")) {
      return TestUiCategory.LOG;
    }
    if (fq.contains(".test.database.")) {
      return TestUiCategory.DATABASE;
    }
    if (fq.contains(".test.controller.")) {
      return TestUiCategory.CONTROLLER;
    }
    if (fq.contains(".exercises.controller.")) {
      return TestUiCategory.CONTROLLER;
    }
    if (fq.contains(".web.ItemController.")) {
      return TestUiCategory.CONTROLLER;
    }
    return TestUiCategory.OTHER;
  }
}
