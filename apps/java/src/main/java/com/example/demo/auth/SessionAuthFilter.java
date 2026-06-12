package com.example.demo.auth;

import com.example.demo.config.SessionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Resolves a shared Redis session from Bearer token, {@code X-Session-ID}, or cookie. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SessionAuthFilter extends OncePerRequestFilter {

  private static final String SESSION_HEADER = "X-Session-ID";
  private static final Pattern BEARER = Pattern.compile("^Bearer\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);

  private final RedisSessionRepository sessions;
  private final SessionProperties sessionProperties;

  public SessionAuthFilter(RedisSessionRepository sessions, SessionProperties sessionProperties) {
    this.sessions = sessions;
    this.sessionProperties = sessionProperties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      resolveSession(request).ifPresent(SessionContext::set);
      filterChain.doFilter(request, response);
    } finally {
      SessionContext.clear();
    }
  }

  private Optional<SharedSession> resolveSession(HttpServletRequest request) {
    String sessionId = resolveSessionId(request);
    if (sessionId == null) {
      return Optional.empty();
    }
    return sessions
        .findById(sessionId)
        .filter(session -> !session.isExpired(Instant.now()));
  }

  private String resolveSessionId(HttpServletRequest request) {
    String bearer = parseBearer(request.getHeader("Authorization"));
    if (bearer != null) {
      return bearer;
    }
    String header = request.getHeader(SESSION_HEADER);
    if (header != null && !header.isBlank()) {
      return header.trim();
    }
    return readCookie(request, sessionProperties.getCookieName());
  }

  private static String parseBearer(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    Matcher matcher = BEARER.matcher(authorization.trim());
    return matcher.matches() ? matcher.group(1) : null;
  }

  private static String readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
        return cookie.getValue().trim();
      }
    }
    return null;
  }
}
