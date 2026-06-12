package com.example.demo.auth;

import com.example.demo.config.SessionProperties;
import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository users;
  private final RedisSessionRepository sessions;
  private final SessionProperties sessionProperties;

  public AuthService(
      UserRepository users,
      RedisSessionRepository sessions,
      SessionProperties sessionProperties) {
    this.users = users;
    this.sessions = sessions;
    this.sessionProperties = sessionProperties;
  }

  public SharedSession login(LoginRequest request) {
    User user = resolveUser(request);
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(sessionProperties.getTtl());
    SharedSession session =
        new SharedSession(
            UUID.randomUUID().toString(),
            user.getId(),
            user.getEmail(),
            user.getName(),
            issuedAt,
            expiresAt,
            "java");
    sessions.save(session);
    return session;
  }

  public void logout(String sessionId) {
    sessions.delete(sessionId);
  }

  public SharedSession requireCurrentSession() {
    SharedSession session = SessionContext.get();
    if (session == null) {
      throw new AuthException(HttpStatus.UNAUTHORIZED, "No active session");
    }
    if (session.isExpired(Instant.now())) {
      sessions.delete(session.sessionId());
      throw new AuthException(HttpStatus.UNAUTHORIZED, "Session expired");
    }
    return session;
  }

  public SharedSession validateSessionId(String sessionId) {
    SharedSession session =
        sessions
            .findById(sessionId)
            .orElseThrow(
                () -> new AuthException(HttpStatus.UNAUTHORIZED, "Session not found in Redis"));
    if (session.isExpired(Instant.now())) {
      sessions.delete(session.sessionId());
      throw new AuthException(HttpStatus.UNAUTHORIZED, "Session expired");
    }
    return session;
  }

  public String redisKey(String sessionId) {
    return sessionProperties.redisKey(sessionId);
  }

  private User resolveUser(LoginRequest request) {
    if (request == null) {
      throw new AuthException(HttpStatus.BAD_REQUEST, "email or userId is required");
    }
    if (request.email() != null && !request.email().isBlank()) {
      return users
          .findByEmailIgnoreCase(request.email().trim())
          .orElseThrow(
              () ->
                  new AuthException(
                      HttpStatus.NOT_FOUND, "No user with email " + request.email().trim()));
    }
    if (request.userId() != null) {
      return users
          .findById(request.userId())
          .orElseThrow(
              () ->
                  new AuthException(
                      HttpStatus.NOT_FOUND, "No user with id " + request.userId()));
    }
    throw new AuthException(HttpStatus.BAD_REQUEST, "email or userId is required");
  }
}
