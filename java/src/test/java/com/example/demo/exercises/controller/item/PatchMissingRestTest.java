package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PatchMissingRestTest extends RestTestSupport {

  @Test
  void patch_returns404_whenMissing() throws Exception {
    mockMvc
        .perform(
            patch("/api/items/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"y\"}"))
        .andExpect(status().isNotFound());
  }
}
