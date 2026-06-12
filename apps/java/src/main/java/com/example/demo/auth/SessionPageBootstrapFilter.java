package com.example.demo.auth;

import com.example.demo.config.SessionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Creates a guest Redis session and {@code Set-Cookie} when the browser loads the dashboard HTML. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SessionPageBootstrapFilter extends OncePerRequestFilter {

  private final AuthService authService;
  private final SessionProperties sessionProperties;

  public SessionPageBootstrapFilter(AuthService authService, SessionProperties sessionProperties) {
    this.authService = authService;
    this.sessionProperties = sessionProperties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isDashboardPageLoad(request) && SessionContext.get() == null) {
      EnsureSessionResult result = authService.ensureSession(null, null);
      SessionContext.set(result.session());
      response.addHeader(
          HttpHeaders.SET_COOKIE, sessionCookie(result.session().sessionId()).toString());
    }
    filterChain.doFilter(request, response);
  }

  private static boolean isDashboardPageLoad(HttpServletRequest request) {
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null || !"/".equals(path)) {
      return false;
    }
    String accept = request.getHeader("Accept");
    return accept == null || accept.contains(MediaType.TEXT_HTML_VALUE);
  }

  private ResponseCookie sessionCookie(String sessionId) {
    return ResponseCookie.from(sessionProperties.getCookieName(), sessionId)
        .httpOnly(true)
        .path("/")
        .maxAge(sessionProperties.getTtl())
        .sameSite("Lax")
        .build();
  }
}
