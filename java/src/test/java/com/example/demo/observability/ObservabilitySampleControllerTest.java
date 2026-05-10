package com.example.demo.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ObservabilitySampleController.class)
class ObservabilitySampleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sampleLog_returnsLoggedPayload() throws Exception {
        mockMvc
                .perform(get("/api/observability/sample-log"))
                .andExpect(status().isOk())
                .andExpect(content().string("logged"));
    }
}
