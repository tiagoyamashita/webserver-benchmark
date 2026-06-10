package com.example.demo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accept or generate {@code X-Request-ID} for log / SQL correlation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Request-ID";
  private static final Pattern SAFE =
      Pattern.compile("^[a-zA-Z0-9._-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request);
    RequestIdContext.set(requestId);
    response.setHeader(HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      RequestIdContext.clear();
    }
  }

  static String resolveRequestId(HttpServletRequest request) {
    String incoming = request.getHeader(HEADER);
    if (incoming != null) {
      String trimmed = incoming.trim();
      if (SAFE.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString();
  }
}
