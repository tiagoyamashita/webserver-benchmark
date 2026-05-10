package com.example.demo.maven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovered test classes grouped by Java package (shown as a task subject). */
public record DiscoveredSubjectGroup(
    String subjectTitle, String javaPackage, List<DiscoveredTestClass> tests) {

  public static List<DiscoveredSubjectGroup> from(List<DiscoveredTestClass> flat) {
    List<DiscoveredTestClass> sorted = new ArrayList<>(flat);
    sorted.sort(Comparator.comparing(DiscoveredTestClass::fqcn));
    Map<String, List<DiscoveredTestClass>> byPkg = new LinkedHashMap<>();
    for (DiscoveredTestClass t : sorted) {
      byPkg.computeIfAbsent(t.javaPackage(), k -> new ArrayList<>()).add(t);
    }
    List<String> pkgKeys = new ArrayList<>(byPkg.keySet());
    pkgKeys.sort(Comparator.naturalOrder());
    List<DiscoveredSubjectGroup> out = new ArrayList<>();
    for (String pkg : pkgKeys) {
      out.add(
          new DiscoveredSubjectGroup(
              subjectTitleFromPackage(pkg), pkg, List.copyOf(byPkg.get(pkg))));
    }
    return out;
  }

  private static String subjectTitleFromPackage(String javaPackage) {
    if (javaPackage == null || javaPackage.isEmpty()) {
      return "Tests";
    }
    int last = javaPackage.lastIndexOf('.');
    return last < 0 ? javaPackage : javaPackage.substring(last + 1);
  }
}
