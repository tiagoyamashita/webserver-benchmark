package com.example.demo.maven;

import java.util.List;

/** One UI section: a category label plus its discovered test classes (sorted by FQCN). */
public record DiscoveredTestCategoryGroup(
    TestUiCategory category, List<DiscoveredTestClass> tests) {}
