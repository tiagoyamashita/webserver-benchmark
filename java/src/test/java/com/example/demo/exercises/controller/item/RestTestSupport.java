package com.example.demo.exercises.controller.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared fixtures for {@link com.example.demo.exercises.controller.ItemController} MockMvc tests
 * (one class per method in this package).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class RestTestSupport {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  protected long createItemAndReturnId(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(root.get("id").isIntegralNumber()).isTrue();
    return root.get("id").longValue();
  }
}
