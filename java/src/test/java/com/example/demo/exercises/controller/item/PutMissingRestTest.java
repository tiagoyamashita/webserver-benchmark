package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PutMissingRestTest extends RestTestSupport {

  @Test
  void put_returns404_whenMissing() throws Exception {
    mockMvc
        .perform(
            put("/api/items/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isNotFound());
  }
}
