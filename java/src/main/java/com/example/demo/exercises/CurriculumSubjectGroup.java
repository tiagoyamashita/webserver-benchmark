package com.example.demo.exercises;

import java.util.List;

/** Curriculum rows grouped under one subject heading (task topic). */
public record CurriculumSubjectGroup(String subject, List<RequiredTestRowView> rows) {}
