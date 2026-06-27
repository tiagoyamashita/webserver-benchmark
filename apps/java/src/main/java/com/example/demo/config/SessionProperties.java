package com.example.demo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared session settings stored in Redis for cross-app authentication. */
@ConfigurationProperties(prefix = "app.session")
public class SessionProperties {

  /**
   * Redis key prefix. Full key is {@code {redisKeyPrefix}{sessionId}} (e.g.
   * {@code webserver-benchmark:session:550e8400-e29b-41d4-a716-446655440000}).
   */
  private String redisKeyPrefix = "webserver-benchmark:session:";

  /** Session TTL written to Redis (other apps should honour {@code expiresAt} in the JSON value). */
  private Duration ttl = Duration.ofHours(24);

  /** Cookie name issued on login ({@code Set-Cookie}) and accepted on subsequent requests. */
  private String cookieName = "webserver_benchmark_session";

  public String getRedisKeyPrefix() {
    return redisKeyPrefix;
  }

  public void setRedisKeyPrefix(String redisKeyPrefix) {
    this.redisKeyPrefix = redisKeyPrefix;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }

  public String redisKey(String sessionId) {
    return redisKeyPrefix + sessionId;
  }
}
