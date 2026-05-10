package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PostCreateRestTest extends RestTestSupport {

  @Test
  void post_create_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"alpha\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("alpha"))
        .andExpect(jsonPath("$.createdAt").exists());
  }
}
