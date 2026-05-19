package com.example.demo.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stack")
public class StackPingProperties {

  /** Base URL for the Rust dashboard (no trailing slash required). */
  private String rustBaseUrl = "http://127.0.0.1:8082";

  /** Base URL for the Python dashboard. */
  private String pythonBaseUrl = "http://127.0.0.1:5000";

  /** Base URL for the Prometheus UI. */
  private String prometheusBaseUrl = "http://127.0.0.1:9090";

  /** Base URL for Grafana (server-side GET from Java). */
  private String grafanaBaseUrl = "http://127.0.0.1:3000";

  /** Base URL for Elasticsearch (server-side GET from Java). */
  private String elasticsearchBaseUrl = "http://127.0.0.1:9200";

  /** Base URL for Kibana (server-side GET from Java). */
  private String kibanaBaseUrl = "http://127.0.0.1:5601";

  /** Base URL for Reach UI (server-side GET from Java). */
  private String reachUiBaseUrl = "http://127.0.0.1:5174";

  /** Browser links from the host machine (Compose port maps; not container DNS names). */
  private String rustBrowserUrl = "http://127.0.0.1:8082/";

  private String pythonBrowserUrl = "http://127.0.0.1:5000/";
  private String prometheusBrowserUrl = "http://127.0.0.1:9090/";
  private String grafanaBrowserUrl = "http://127.0.0.1:3000/";
  private String elasticsearchBrowserUrl = "http://127.0.0.1:9200/";
  private String kibanaBrowserUrl = "http://127.0.0.1:5601/";
  private String reachUiBrowserUrl = "http://127.0.0.1:5174/";

  public String getRustBaseUrl() {
    return rustBaseUrl;
  }

  public void setRustBaseUrl(String rustBaseUrl) {
    this.rustBaseUrl = rustBaseUrl;
  }

  public String getPythonBaseUrl() {
    return pythonBaseUrl;
  }

  public void setPythonBaseUrl(String pythonBaseUrl) {
    this.pythonBaseUrl = pythonBaseUrl;
  }

  public String getPrometheusBaseUrl() {
    return prometheusBaseUrl;
  }

  public void setPrometheusBaseUrl(String prometheusBaseUrl) {
    this.prometheusBaseUrl = prometheusBaseUrl;
  }

  public String getGrafanaBaseUrl() {
    return grafanaBaseUrl;
  }

  public void setGrafanaBaseUrl(String grafanaBaseUrl) {
    this.grafanaBaseUrl = grafanaBaseUrl;
  }

  public String getElasticsearchBaseUrl() {
    return elasticsearchBaseUrl;
  }

  public void setElasticsearchBaseUrl(String elasticsearchBaseUrl) {
    this.elasticsearchBaseUrl = elasticsearchBaseUrl;
  }

  public String getKibanaBaseUrl() {
    return kibanaBaseUrl;
  }

  public void setKibanaBaseUrl(String kibanaBaseUrl) {
    this.kibanaBaseUrl = kibanaBaseUrl;
  }

  public String getReachUiBaseUrl() {
    return reachUiBaseUrl;
  }

  public void setReachUiBaseUrl(String reachUiBaseUrl) {
    this.reachUiBaseUrl = reachUiBaseUrl;
  }

  public String getRustBrowserUrl() {
    return rustBrowserUrl;
  }

  public void setRustBrowserUrl(String rustBrowserUrl) {
    this.rustBrowserUrl = rustBrowserUrl;
  }

  public String getPythonBrowserUrl() {
    return pythonBrowserUrl;
  }

  public void setPythonBrowserUrl(String pythonBrowserUrl) {
    this.pythonBrowserUrl = pythonBrowserUrl;
  }

  public String getPrometheusBrowserUrl() {
    return prometheusBrowserUrl;
  }

  public void setPrometheusBrowserUrl(String prometheusBrowserUrl) {
    this.prometheusBrowserUrl = prometheusBrowserUrl;
  }

  public String getGrafanaBrowserUrl() {
    return grafanaBrowserUrl;
  }

  public void setGrafanaBrowserUrl(String grafanaBrowserUrl) {
    this.grafanaBrowserUrl = grafanaBrowserUrl;
  }

  public String getElasticsearchBrowserUrl() {
    return elasticsearchBrowserUrl;
  }

  public void setElasticsearchBrowserUrl(String elasticsearchBrowserUrl) {
    this.elasticsearchBrowserUrl = elasticsearchBrowserUrl;
  }

  public String getKibanaBrowserUrl() {
    return kibanaBrowserUrl;
  }

  public void setKibanaBrowserUrl(String kibanaBrowserUrl) {
    this.kibanaBrowserUrl = kibanaBrowserUrl;
  }

  public String getReachUiBrowserUrl() {
    return reachUiBrowserUrl;
  }

  public void setReachUiBrowserUrl(String reachUiBrowserUrl) {
    this.reachUiBrowserUrl = reachUiBrowserUrl;
  }
}
