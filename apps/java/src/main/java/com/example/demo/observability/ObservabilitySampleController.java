package com.example.demo.observability;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/observability")
public class ObservabilitySampleController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilitySampleController.class);

    /** Lightweight health check for Connectivity iframe reachability reporting. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "ok", true);
    }

    /** Generates one INFO log line — useful to verify Filebeat / ELK wiring. */
    @GetMapping("/sample-log")
    public String sampleLog() {
        log.info("Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)");
        return "logged";
    }
}
