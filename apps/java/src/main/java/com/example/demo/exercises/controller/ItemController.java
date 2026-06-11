package com.example.demo.exercises.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.example.demo.exercises.db.Item;
import com.example.demo.exercises.db.ItemRepository;
import com.example.demo.observability.RequestIdContext;
import com.example.demo.exercises.validation.CreateItemRequest;
import com.example.demo.exercises.validation.UpdateItemRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * REST CRUD API for {@link com.example.demo.exercises.db.Item}. Integration-tested with MockMvc +
 * JPA; see {@code com.example.demo.exercises.controller.ItemController} (test package).
 */
@Tag(name = "Items", description = "CRUD for the shared PostgreSQL `items` table")
@RestController
@RequestMapping("/api/items")
public class ItemController {

  private static final String SOURCE =
      "src/main/java/com/example/demo/exercises/controller/ItemController.java";
  private static final Logger log = LoggerFactory.getLogger(ItemController.class);

  private final ItemRepository items;

  public ItemController(ItemRepository items) {
    this.items = items;
  }

  @GetMapping
  public List<ItemResponse> list() {
    log.info(
        "ItemController.list request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "GET"),
        kv("path", "/api/items"));
    List<ItemResponse> result = items.findAll().stream().map(ItemResponse::from).toList();
    log.info(
        "ItemController.list succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("count", result.size()));
    log.trace(
        "ItemController.list result",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("items", result));
    return result;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@Valid @RequestBody CreateItemRequest body) {
    log.info(
        "ItemController.create request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "POST"),
        kv("path", "/api/items"),
        kv("name", body.name()));
    Item saved = items.save(new Item(body.name()));
    ItemResponse response = ItemResponse.from(saved);
    log.info(
        "ItemController.create succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("id", saved.getId()),
        kv("name", saved.getName()));
    return response;
  }

  @GetMapping("/{id}")
  public ResponseEntity<ItemResponse> getById(@PathVariable Long id) {
    log.info(
        "ItemController.getById request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "GET"),
        kv("path", "/api/items/{id}"),
        kv("id", id));
    var found =
        items
            .findById(id)
            .map(ItemResponse::from)
            .map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "ItemController.getById not found",
          kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
          kv("id", id));
      return ResponseEntity.notFound().build();
    }
    ItemResponse response = found.get().getBody();
    log.info(
        "ItemController.getById succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("id", id),
        kv("name", response != null ? response.name() : null));
    return found.get();
  }

  @PutMapping("/{id}")
  public ResponseEntity<ItemResponse> replace(
      @PathVariable Long id, @Valid @RequestBody UpdateItemRequest body) {
    log.info(
        "ItemController.replace request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "PUT"),
        kv("path", "/api/items/{id}"),
        kv("id", id),
        kv("name", body.name()));
    var found =
        items
            .findById(id)
            .map(
                entity -> {
                  entity.setName(body.name());
                  return ItemResponse.from(items.save(entity));
                })
            .map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "ItemController.replace not found",
          kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
          kv("id", id),
          kv("name", body.name()));
      return ResponseEntity.notFound().build();
    }
    ItemResponse response = found.get().getBody();
    log.info(
        "ItemController.replace succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("id", id),
        kv("name", response != null ? response.name() : null));
    return found.get();
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ItemResponse> updateName(
      @PathVariable Long id, @Valid @RequestBody UpdateItemRequest body) {
    log.info(
        "ItemController.updateName request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "PATCH"),
        kv("path", "/api/items/{id}"),
        kv("id", id),
        kv("name", body.name()));
    var found =
        items
            .findById(id)
            .map(
                entity -> {
                  entity.setName(body.name());
                  return ItemResponse.from(items.save(entity));
                })
            .map(ResponseEntity::ok);
    if (found.isEmpty()) {
      log.warn(
          "ItemController.updateName not found",
          kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
          kv("id", id),
          kv("name", body.name()));
      return ResponseEntity.notFound().build();
    }
    ItemResponse response = found.get().getBody();
    log.info(
        "ItemController.updateName succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("id", id),
        kv("name", response != null ? response.name() : null));
    return found.get();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    log.info(
        "ItemController.delete request received",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("controller", "ItemController"),
        kv("method", "DELETE"),
        kv("path", "/api/items/{id}"),
        kv("id", id));
    if (!items.existsById(id)) {
      log.warn(
          "ItemController.delete not found",
          kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
          kv("id", id));
      return ResponseEntity.notFound().build();
    }
    items.deleteById(id);
    log.info(
        "ItemController.delete succeeded",
        kv("source", SOURCE),
        kv("request_id", RequestIdContext.get()),
        kv("id", id));
    return ResponseEntity.noContent().build();
  }

  public record ItemResponse(Long id, String name, String createdAt) {
    static ItemResponse from(Item e) {
      return new ItemResponse(e.getId(), e.getName(), e.getCreatedAt().toString());
    }
  }
}
