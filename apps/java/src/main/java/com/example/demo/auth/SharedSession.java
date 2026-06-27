package com.example.demo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Session payload stored in Redis at {@code webserver-benchmark:session:{sessionId}}.
 *
 * <p>Other stack apps (Python, Rust, react-node) can authenticate by reading the same key from
 * Redis and checking {@link #expiresAt()} is in the future. Clients pass {@link #sessionId()} via
 * {@code Authorization: Bearer …}, {@code X-Session-ID}, or the {@code webserver_benchmark_session} cookie.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SharedSession(
    String sessionId,
    long userId,
    String email,
    String name,
    Instant issuedAt,
    Instant expiresAt,
    String issuer) {

  public boolean isExpired(Instant now) {
    return expiresAt != null && !expiresAt.isAfter(now);
  }
}
