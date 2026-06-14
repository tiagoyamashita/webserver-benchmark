package com.example.demo.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpAccessLoggingTest {

  @Test
  void skipsGetActuatorPrometheusOn200() {
    assertFalse(HttpAccessLogging.shouldLogHttpAccess("GET", "/actuator/prometheus", 200));
  }

  @Test
  void logsGetActuatorPrometheusOnNon200() {
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("GET", "/actuator/prometheus", 503));
  }

  @Test
  void stillLogsOtherRoutes() {
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("GET", "/api/items", 200));
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("POST", "/actuator/prometheus", 200));
  }

  @Test
  void stripsQueryStringFromPath() {
    assertFalse(
        HttpAccessLogging.shouldLogHttpAccess("GET", "/actuator/prometheus?verbose=1", 200));
  }

  @Test
  void skipsGetObservabilityHealthOn200() {
    assertFalse(HttpAccessLogging.shouldLogHttpAccess("GET", "/api/observability/health", 200));
  }

  @Test
  void logsGetObservabilityHealthOnNon200() {
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("GET", "/api/observability/health", 503));
  }

  @Test
  void skipsPostAuthEnsureOn200() {
    assertFalse(HttpAccessLogging.shouldLogHttpAccess("POST", "/api/auth/ensure", 200));
  }

  @Test
  void logsPostAuthEnsureOnNon200() {
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("POST", "/api/auth/ensure", 201));
    assertTrue(HttpAccessLogging.shouldLogHttpAccess("POST", "/api/auth/ensure", 503));
  }
}
