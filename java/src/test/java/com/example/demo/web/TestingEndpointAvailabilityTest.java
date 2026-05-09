package com.example.demo.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies {@code /testingendpoint} is mounted and reports which HTTP methods succeed, logging to
 * the console (Surefire: see {@code target/surefire-reports/*.txt} or IDE run output).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TestingEndpointAvailabilityTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void testingEndpoint_isAvailable_andLogsSupportedMethods() throws Exception {
    String path = TestingEndpointController.PATH;

    MvcResult optionsResult = mockMvc.perform(options(path)).andReturn();
    int optionsStatus = optionsResult.getResponse().getStatus();
    String allowHeader = optionsResult.getResponse().getHeader(HttpHeaders.ALLOW);
    System.out.println("[testingendpoint] OPTIONS status=" + optionsStatus);
    System.out.println("[testingendpoint] Allow header: " + allowHeader);

    assertThat(optionsStatus).isBetween(200, 299);
    assertThat(allowHeader).isNotBlank();
    String allowUpper = allowHeader.toUpperCase();
    for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
      assertThat(allowUpper).contains(method);
    }

    List<String> succeeding = new ArrayList<>();
    if (is2xx(mockMvc.perform(get(path)).andReturn())) {
      succeeding.add("GET");
    }
    if (is2xx(
        mockMvc
            .perform(
                post(path)
                    .contentType(TestingEndpointController.json())
                    .content(TestingEndpointController.emptyJsonBody()))
            .andReturn())) {
      succeeding.add("POST");
    }
    if (is2xx(
        mockMvc
            .perform(
                put(path)
                    .contentType(TestingEndpointController.json())
                    .content(TestingEndpointController.emptyJsonBody()))
            .andReturn())) {
      succeeding.add("PUT");
    }
    if (is2xx(
        mockMvc
            .perform(
                patch(path)
                    .contentType(TestingEndpointController.json())
                    .content(TestingEndpointController.emptyJsonBody()))
            .andReturn())) {
      succeeding.add("PATCH");
    }
    if (is2xx(mockMvc.perform(delete(path)).andReturn())) {
      succeeding.add("DELETE");
    }

    System.out.println("[testingendpoint] Methods with 2xx response: " + succeeding);
    assertThat(succeeding).containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE");
  }

  private static boolean is2xx(MvcResult result) {
    int status = result.getResponse().getStatus();
    return status >= 200 && status < 300;
  }
}
