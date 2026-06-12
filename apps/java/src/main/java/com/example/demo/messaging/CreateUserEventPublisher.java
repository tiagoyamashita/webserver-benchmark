package com.example.demo.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.config.KafkaAppProperties;
import com.example.demo.observability.RequestIdRelay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Publishes {@link CreateUserEvent} messages to Kafka for Java and Rust consumers. */
@Service
public class CreateUserEventPublisher {

  private static final String SOURCE =
      "src/main/java/com/example/demo/messaging/CreateUserEventPublisher.java";
  private static final Logger log = LoggerFactory.getLogger(CreateUserEventPublisher.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final KafkaAppProperties kafkaAppProperties;
  private final ObjectMapper objectMapper;

  public CreateUserEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      KafkaAppProperties kafkaAppProperties,
      ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaAppProperties = kafkaAppProperties;
    this.objectMapper = objectMapper;
  }

  /**
   * Publishes a {@code create-user} event. Java and Rust share consumer group
   * {@code exercises-create-user}; one app inserts into Postgres {@code users} per message.
   */
  public void publishCreateUser(String name, String email) throws JsonProcessingException {
    String requestId = RequestIdRelay.resolveOutboundRequestId();
    String idForLog = requestId;
    CreateUserEvent event = CreateUserEvent.of(name, email, requestId);
    String topic = kafkaAppProperties.getCreateUserTopic();
    String payload = objectMapper.writeValueAsString(event);

    log.info(
        "CreateUserEventPublisher.publishCreateUser publishing request_id={}",
        idForLog,
        kv("source", SOURCE),
        kv("controller", "CreateUserEventPublisher"),
        kv("request_id", requestId),
        kv("topic", topic),
        kv("event", CreateUserEvent.EVENT_TYPE),
        kv("name", name),
        kv("email", email));

    ProducerRecord<String, String> record = new ProducerRecord<>(topic, email, payload);
    record
        .headers()
        .add(new RecordHeader("X-Request-ID", requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    kafkaTemplate.send(record);

    log.info(
        "CreateUserEventPublisher.publishCreateUser succeeded request_id={}",
        idForLog,
        kv("source", SOURCE),
        kv("controller", "CreateUserEventPublisher"),
        kv("request_id", requestId),
        kv("topic", topic),
        kv("event", CreateUserEvent.EVENT_TYPE),
        kv("name", name),
        kv("email", email));
  }
}
