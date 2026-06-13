package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares application topics. Spring {@code KafkaAdmin} creates each topic on startup when it does
 * not already exist (see {@code spring.kafka.admin.fail-fast}).
 */
@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic createUserTopic(KafkaAppProperties kafkaAppProperties) {
    return TopicBuilder.name(kafkaAppProperties.getCreateUserTopic())
        .partitions(kafkaAppProperties.getCreateUserPartitions())
        .replicas(kafkaAppProperties.getCreateUserReplicas())
        .build();
  }

  @Bean
  public NewTopic createItemTopic(KafkaAppProperties kafkaAppProperties) {
    return TopicBuilder.name(kafkaAppProperties.getCreateItemTopic())
        .partitions(kafkaAppProperties.getCreateItemPartitions())
        .replicas(kafkaAppProperties.getCreateItemReplicas())
        .build();
  }
}
