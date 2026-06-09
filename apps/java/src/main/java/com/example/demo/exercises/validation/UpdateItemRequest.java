package com.example.demo.exercises.validation;

import jakarta.validation.constraints.NotBlank;

public record UpdateItemRequest(@NotBlank String name) {}
