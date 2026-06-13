package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.demo.observability.OutboundHttpLogger;
import com.example.demo.observability.RequestIdRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class StackPingService {

  private static final Logger log = LoggerFactory.getLogger(StackPingService.class);

  private final RestClient restClient;
  private final StackPingProperties properties;
  private final RedisConnectionFactory redisConnectionFactory;

  public StackPingService(
      @Qualifier("stackPingRestClient") RestClient stackPingRestClient,
      StackPingProperties properties,
      RedisConnectionFactory redisConnectionFactory) {
    this.restClient = stackPingRestClient;
    this.properties = properties;
    this.redisConnectionFactory = redisConnectionFactory;
  }

  public Map<String, Object> emptyGet(String stack, String baseUrl) {
    String root = normalizeRoot(baseUrl);
    URI uri = URI.create(root);
    OutboundHttpLogger.logRequest("GET", uri, stack, null);
    long start = System.nanoTime();
    try {
      ResponseEntity<Void> res =
          RequestIdRelay.applyOutbound(restClient.get().uri(uri)).retrieve().toBodilessEntity();
      long ms = (System.nanoTime() - start) / 1_000_000L;
      OutboundHttpLogger.logResponse(
          "GET", uri, res.getStatusCode().value(), ms, stack, res.getHeaders(), "");
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", true,
              "status", res.getStatusCode().value());
      logStackPingResult(result);
      return result;
    } catch (RestClientResponseException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      OutboundHttpLogger.logResponse(
          "GET",
          uri,
          e.getStatusCode().value(),
          ms,
          stack,
          e.getResponseHeaders(),
          e.getResponseBodyAsString());
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", false,
              "status", e.getStatusCode().value(),
              "error", e.getStatusText() != null ? e.getStatusText() : e.getMessage());
      logStackPingResult(result);
      return result;
    } catch (ResourceAccessException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String hint =
          "Cannot connect (is the container running on the Compose network?). ";
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      OutboundHttpLogger.logFailure("GET", uri, ms, stack, hint + msg);
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", false,
              "error", hint + msg);
      logStackPingResult(result);
      return result;
    } catch (RestClientException e) {
      long ms = (System.nanoTime() - start) / 1_000_000L;
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      OutboundHttpLogger.logFailure("GET", uri, ms, stack, msg);
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", false,
              "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
      logStackPingResult(result);
      return result;
    }
  }

  public Map<String, Object> pingAll() {
    List<Map<String, Object>> results =
        List.of(
            pingPostgres(),
            pingRedis(),
            pingRust(),
            pingPython(),
            pingPrometheus(),
            pingGrafana(),
            pingElasticsearch(),
            pingKibana(),
            pingReactNode());
    long okCount = results.stream().filter(r -> Boolean.TRUE.equals(r.get("ok"))).count();
    log.info(
        "Dashboard UI stack ping-all completed",
        kv("ui_event", "dashboard.ui"),
        kv("action", "stack-ping-all"),
        kv("serviceCount", results.size()),
        kv("okCount", okCount),
        kv("failCount", results.size() - okCount));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("results", results);
    return body;
  }

  private void logStackPingResult(Map<String, Object> result) {
    Object status = result.get("status");
    Object error = result.get("error");
    if (Boolean.TRUE.equals(result.get("ok"))) {
      log.info(
          "Dashboard UI stack ping completed",
          kv("ui_event", "dashboard.ui"),
          kv("action", "stack-ping"),
          kv("target", result.get("stack")),
          kv("ok", true),
          kv("status", status),
          kv("url", result.get("url")));
    } else {
      log.warn(
          "Dashboard UI stack ping failed",
          kv("ui_event", "dashboard.ui"),
          kv("action", "stack-ping"),
          kv("target", result.get("stack")),
          kv("ok", false),
          kv("status", status),
          kv("url", result.get("url")),
          kv("error", error));
    }
  }

  public Map<String, Object> pingPostgres() {
    String host = envOrDefault("DB_HOST", "127.0.0.1");
    String port = envOrDefault("DB_PORT", "5432");
    String db = envOrDefault("DB_NAME", "demo");
    String user = envOrDefault("DB_USERNAME", "postgres");
    String password = envOrDefault("DB_PASSWORD", "postgres");
    String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
    try (Connection conn = DriverManager.getConnection(url, user, password);
        PreparedStatement ps = conn.prepareStatement("SELECT 1");
        ResultSet rs = ps.executeQuery()) {
      if (rs.next()) {
        Map<String, Object> result =
            Map.of(
                "stack", "postgres",
                "url", url,
                "ok", true);
        logStackPingResult(result);
        return result;
      }
      Map<String, Object> result =
          Map.of(
              "stack", "postgres",
              "url", url,
              "ok", false,
              "error", "SELECT 1 returned no row");
      logStackPingResult(result);
      return result;
    } catch (SQLException e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      Map<String, Object> result =
          Map.of(
              "stack", "postgres",
              "url", url,
              "ok", false,
              "error", "Cannot connect to Postgres. " + msg);
      logStackPingResult(result);
      return result;
    }
  }

  public Map<String, Object> pingRedis() {
    String host = envOrDefault("REDIS_HOST", "127.0.0.1");
    String port = envOrDefault("REDIS_PORT", "6379");
    String url = "redis://" + host + ":" + port;
    try {
      String pong = redisConnectionFactory.getConnection().ping();
      boolean ok = pong != null && pong.equalsIgnoreCase("PONG");
      Map<String, Object> result =
          ok
              ? Map.of(
                  "stack", "redis",
                  "url", url,
                  "ok", true,
                  "pong", pong)
              : Map.of(
                  "stack", "redis",
                  "url", url,
                  "ok", false,
                  "error", "unexpected PING response: " + pong);
      logStackPingResult(result);
      return result;
    } catch (RuntimeException e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      Map<String, Object> result =
          Map.of(
              "stack", "redis",
              "url", url,
              "ok", false,
              "error", "Cannot connect to Redis. " + msg);
      logStackPingResult(result);
      return result;
    }
  }

  public Map<String, Object> pingRust() {
    return emptyGet("rust", properties.getRustBaseUrl());
  }

  public Map<String, Object> pingPython() {
    return emptyGet("python", properties.getPythonBaseUrl());
  }

  public Map<String, Object> pingPrometheus() {
    return emptyGet("prometheus", properties.getPrometheusBaseUrl());
  }

  public Map<String, Object> pingGrafana() {
    return emptyGet("grafana", properties.getGrafanaBaseUrl());
  }

  public Map<String, Object> pingElasticsearch() {
    return emptyGet("elasticsearch", properties.getElasticsearchBaseUrl());
  }

  public Map<String, Object> pingKibana() {
    return emptyGet("kibana", properties.getKibanaBaseUrl());
  }

  public Map<String, Object> pingReactNode() {
    String base = properties.getReactNodeBaseUrl().trim().replaceAll("/+$", "");
    return emptyGet("react-node", base + "/api/health");
  }

  private static String envOrDefault(String key, String fallback) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String normalizeRoot(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://127.0.0.1/";
    }
    String t = baseUrl.trim();
    try {
      URI uri = URI.create(t);
      String path = uri.getPath();
      if (path != null && !path.isEmpty() && !"/".equals(path)) {
        return t;
      }
    } catch (IllegalArgumentException ignored) {
      // fall through
    }
    return t.endsWith("/") ? t : t + "/";
  }
}
