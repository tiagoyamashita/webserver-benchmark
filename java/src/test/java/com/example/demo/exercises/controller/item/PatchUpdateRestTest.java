package com.example.demo.exercises.controller.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PatchUpdateRestTest extends RestTestSupport {

  @Test
  void patch_update_returns200() throws Exception {
    long id = createItemAndReturnId("before-patch");
    mockMvc
        .perform(
            patch("/api/items/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"after-patch\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("after-patch"));
  }
}
