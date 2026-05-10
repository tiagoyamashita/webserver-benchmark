package com.example.demo.exercises;

import com.example.demo.maven.TestDiscoveryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TestCurriculumService {

  private final RequiredTestCatalog catalog;
  private final TestDiscoveryService discovery;

  public TestCurriculumService(RequiredTestCatalog catalog, TestDiscoveryService discovery) {
    this.catalog = catalog;
    this.discovery = discovery;
  }

  /** Builds checklist rows: comparing curriculum {@link RequiredTestEntry#targetFqcn()} to disk. */
  public List<RequiredTestRowView> buildRows(DashboardNotesPayload notes) {
    Set<String> onDisk = discovery.allowedFqcnSet();
    List<RequiredTestRowView> rows = new ArrayList<>();
    var byId = notes.getByTestId();
    for (RequiredTestEntry spec : catalog.entries()) {
      boolean present = onDisk.contains(spec.targetFqcn());
      String answerFqcn = ExerciseFileSaveService.appendResponseClassName(spec.targetFqcn());
      String path = relativeSrcPath(answerFqcn);
      String draft = byId.getOrDefault(spec.id(), "");
      rows.add(new RequiredTestRowView(spec, present, path, draft));
    }
    return rows;
  }

  /** Same as {@link #buildRows} but grouped by {@link RequiredTestEntry#subject()} for the UI. */
  public List<CurriculumSubjectGroup> buildSubjectGroups(DashboardNotesPayload notes) {
    List<RequiredTestRowView> flat = buildRows(notes);
    Map<String, List<RequiredTestRowView>> map = new LinkedHashMap<>();
    for (RequiredTestRowView row : flat) {
      map.computeIfAbsent(row.spec().subject(), k -> new ArrayList<>()).add(row);
    }
    List<CurriculumSubjectGroup> groups = new ArrayList<>();
    for (Map.Entry<String, List<RequiredTestRowView>> e : map.entrySet()) {
      groups.add(new CurriculumSubjectGroup(e.getKey(), List.copyOf(e.getValue())));
    }
    return groups;
  }

  /** {@code com.foo.BarTest} → {@code src/test/java/com/foo/BarTest.java}. */
  public static String relativeSrcPath(String fqcn) {
    int last = fqcn.lastIndexOf('.');
    String pkgPath = last < 0 ? "" : fqcn.substring(0, last).replace('.', '/');
    String simple = last < 0 ? fqcn : fqcn.substring(last + 1);
    if (pkgPath.isEmpty()) {
      return "src/test/java/" + simple + ".java";
    }
    return "src/test/java/" + pkgPath + "/" + simple + ".java";
  }
}
