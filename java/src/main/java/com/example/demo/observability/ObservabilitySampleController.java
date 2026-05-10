package com.example.demo.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class ObservabilitySampleController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilitySampleController.class);

    /** Generates one INFO log line — useful to verify Filebeat / ELK wiring. */
    @GetMapping("/sample-log")
    public String sampleLog() {
        log.info("Observability sample event (JSON log file -> Filebeat -> Logstash -> Elasticsearch)");
        return "logged";
    }
}
