package com.example.demo.auth;

import com.example.demo.config.SessionProperties;
import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import java.time.Instant;
import java.util.Optional;
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

  public EnsureSessionResult ensureSession(String clientSessionId, SharedSession requestSession) {
    Instant now = Instant.now();
    Optional<SharedSession> fromRequest = validSession(requestSession, now);
    if (fromRequest.isPresent()) {
      return new EnsureSessionResult(fromRequest.get(), false);
    }
    if (clientSessionId != null && !clientSessionId.isBlank()) {
      Optional<SharedSession> fromClient = validStoredSession(clientSessionId.trim(), now);
      if (fromClient.isPresent()) {
        return new EnsureSessionResult(fromClient.get(), false);
      }
    }
    return new EnsureSessionResult(createAnonymousSession(), true);
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

  /** Delete the current Redis session (if any) and issue a new session id with fresh payload. */
  public SharedSession refreshSession(SharedSession current) {
    if (current != null) {
      sessions.delete(current.sessionId());
    }
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(sessionProperties.getTtl());
    SharedSession session;
    if (current != null && current.userId() > 0L) {
      session =
          new SharedSession(
              UUID.randomUUID().toString(),
              current.userId(),
              current.email(),
              current.name(),
              issuedAt,
              expiresAt,
              "java");
    } else {
      session =
          new SharedSession(
              UUID.randomUUID().toString(),
              0L,
              null,
              "Guest",
              issuedAt,
              expiresAt,
              "java");
    }
    sessions.save(session);
    return session;
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

  private SharedSession createAnonymousSession() {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(sessionProperties.getTtl());
    SharedSession session =
        new SharedSession(
            UUID.randomUUID().toString(),
            0L,
            null,
            "Guest",
            issuedAt,
            expiresAt,
            "java");
    sessions.save(session);
    return session;
  }

  private static Optional<SharedSession> validSession(SharedSession session, Instant now) {
    if (session == null || session.isExpired(now)) {
      return Optional.empty();
    }
    return Optional.of(session);
  }

  private Optional<SharedSession> validStoredSession(String sessionId, Instant now) {
    return sessions.findById(sessionId).filter(session -> !session.isExpired(now));
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
