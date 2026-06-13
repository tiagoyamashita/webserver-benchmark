package com.example.demo.messaging;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.config.KafkaAppProperties;
import com.example.demo.observability.RequestIdRelay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Publishes {@link CreateItemEvent} messages for the Python-only {@code create-item} consumer. */
@Service
public class CreateItemEventPublisher {

  private static final String SOURCE =
      "src/main/java/com/example/demo/messaging/CreateItemEventPublisher.java";
  private static final Logger log = LoggerFactory.getLogger(CreateItemEventPublisher.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final KafkaAppProperties kafkaAppProperties;
  private final ObjectMapper objectMapper;

  public CreateItemEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      KafkaAppProperties kafkaAppProperties,
      ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaAppProperties = kafkaAppProperties;
    this.objectMapper = objectMapper;
  }

  public void publishCreateItem(String name) throws JsonProcessingException {
    String requestId = RequestIdRelay.resolveOutboundRequestId();
    CreateItemEvent event = CreateItemEvent.of(name, requestId);
    String topic = kafkaAppProperties.getCreateItemTopic();
    String payload = objectMapper.writeValueAsString(event);

    log.info(
        "CreateItemEventPublisher.publishCreateItem publishing",
        kv("source", SOURCE),
        kv("controller", "CreateItemEventPublisher"),
        kv("topic", topic),
        kv("kafka_event", CreateItemEvent.EVENT_TYPE),
        kv("name", name));

    ProducerRecord<String, String> record = new ProducerRecord<>(topic, name, payload);
    record
        .headers()
        .add(new RecordHeader("X-Request-ID", requestId.getBytes(StandardCharsets.UTF_8)));

    kafkaTemplate.send(record);

    log.info(
        "CreateItemEventPublisher.publishCreateItem succeeded",
        kv("source", SOURCE),
        kv("controller", "CreateItemEventPublisher"),
        kv("topic", topic),
        kv("kafka_event", CreateItemEvent.EVENT_TYPE),
        kv("name", name));
  }
}
