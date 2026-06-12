package com.example.demo.auth;

/** Login by Postgres user email or id (demo — no password in this exercises stack). */
public record LoginRequest(String email, Long userId) {}
