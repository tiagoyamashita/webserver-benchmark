package com.example.demo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Blocks API and dashboard routes until the session is bound to a registered user. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LoggedInUserRequiredFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("GET".equals(method) && ("/".equals(path) || "/login.html".equals(path))) {
      return true;
    }
    if ("/health".equals(path) || "/metrics".equals(path)) {
      return true;
    }
    if (path.startsWith("/api/auth/")) {
      return true;
    }
    if ("POST".equals(method) && "/api/users".equals(path)) {
      return true;
    }
    if (path.startsWith("/js/") || path.startsWith("/css/") || path.startsWith("/static/")) {
      return true;
    }
    if (path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".html") || path.endsWith(".ico")) {
      return true;
    }
    if (path.startsWith("/swagger-ui") || path.startsWith("/api-docs") || path.startsWith("/v3/api-docs")) {
      return true;
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    SharedSession session = SessionContext.get();
    if (session != null && session.userId() > 0L && session.email() != null && !session.email().isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"Sign in required\"}");
  }
}
