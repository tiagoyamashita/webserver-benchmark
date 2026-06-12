package com.example.demo.auth;

import java.time.Instant;

public record SessionResponse(
    String sessionId,
    long userId,
    String email,
    String name,
    Instant issuedAt,
    Instant expiresAt,
    String issuer,
    String redisKey) {

  static SessionResponse from(SharedSession session, String redisKey) {
    return new SessionResponse(
        session.sessionId(),
        session.userId(),
        session.email(),
        session.name(),
        session.issuedAt(),
        session.expiresAt(),
        session.issuer(),
        redisKey);
  }
}
