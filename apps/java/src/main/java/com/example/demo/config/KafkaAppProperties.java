package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public class KafkaAppProperties {

  private String createUserTopic = "create-user";
  private String createUserConsumerGroup = "exercises-create-user";
  private int createUserPartitions = 1;
  private short createUserReplicas = 1;

  public String getCreateUserTopic() {
    return createUserTopic;
  }

  public void setCreateUserTopic(String createUserTopic) {
    this.createUserTopic = createUserTopic;
  }

  public String getCreateUserConsumerGroup() {
    return createUserConsumerGroup;
  }

  public void setCreateUserConsumerGroup(String createUserConsumerGroup) {
    this.createUserConsumerGroup = createUserConsumerGroup;
  }

  public int getCreateUserPartitions() {
    return createUserPartitions;
  }

  public void setCreateUserPartitions(int createUserPartitions) {
    this.createUserPartitions = createUserPartitions;
  }

  public short getCreateUserReplicas() {
    return createUserReplicas;
  }

  public void setCreateUserReplicas(short createUserReplicas) {
    this.createUserReplicas = createUserReplicas;
  }
}
