package com.example.demo.auth;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.config.SessionProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Auth",
    description =
        "Shared Redis sessions for cross-app authentication (key exercises:session:{sessionId})")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/auth/AuthController.java";
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthService authService;
  private final SessionProperties sessionProperties;

  public AuthController(AuthService authService, SessionProperties sessionProperties) {
    this.authService = authService;
    this.sessionProperties = sessionProperties;
  }

  @PostMapping("/ensure")
  public ResponseEntity<SessionResponse> ensure(
      @RequestBody(required = false) EnsureSessionRequest body, HttpServletResponse response) {
    String clientSessionId = body != null ? body.sessionId() : null;
    log.info(
        "AuthController.ensure request received",
        kv("source", SOURCE),
        kv("controller", "AuthController"),
        kv("method", "POST"),
        kv("path", "/api/auth/ensure"),
        kv("clientSessionId", clientSessionId));
    EnsureSessionResult result = authService.ensureSession(clientSessionId, SessionContext.get());
    SessionContext.set(result.session());
    response.addHeader(
        HttpHeaders.SET_COOKIE, sessionCookie(result.session().sessionId()).toString());
    SessionResponse payload =
        SessionResponse.from(result.session(), authService.redisKey(result.session().sessionId()));
    log.info(
        "AuthController.ensure succeeded",
        kv("source", SOURCE),
        kv("created", result.created()),
        kv("userId", result.session().userId()));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(payload);
  }

  @PostMapping("/login")
  public ResponseEntity<SessionResponse> login(
      @Valid @RequestBody LoginRequest body, HttpServletResponse response) {
    log.info(
        "AuthController.login request received",
        kv("source", SOURCE),
        kv("controller", "AuthController"),
        kv("method", "POST"),
        kv("path", "/api/auth/login"),
        kv("email", body.email()),
        kv("userId", body.userId()));
    SharedSession session = authService.login(body);
    SessionContext.set(session);
    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(session.sessionId()).toString());
    SessionResponse payload =
        SessionResponse.from(session, authService.redisKey(session.sessionId()));
    log.info(
        "AuthController.login succeeded",
        kv("source", SOURCE),
        kv("userId", session.userId()),
        kv("redisKey", payload.redisKey()));
    return ResponseEntity.status(HttpStatus.CREATED).body(payload);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    log.info(
        "AuthController.logout request received",
        kv("source", SOURCE),
        kv("controller", "AuthController"),
        kv("method", "POST"),
        kv("path", "/api/auth/logout"));
    SharedSession session = authService.requireCurrentSession();
    authService.logout(session.sessionId());
    response.addHeader(HttpHeaders.SET_COOKIE, clearSessionCookie().toString());
    log.info(
        "AuthController.logout succeeded",
        kv("source", SOURCE));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/session")
  public SessionResponse currentSession() {
    log.info(
        "AuthController.currentSession request received",
        kv("source", SOURCE),
        kv("controller", "AuthController"),
        kv("method", "GET"),
        kv("path", "/api/auth/session"));
    SharedSession session = authService.requireCurrentSession();
    SessionResponse payload =
        SessionResponse.from(session, authService.redisKey(session.sessionId()));
    log.info(
        "AuthController.currentSession succeeded",
        kv("source", SOURCE),
        kv("userId", session.userId()));
    return payload;
  }

  private ResponseCookie sessionCookie(String sessionId) {
    return ResponseCookie.from(sessionProperties.getCookieName(), sessionId)
        .httpOnly(true)
        .path("/")
        .maxAge(sessionProperties.getTtl())
        .sameSite("Lax")
        .build();
  }

  private ResponseCookie clearSessionCookie() {
    return ResponseCookie.from(sessionProperties.getCookieName(), "")
        .httpOnly(true)
        .path("/")
        .maxAge(Duration.ZERO)
        .sameSite("Lax")
        .build();
  }
}
