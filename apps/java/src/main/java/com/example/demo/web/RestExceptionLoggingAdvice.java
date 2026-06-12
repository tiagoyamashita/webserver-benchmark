package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.auth.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Logs validation and unexpected REST failures with request params for replication. */
@RestControllerAdvice
public class RestExceptionLoggingAdvice {

  private static final String SOURCE =
      "src/main/java/com/example/demo/web/RestExceptionLoggingAdvice.java";
  private static final Logger log = LoggerFactory.getLogger(RestExceptionLoggingAdvice.class);

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<Object> handleAuth(AuthException ex, HttpServletRequest request) {
    HttpStatus status = ex.getStatus();
    if (status.is4xxClientError() && status != HttpStatus.INTERNAL_SERVER_ERROR) {
      log.warn(
          "REST auth failed",
          kv("source", SOURCE),
          kv("method", request.getMethod()),
          kv("path", request.getRequestURI()),
          kv("status", status.value()),
          kv("error", ex.getMessage()));
    } else {
      log.error(
          "REST auth failed",
          kv("source", SOURCE),
          kv("method", request.getMethod()),
          kv("path", request.getRequestURI()),
          kv("status", status.value()),
          kv("error", ex.getMessage()),
          ex);
    }
    return ResponseEntity.status(status).body(ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Object> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + "=" + err.getRejectedValue())
            .collect(Collectors.joining(", "));
    log.warn(
        "REST validation failed",
        kv("source", SOURCE),
        kv("method", request.getMethod()),
        kv("path", request.getRequestURI()),
        kv("fields", fields),
        kv("errors", ex.getBindingResult().getFieldErrors().size()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "REST request failed",
        kv("source", SOURCE),
        kv("method", request.getMethod()),
        kv("path", request.getRequestURI()),
        kv("query", request.getQueryString()),
        kv("error", ex.getMessage()),
        ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Internal server error");
  }
}
