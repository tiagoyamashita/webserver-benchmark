package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DeleteMissingRestTest extends RestTestSupport {

  @Test
  void delete_returns404_whenMissing() throws Exception {
    mockMvc.perform(delete("/api/items/999999")).andExpect(status().isNotFound());
  }
}
