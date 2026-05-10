package com.example.demo.exercises;

public record RequiredTestEntry(String id, String targetFqcn, String title, String hint, String subject) {
  public RequiredTestEntry {
    if (subject == null || subject.isBlank()) {
      subject = "General";
    } else {
      subject = subject.trim();
    }
  }
}
