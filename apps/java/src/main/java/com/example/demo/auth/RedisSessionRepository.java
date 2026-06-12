package com.example.demo.auth;

import com.example.demo.config.SessionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisSessionRepository {

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final SessionProperties sessionProperties;

  public RedisSessionRepository(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      SessionProperties sessionProperties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.sessionProperties = sessionProperties;
  }

  public void save(SharedSession session) {
    try {
      String json = objectMapper.writeValueAsString(session);
      redis
          .opsForValue()
          .set(
              sessionProperties.redisKey(session.sessionId()),
              json,
              sessionProperties.getTtl());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize session " + session.sessionId(), e);
    }
  }

  public Optional<SharedSession> findById(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    String json = redis.opsForValue().get(sessionProperties.redisKey(sessionId.trim()));
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(json, SharedSession.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize session " + sessionId, e);
    }
  }

  public void delete(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }
    redis.delete(sessionProperties.redisKey(sessionId.trim()));
  }
}
