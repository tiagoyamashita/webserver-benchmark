package com.example.demo.web;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tests")
public class TestSourceController {

  private final TestSourceService testSourceService;

  public TestSourceController(TestSourceService testSourceService) {
    this.testSourceService = testSourceService;
  }

  @GetMapping(value = "/source", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TestSourceService.TestSourcePayload> getSource(
      @RequestParam("className") String className) {
    Optional<TestSourceService.TestSourcePayload> payload =
        testSourceService.readTestClassSource(className);
    return payload
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }
}
