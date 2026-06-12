package com.example.demo.auth;

/** Optional browser session id from {@code localStorage} when calling {@code POST /api/auth/ensure}. */
public record EnsureSessionRequest(String sessionId) {}
