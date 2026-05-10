package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GetOneMissingRestTest extends RestTestSupport {

  @Test
  void getOne_returns404_whenMissing() throws Exception {
    mockMvc
        .perform(get("/api/items/999999").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }
}
