package com.example.demo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Structured HTTP access lines for Grafana / Kibana (method, path, status, ms). */
@Component
@Profile("observability")
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger("http.request");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    String requestId = RequestIdContext.get();
    String dashboardPage = DashboardPageContext.get();
    String idForLog = requestId != null ? requestId : "";
    Map<String, String> headers = HttpRequestSnapshot.headers(request);
    try {
      filterChain.doFilter(request, response);
    } finally {
      String method = request.getMethod();
      String path = request.getRequestURI();
      int status = response.getStatus();
      if (!HttpAccessLogging.shouldLogHttpAccess(method, path, status)) {
        return;
      }
      Map<String, Object> urlParams = HttpRequestSnapshot.urlParams(request);
      Map<String, Object> body = HttpRequestSnapshot.body(request);
      log.info(
          "{} {} request received request_id={}",
          method,
          path,
          idForLog,
          StructuredArguments.kv("method", method),
          StructuredArguments.kv("path", path),
          StructuredArguments.kv("request_id", requestId),
          StructuredArguments.kv("phase", "received"),
          StructuredArguments.kv("headers", headers),
          StructuredArguments.kv("url_params", urlParams),
          StructuredArguments.kv("body", body));
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String pageForLog = dashboardPage != null ? dashboardPage : "";
      log.info(
          "{} {} {} request_id={} dashboard_page={}",
          method,
          path,
          status,
          idForLog,
          pageForLog,
          StructuredArguments.kv("method", method),
          StructuredArguments.kv("path", path),
          StructuredArguments.kv("status", status),
          StructuredArguments.kv("ms", ms),
          StructuredArguments.kv("request_id", requestId),
          StructuredArguments.kv("dashboard_page", dashboardPage),
          StructuredArguments.kv("phase", "completed"));
    }
  }
}
