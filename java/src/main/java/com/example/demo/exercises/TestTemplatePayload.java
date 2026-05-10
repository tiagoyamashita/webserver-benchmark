package com.example.demo.exercises;

/** JSON body for {@code GET /dashboard/test-template}: starter for the on-disk answer file (e.g. {@code *TestResponse.java}). */
public record TestTemplatePayload(String exerciseTitle, String relativePath, String javaSource) {}
