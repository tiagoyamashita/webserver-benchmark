package com.example.demo.exercises.controller;

import com.example.demo.exercises.db.Item;
import com.example.demo.exercises.db.ItemRepository;
import com.example.demo.exercises.validation.CreateItemRequest;
import com.example.demo.exercises.validation.UpdateItemRequest;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * REST CRUD API for {@link com.example.demo.exercises.db.Item}. Integration-tested with MockMvc +
 * JPA; see {@code com.example.demo.exercises.controller.ItemController} (test package).
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

  private final ItemRepository items;

  public ItemController(ItemRepository items) {
    this.items = items;
  }

  @GetMapping
  public List<ItemResponse> list() {
    return items.findAll().stream().map(ItemResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@Valid @RequestBody CreateItemRequest body) {
    Item saved = items.save(new Item(body.name()));
    return ItemResponse.from(saved);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ItemResponse> getById(@PathVariable Long id) {
    return items
        .findById(id)
        .map(ItemResponse::from)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}")
  public ResponseEntity<ItemResponse> replace(
      @PathVariable Long id, @Valid @RequestBody UpdateItemRequest body) {
    return items
        .findById(id)
        .map(
            entity -> {
              entity.setName(body.name());
              return ItemResponse.from(items.save(entity));
            })
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ItemResponse> updateName(
      @PathVariable Long id, @Valid @RequestBody UpdateItemRequest body) {
    return items
        .findById(id)
        .map(
            entity -> {
              entity.setName(body.name());
              return ItemResponse.from(items.save(entity));
            })
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!items.existsById(id)) {
      return ResponseEntity.notFound().build();
    }
    items.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  public record ItemResponse(Long id, String name, String createdAt) {
    static ItemResponse from(Item e) {
      return new ItemResponse(e.getId(), e.getName(), e.getCreatedAt().toString());
    }
  }
}
