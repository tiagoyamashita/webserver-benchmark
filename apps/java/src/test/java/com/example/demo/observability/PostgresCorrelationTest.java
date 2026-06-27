package com.example.demo.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresCorrelationTest {

  @Test
  void postgresApplicationName_includesRequestId() {
    String requestId = "11111111-2222-3333-4444-555555555555";
    assertEquals(
        "webserver-benchmark-java;req=" + requestId,
        PostgresCorrelation.postgresApplicationName("webserver-benchmark-java", requestId));
  }

  @Test
  void postgresApplicationName_truncatesLongRequestId() {
    String requestId = "x".repeat(80);
    String stamped = PostgresCorrelation.postgresApplicationName("webserver-benchmark-java", requestId);
    assertTrue(stamped.length() <= 63);
    assertEquals("webserver-benchmark-java;req=" + "x".repeat(44), stamped);
  }

  @Test
  void resolveEffectiveRequestId_fallsBackToOutboundWhenMissing() {
    String id = PostgresCorrelation.resolveEffectiveRequestId();
    assertFalse(id.isBlank());
  }
}
