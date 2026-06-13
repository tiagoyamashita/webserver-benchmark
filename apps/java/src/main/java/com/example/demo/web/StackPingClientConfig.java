package com.example.demo.web;

import com.example.demo.observability.RequestIdRelay;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class StackPingClientConfig {

  @Bean(name = "stackPingRestClient")
  public RestClient stackPingRestClient() {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofSeconds(15));
    return RestClient.builder()
        .requestFactory(factory)
        .requestInterceptor(
            (request, body, execution) -> {
              request
                  .getHeaders()
                  .set(RequestIdRelay.REQUEST_ID_HEADER, RequestIdRelay.resolveOutboundRequestId());
              request.getHeaders().set(RequestIdRelay.ORIGIN_HEADER, RequestIdRelay.SERVICE);
              return execution.execute(request, body);
            })
        .build();
  }
}
