package com.example.demo.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code create-user} events from Kafka (shared group {@code exercises-create-user}). */
@Component
public class CreateUserEventConsumer {

  private static final String SOURCE =
      "src/main/java/com/example/demo/messaging/CreateUserEventConsumer.java";
  private static final Logger log = LoggerFactory.getLogger(CreateUserEventConsumer.class);

  private final CreateUserEventHandler createUserEventHandler;

  public CreateUserEventConsumer(CreateUserEventHandler createUserEventHandler) {
    this.createUserEventHandler = createUserEventHandler;
  }

  @KafkaListener(
      topics = "${app.kafka.create-user-topic}",
      groupId = "${app.kafka.create-user-consumer-group}")
  public void onCreateUser(ConsumerRecord<String, String> record) {
    String requestIdHeader = headerValue(record, "X-Request-ID");
    String idForLog = requestIdHeader != null ? requestIdHeader : "";

    log.trace(
        "CreateUserEventConsumer.onCreateUser record request_id={}",
        idForLog,
        kv("source", SOURCE),
        kv("controller", "CreateUserEventConsumer"),
        kv("request_id", requestIdHeader),
        kv("topic", record.topic()),
        kv("partition", record.partition()),
        kv("offset", record.offset()));

    createUserEventHandler.handle(record.value(), requestIdHeader);
  }

  private static String headerValue(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }
}
