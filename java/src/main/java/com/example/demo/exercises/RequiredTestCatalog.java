package com.example.demo.exercises;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class RequiredTestCatalog {

  private final List<RequiredTestEntry> entries;

  public RequiredTestCatalog(ObjectMapper objectMapper) {
    this.entries = loadOrEmpty(objectMapper);
  }

  public List<RequiredTestEntry> entries() {
    return entries;
  }

  public Optional<RequiredTestEntry> findByTargetFqcn(String fqcn) {
    if (fqcn == null || fqcn.isBlank()) {
      return Optional.empty();
    }
    return entries.stream().filter(e -> e.targetFqcn().equals(fqcn)).findFirst();
  }

  private static List<RequiredTestEntry> loadOrEmpty(ObjectMapper objectMapper) {
    try {
      ClassPathResource res = new ClassPathResource("exercises/required-tests.json");
      if (!res.exists()) {
        return List.of();
      }
      try (InputStream in = res.getInputStream()) {
        Wrapper w = objectMapper.readValue(in, Wrapper.class);
        return w.tests == null ? List.of() : List.copyOf(w.tests);
      }
    } catch (IOException e) {
      return List.of();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class Wrapper {
    public List<RequiredTestEntry> tests;
  }
}
