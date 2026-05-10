package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PutReplaceRestTest extends RestTestSupport {

  @Test
  void put_replace_returns200() throws Exception {
    long id = createItemAndReturnId("before");
    mockMvc
        .perform(
            put("/api/items/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"after-put\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.name").value("after-put"));
  }
}
