package com.example.demo.exercises.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import com.example.demo.exercises.validation.CreateUserRequest;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/exercises/controller/UserController.java";
  private static final Logger log = LoggerFactory.getLogger(UserController.class);

  private final UserRepository users;

  public UserController(UserRepository users) {
    this.users = users;
  }

  @GetMapping
  public List<UserResponse> list() {
    log.info(
        "UserController.list request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "GET"),
        kv("path", "/api/users"));
    List<UserResponse> result =
        users.findAll().stream()
            .sorted(Comparator.comparing(User::getId))
            .map(UserResponse::from)
            .toList();
    log.info(
        "UserController.list succeeded",
        kv("source", SOURCE),
        kv("count", result.size()));
    log.trace("UserController.list result", kv("source", SOURCE), kv("users", result));
    return result;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest body) {
    log.info(
        "UserController.create request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "POST"),
        kv("path", "/api/users"),
        kv("name", body.name()),
        kv("email", body.email()));
    User saved = users.save(new User(body.name(), body.email()));
    UserResponse response = UserResponse.from(saved);
    log.info(
        "UserController.create succeeded",
        kv("source", SOURCE),
        kv("id", saved.getId()),
        kv("name", saved.getName()),
        kv("email", saved.getEmail()));
    return response;
  }

  public record UserResponse(Long id, String name, String email, String createdAt) {
    static UserResponse from(User entity) {
      return new UserResponse(
          entity.getId(),
          entity.getName(),
          entity.getEmail(),
          entity.getCreatedAt().toString());
    }
  }
}
