package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class DeleteRestTest extends RestTestSupport {

  @Test
  void delete_returns204() throws Exception {
    long id = createItemAndReturnId("to-delete");
    mockMvc.perform(delete("/api/items/" + id)).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/items/" + id)).andExpect(status().isNotFound());
  }
}
