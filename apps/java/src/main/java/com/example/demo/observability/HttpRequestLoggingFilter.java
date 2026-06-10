package com.example.demo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    try {
      filterChain.doFilter(request, response);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String requestId = RequestIdContext.get();
      log.info(
          "{} {} {}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          StructuredArguments.kv("method", request.getMethod()),
          StructuredArguments.kv("path", request.getRequestURI()),
          StructuredArguments.kv("status", response.getStatus()),
          StructuredArguments.kv("ms", ms),
          StructuredArguments.kv("request_id", requestId));
    }
  }
}
