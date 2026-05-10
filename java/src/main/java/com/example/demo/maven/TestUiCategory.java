package com.example.demo.maven;

/** Dashboard grouping for discovered {@code *Test.java} classes. */
public enum TestUiCategory {
  CONTROLLER("Controller tests", "REST / MockMvc and controller-focused integration tests."),
  DATABASE("Database tests", "JPA, repositories, and persistence-focused tests."),
  LOG("Log tests", "Tests that emphasize console or Surefire output (e.g. endpoint probes)."),
  OTHER("Other", "Unclassified tests (misc packages or legacy paths).");

  private final String title;
  private final String description;

  TestUiCategory(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }
}
