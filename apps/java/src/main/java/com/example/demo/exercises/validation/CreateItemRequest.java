package com.example.demo.exercises.validation;

import jakarta.validation.constraints.NotBlank;

public record CreateItemRequest(@NotBlank String name) {}
