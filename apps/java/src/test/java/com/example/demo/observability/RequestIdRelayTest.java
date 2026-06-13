package com.example.demo.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RequestIdRelayTest {

  @Test
  void resolveKafkaRequestId_prefersMessageBody() {
    String messageId = "11111111-2222-3333-4444-555555555555";
    String headerId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    assertEquals(messageId, RequestIdRelay.resolveKafkaRequestId(messageId, headerId));
  }

  @Test
  void resolveKafkaRequestId_fallsBackToHeader() {
    String headerId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    assertEquals(headerId, RequestIdRelay.resolveKafkaRequestId(null, headerId));
  }
}
