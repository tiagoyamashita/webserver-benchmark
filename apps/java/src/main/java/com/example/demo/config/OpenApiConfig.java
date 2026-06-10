package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI exercisesOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Exercises Java API")
                .version("1.0")
                .description(
                    "REST CRUD for users and items (`/api/users`, `/api/items`)."));
  }
}
