package com.example.demo.web;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo endpoint used by {@link TestingEndpointAvailabilityTest} to verify routing and HTTP verbs.
 */
@RestController
public class TestingEndpointController {

  public static final String PATH = "/testingendpoint";

  @GetMapping(PATH)
  public String get() {
    return "testingendpoint GET";
  }

  @PostMapping(PATH)
  public String post(@RequestBody(required = false) String body) {
    return "testingendpoint POST";
  }

  @PutMapping(PATH)
  public String put(@RequestBody(required = false) String body) {
    return "testingendpoint PUT";
  }

  @PatchMapping(PATH)
  public String patch(@RequestBody(required = false) String body) {
    return "testingendpoint PATCH";
  }

  @DeleteMapping(PATH)
  public String delete() {
    return "testingendpoint DELETE";
  }

  /**
   * Declares supported methods for {@code OPTIONS /testingendpoint} so clients can discover verbs
   * without probing.
   */
  @RequestMapping(value = PATH, method = RequestMethod.OPTIONS)
  public ResponseEntity<Void> options() {
    return ResponseEntity.ok()
        .allow(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE,
            HttpMethod.OPTIONS)
        .build();
  }

  /** Small JSON body helper for verb probes in tests. */
  public static String emptyJsonBody() {
    return "{}";
  }

  /** Content-Type for JSON probes. */
  public static MediaType json() {
    return MediaType.APPLICATION_JSON;
  }
}
