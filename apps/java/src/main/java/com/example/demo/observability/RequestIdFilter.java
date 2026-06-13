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
import org.springframework.web.util.ContentCachingRequestWrapper;

/** Accept or generate {@code X-Request-ID} for log / SQL correlation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Request-ID";
  private static final String PAGE_HEADER = "X-Dashboard-Page";
  private static final Pattern SAFE =
      Pattern.compile("^[a-zA-Z0-9._-]{8,64}$");
  private static final Pattern PAGE_SAFE =
      Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request);
    String dashboardPage = resolveDashboardPage(request);
    RequestIdContext.set(requestId);
    InboundRequestContext.set(request.getMethod(), request.getRequestURI());
    if (dashboardPage != null) {
      DashboardPageContext.set(dashboardPage);
    }
    response.setHeader(HEADER, requestId);
    HttpServletRequest wrapped =
        request instanceof ContentCachingRequestWrapper cached
            ? cached
            : new ContentCachingRequestWrapper(request);
    try {
      filterChain.doFilter(wrapped, response);
    } finally {
      RequestIdContext.clear();
      InboundRequestContext.clear();
      DashboardPageContext.clear();
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

  static String resolveDashboardPage(HttpServletRequest request) {
    String incoming = request.getHeader(PAGE_HEADER);
    if (incoming != null) {
      String trimmed = incoming.trim();
      if (PAGE_SAFE.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    return null;
  }
}
