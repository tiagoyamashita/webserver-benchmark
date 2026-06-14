package com.example.demo.auth;

/** Login by Postgres user email or id; optional password (BCrypt) when set on the user row. */
public record LoginRequest(String email, Long userId, String password) {}
