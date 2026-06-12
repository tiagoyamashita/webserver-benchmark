package com.example.demo.config;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Logs Redis reachability at startup (non-fatal if the broker is down). */
@Component
public class RedisStartupVerifier implements ApplicationRunner {

  private static final String SOURCE =
      "src/main/java/com/example/demo/config/RedisStartupVerifier.java";
  private static final Logger log = LoggerFactory.getLogger(RedisStartupVerifier.class);

  private final RedisConnectionFactory redisConnectionFactory;

  public RedisStartupVerifier(RedisConnectionFactory redisConnectionFactory) {
    this.redisConnectionFactory = redisConnectionFactory;
  }

  @Override
  public void run(ApplicationArguments args) {
    String host = envOrDefault("REDIS_HOST", "127.0.0.1");
    String port = envOrDefault("REDIS_PORT", "6379");
    String url = "redis://" + host + ":" + port;
    try {
      String pong = redisConnectionFactory.getConnection().ping();
      log.info(
          "RedisStartupVerifier.run succeeded",
          kv("source", SOURCE),
          kv("controller", "RedisStartupVerifier"),
          kv("url", url),
          kv("pong", pong));
    } catch (RuntimeException e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.warn(
          "RedisStartupVerifier.run failed",
          kv("source", SOURCE),
          kv("controller", "RedisStartupVerifier"),
          kv("url", url),
          kv("error", msg));
    }
  }

  private static String envOrDefault(String key, String fallback) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }
}
