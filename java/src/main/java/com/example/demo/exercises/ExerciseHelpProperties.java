package com.example.demo.exercises;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exercises.help")
public record ExerciseHelpProperties(String cloneUrl, String readmeUrl) {

  public ExerciseHelpProperties {
    cloneUrl = cloneUrl == null ? "" : cloneUrl.trim();
    readmeUrl = readmeUrl == null ? "" : readmeUrl.trim();
  }
}
