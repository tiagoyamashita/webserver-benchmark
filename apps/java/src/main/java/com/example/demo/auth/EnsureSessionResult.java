package com.example.demo.auth;

public record EnsureSessionResult(SharedSession session, boolean created) {}
