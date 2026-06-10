---
name: controller-logging
description: >-
  Add structured controller logging in the exercises stack: request entry with source path,
  parameter capture for replication, success exit lines, and INFO/WARN/ERROR/TRACE levels.
  Use when improving logs, debugging ELK/Kibana, or adding logging to Java/Python/Rust controllers.
---

# Controller logging (exercises stack)

## Rules

1 - log on controller receiving request log the request and the packge to file or full path in src for the file as the controller.

2 - log errors and the parameters passed so we can try to replicate later 

3 - log at the end of each function if everything succeeded

4 - use appropriate levels of loggin INFO, WARN, ERROR ,TRACE

## Level guide

| Level | When |
|-------|------|
| **TRACE** | Verbose payloads (full bodies, large collections); disabled in prod by default |
| **INFO** | Request received, successful completion, normal business outcomes |
| **WARN** | Expected failures (404, validation rejection, downstream non-2xx) — include params to replay |
| **ERROR** | Unexpected exceptions, data corruption, cannot complete after retries |

## Java (Spring) pattern

Use SLF4J + `net.logstash.logback.argument.StructuredArguments.kv` (already on classpath).

Each controller declares a **source** constant — path under `src/main/java/`:

```java
private static final String SOURCE =
    "src/main/java/com/example/demo/exercises/controller/ItemController.java";
private static final Logger log = LoggerFactory.getLogger(ItemController.class);
```

### Request received (INFO)

Log at the **start** of every public handler:

```java
log.info(
    "ItemController.create request received",
    kv("source", SOURCE),
    kv("controller", "ItemController"),
    kv("method", "POST"),
    kv("path", "/api/items"),
    kv("name", body.name()));
```

Include path variables, query params, and safe body fields — enough to replay with `curl`.

### Success (INFO)

Log at the **end** before `return` when the handler completes normally:

```java
log.info(
    "ItemController.create succeeded",
    kv("source", SOURCE),
    kv("id", saved.getId()),
    kv("name", saved.getName()));
```

### Expected failure (WARN)

404, blank input, downstream HTTP errors — log params, no stack trace unless useful:

```java
log.warn(
    "ItemController.getById not found",
    kv("source", SOURCE),
    kv("id", id));
```

### Unexpected failure (ERROR)

Wrap only when the controller catches; otherwise use `@RestControllerAdvice`:

```java
log.error(
    "ItemController.create failed",
    kv("source", SOURCE),
    kv("name", body.name()),
    kv("error", e.getMessage()),
    e);
```

### TRACE

Optional full JSON bodies or large result sets:

```java
log.trace("ItemController.list result", kv("source", SOURCE), kv("items", result));
```

## Checklist per handler

- [ ] `SOURCE` constant on the controller class
- [ ] INFO on request received (method, path, params)
- [ ] INFO on success (outcome ids/counts/status)
- [ ] WARN on expected failures with replay params
- [ ] ERROR on unexpected exceptions with params + stack trace
- [ ] TRACE only for noisy detail

## Do not duplicate

- `HttpRequestLoggingFilter` (`observability` profile) already logs HTTP method/path/status/ms — controller logs add **business context** and **handler source file**, not another access log per line.

## Python (Flask)

Helper: `exercises.web.controller_logging` — `log_received`, `log_succeeded`, `log_warn`, `log_error`, `log_trace`.

```python
SOURCE = "src/exercises/web/items_api.py"
_LOG = logging.getLogger(__name__)

log_received(_LOG, "create_item", SOURCE, "POST", "/api/items", name=name)
# ... work ...
log_succeeded(_LOG, "create_item", SOURCE, id=body["id"], name=body["name"])
```

JSON file logs (`observability_logging.py`) include all `extra={...}` fields in ELK.

## Rust (Axum)

Use `tracing` with `source`, `controller`, `method`, `path`, and replay params:

```rust
const SOURCE: &str = "src/items.rs";

tracing::info!(
    source = SOURCE,
    controller = "create_item",
    method = "POST",
    path = "/api/items",
    name = %name,
    "create_item request received"
);
tracing::info!(source = SOURCE, controller = "create_item", id = row.id, "create_item succeeded");
tracing::warn!(source = SOURCE, controller = "create_item", reason = "blank-name", "create_item validation failed");
tracing::error!(source = SOURCE, controller = "create_item", error = %e, "create_item failed");
tracing::trace!(source = SOURCE, controller = "list_items", items = ?responses, "list_items result");
```

## React Node (Express)

Helper: `server/controller-logging.ts` — `logReceived`, `logSucceeded`, `logWarn`, `logError`, `logTrace`.

```typescript
const SOURCE = "server/app.ts";

logReceived("createItem", SOURCE, "POST", "/api/items", { name, request_id: req.requestId });
// ... proxy to Java ...
logSucceeded("createItem", SOURCE, { id: item.id, name: item.name, request_id: req.requestId });
```

Logs emit structured JSON to stdout and to `${LOG_PATH}/demo-app.json.log` when `EXERCISES_OBSERVABILITY=true`.

## Verify in Kibana

Filter Java app logs by `source` or `controller` field. A create-item flow should show:

1. `… request received` (INFO)
2. `… succeeded` (INFO) — or WARN/ERROR with the same params
