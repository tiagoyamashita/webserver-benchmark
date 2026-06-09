package com.example.demo.exercises.controller;

import com.example.demo.exercises.db.User;
import com.example.demo.exercises.db.UserRepository;
import com.example.demo.exercises.validation.CreateUserRequest;
import jakarta.validation.Valid;
import java.util.List;
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

  private final UserRepository users;

  public UserController(UserRepository users) {
    this.users = users;
  }

  @GetMapping
  public List<UserResponse> list() {
    return users.findAll().stream().map(UserResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest body) {
    User saved = users.save(new User(body.name(), body.email()));
    return UserResponse.from(saved);
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
