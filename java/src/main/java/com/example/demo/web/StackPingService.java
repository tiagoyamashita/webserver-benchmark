package com.example.demo.web;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

  public StackPingService(
      @Qualifier("stackPingRestClient") RestClient stackPingRestClient,
      StackPingProperties properties) {
    this.restClient = stackPingRestClient;
    this.properties = properties;
  }

  public Map<String, Object> emptyGet(String stack, String baseUrl) {
    String root = normalizeRoot(baseUrl);
    try {
      ResponseEntity<Void> res =
          restClient.get().uri(URI.create(root)).retrieve().toBodilessEntity();
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", true,
              "status", res.getStatusCode().value());
      logStackPingResult(result);
      return result;
    } catch (RestClientResponseException e) {
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
      String hint =
          "Cannot connect (is the container running on the Compose network?). ";
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      Map<String, Object> result =
          Map.of(
              "stack", stack,
              "url", root,
              "ok", false,
              "error", hint + msg);
      logStackPingResult(result);
      return result;
    } catch (RestClientException e) {
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
            pingRust(),
            pingPython(),
            pingPrometheus(),
            pingGrafana(),
            pingElasticsearch(),
            pingKibana(),
            pingReachUi());
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

  public Map<String, Object> pingReachUi() {
    return emptyGet("reach-ui", properties.getReachUiBaseUrl());
  }

  private static String normalizeRoot(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://127.0.0.1/";
    }
    String t = baseUrl.trim();
    return t.endsWith("/") ? t : t + "/";
  }
}
