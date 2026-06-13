package com.example.demo.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestIdContextTest {

  @AfterEach
  void tearDown() {
    RequestIdContext.clear();
  }

  @Test
  void set_mirrorsRequestIdIntoMdc() {
    RequestIdContext.set("req-abc-12345678");
    assertEquals("req-abc-12345678", RequestIdContext.get());
    assertEquals("req-abc-12345678", MDC.get(RequestIdContext.MDC_REQUEST_ID));
  }

  @Test
  void clear_removesMdcRequestId() {
    RequestIdContext.set("req-abc-12345678");
    RequestIdContext.clear();
    assertNull(RequestIdContext.get());
    assertNull(MDC.get(RequestIdContext.MDC_REQUEST_ID));
  }

  @Test
  void get_fallsBackToMdcWhenThreadLocalMissing() {
    MDC.put(RequestIdContext.MDC_REQUEST_ID, "mdc-only-id-12345678");
    try {
      assertEquals("mdc-only-id-12345678", RequestIdContext.get());
    } finally {
      MDC.remove(RequestIdContext.MDC_REQUEST_ID);
    }
  }
}
