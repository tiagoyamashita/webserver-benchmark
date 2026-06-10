package com.example.demo.exercises.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import com.example.demo.exercises.validation.CreateUserRequest;
import com.example.demo.exercises.validation.UpdateUserRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST CRUD API for {@link com.example.demo.exercises.db.User}. */
@Tag(name = "Users", description = "CRUD for the shared PostgreSQL `users` table")
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

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
    log.info(
        "UserController.getById request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "GET"),
        kv("path", "/api/users/{id}"),
        kv("id", id));
    var found =
        users.findById(id).map(UserResponse::from).map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "UserController.getById not found",
          kv("source", SOURCE),
          kv("id", id));
      return ResponseEntity.notFound().build();
    }
    UserResponse response = found.get().getBody();
    log.info(
        "UserController.getById succeeded",
        kv("source", SOURCE),
        kv("id", id),
        kv("email", response != null ? response.email() : null));
    return found.get();
  }

  @PutMapping("/{id}")
  public ResponseEntity<UserResponse> replace(
      @PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
    log.info(
        "UserController.replace request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "PUT"),
        kv("path", "/api/users/{id}"),
        kv("id", id),
        kv("name", body.name()),
        kv("email", body.email()));
    var found =
        users
            .findById(id)
            .map(
                entity -> {
                  entity.setName(body.name());
                  entity.setEmail(body.email());
                  return UserResponse.from(users.save(entity));
                })
            .map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "UserController.replace not found",
          kv("source", SOURCE),
          kv("id", id),
          kv("name", body.name()),
          kv("email", body.email()));
      return ResponseEntity.notFound().build();
    }
    UserResponse response = found.get().getBody();
    log.info(
        "UserController.replace succeeded",
        kv("source", SOURCE),
        kv("id", id),
        kv("email", response != null ? response.email() : null));
    return found.get();
  }

  @PatchMapping("/{id}")
  public ResponseEntity<UserResponse> update(
      @PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
    log.info(
        "UserController.update request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "PATCH"),
        kv("path", "/api/users/{id}"),
        kv("id", id),
        kv("name", body.name()),
        kv("email", body.email()));
    var found =
        users
            .findById(id)
            .map(
                entity -> {
                  entity.setName(body.name());
                  entity.setEmail(body.email());
                  return UserResponse.from(users.save(entity));
                })
            .map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "UserController.update not found",
          kv("source", SOURCE),
          kv("id", id),
          kv("name", body.name()),
          kv("email", body.email()));
      return ResponseEntity.notFound().build();
    }
    UserResponse response = found.get().getBody();
    log.info(
        "UserController.update succeeded",
        kv("source", SOURCE),
        kv("id", id),
        kv("email", response != null ? response.email() : null));
    return found.get();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    log.info(
        "UserController.delete request received",
        kv("source", SOURCE),
        kv("controller", "UserController"),
        kv("method", "DELETE"),
        kv("path", "/api/users/{id}"),
        kv("id", id));
    if (!users.existsById(id)) {
      log.warn(
          "UserController.delete not found",
          kv("source", SOURCE),
          kv("id", id));
      return ResponseEntity.notFound().build();
    }
    users.deleteById(id);
    log.info(
        "UserController.delete succeeded",
        kv("source", SOURCE),
        kv("id", id));
    return ResponseEntity.noContent().build();
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
