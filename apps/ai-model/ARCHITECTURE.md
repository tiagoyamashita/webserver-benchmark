# AI Model — How it works

This app runs a **local GGUF model on your PC**. It does **not** use Ollama, OpenAI, or a separate inference daemon. Inference happens **inside the Python process** via **`llama-cpp-python`** (bindings for [llama.cpp](https://github.com/ggerganov/llama.cpp)).

## What runs the AI?

| Tool | Used here? | Role |
|------|------------|------|
| **llama-cpp-python** | **Yes** | Python library; loads `.gguf` and runs token generation in-process |
| **llama.cpp** | **Yes (under the hood)** | C++ engine that llama-cpp-python wraps |
| **Ollama** | No | Separate app with its own server, model pull UI, and REST API |
| **OpenAI / cloud API** | No | Remote hosted models |

**Ollama vs this app (same idea, different packaging):**

```mermaid
flowchart LR
  subgraph ollama["Ollama (not used here)"]
    OClient[Client / UI] --> OServer[Ollama server :11434]
    OServer --> OllamaCpp[llama.cpp]
    OllamaCpp --> OGguf[(models in ~/.ollama)]
  end

  subgraph exercises["Exercises ai-model"]
    EClient[Client / curl / future dashboard] --> EFastAPI[FastAPI :8095]
    EFastAPI --> ELlamaPy[llama-cpp-python]
    ELlamaPy --> ECpp[llama.cpp]
    ECpp --> EGguf[(apps/ai-model/models/*.gguf)]
  end
```

Both ultimately use **llama.cpp + GGUF**. Ollama is a standalone product; we embed inference in our own small FastAPI service.

---

## Current architecture (today)

There is **no dashboard UI wired yet**. You call the service directly (curl, Postman, or another app you add later).

```mermaid
sequenceDiagram
  actor User
  participant Client as Client<br/>(curl / Postman / script)
  participant FastAPI as ai-model<br/>FastAPI :8095
  participant Engine as LlamaCppEngine
  participant Llama as llama-cpp-python
  participant GGUF as GGUF file<br/>models/*.gguf

  User->>Client: Type prompt
  Client->>FastAPI: POST /api/generate<br/>{"prompt","max_tokens"}
  FastAPI->>Engine: generate(prompt, max_tokens)
  Engine->>Llama: create_completion(...)
  Llama->>GGUF: Read weights from disk
  GGUF-->>Llama: tensors
  Llama-->>Engine: token stream → text
  Engine-->>FastAPI: completion string
  FastAPI-->>Client: {"text","backend":"llama-cpp","elapsedMs"}
  Client-->>User: Show answer
```

```mermaid
flowchart TB
  subgraph host["Your PC (Windows or Podman container)"]
    direction TB

    subgraph process["Python process — ai-model-serve"]
      Uvicorn[uvicorn]
      App[FastAPI app.py]
      Config[config.py<br/>AI_MODEL_GGUF_PATH]
      Build[engine.py build_engine]
      Stub[StubEngine<br/>no model loaded]
      LlamaEng[LlamaCppEngine]
      Lib[llama-cpp-python]
      Uvicorn --> App
      App --> Config
      App --> Build
      Build -->|no .gguf| Stub
      Build -->|GGUF present| LlamaEng
      LlamaEng --> Lib
    end

    Disk[(models/qwen2.5-coder-7b-instruct-q4_k_m.gguf)]
    Lib --> Disk
  end

  Client((HTTP client)) -->|GET /health| App
  Client -->|POST /api/generate| App
  Client -->|GET /metrics| App
```

---

## Typical future flow (UI → app server → ai-model)

When you hook this into the exercises stack, the pattern would look like this:

```mermaid
sequenceDiagram
  actor User
  participant UI as Browser UI<br/>(Java / React / Python dashboard)
  participant App as App server<br/>(e.g. Java :8080 or React :5174)
  participant LM as ai-model<br/>Python FastAPI :8095
  participant AI as llama-cpp-python + GGUF

  User->>UI: Enter coding question
  UI->>App: POST /api/.../ask<br/>(your new endpoint)
  App->>LM: POST http://ai-model:8095/api/generate
  LM->>AI: run inference
  AI-->>LM: generated text
  LM-->>App: JSON response
  App-->>UI: formatted answer
  UI-->>User: Display in page
```

```mermaid
flowchart LR
  subgraph browser["Browser"]
    UI[Dashboard UI]
  end

  subgraph apps["Exercises apps (optional middle tier)"]
    Java[Java :8080]
    Python[Python :5000]
    React[React Node :5174]
  end

  subgraph lm["ai-model (this app)"]
    API[FastAPI]
    Infer[llama-cpp-python]
  end

  File[(GGUF on disk)]

  UI -->|future| Java
  UI -->|future| React
  Java -.->|HTTP POST /api/generate| API
  Python -.->|HTTP POST /api/generate| API
  React -.->|HTTP POST /api/generate| API
  API --> Infer
  Infer --> File
```

**Today:** dashed arrows are not implemented — call **ai-model directly** on port **8095**.

---

## Startup lifecycle

```mermaid
stateDiagram-v2
  [*] --> LoadEnv: ai-model-serve starts
  LoadEnv --> CheckPath: read AI_MODEL_GGUF_PATH
  CheckPath --> StubMode: file missing
  CheckPath --> LoadModel: .gguf exists
  LoadModel --> ImportLlama: pip install llama-cpp-python?
  ImportLlama --> StubMode: import fails
  ImportLlama --> Ready: Llama(model_path=...)
  StubMode --> Degraded: /health status degraded<br/>POST /api/generate → 503
  Ready --> Serving: /health status ok<br/>inference works
  Serving --> Ready: each POST /api/generate
```

On first request after startup, the model is **already loaded in memory** (load happens at process start, not per request).

---

## HTTP API (what clients call)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/health` | Service + model ready state |
| `GET` | `/api/model/info` | Backend (`llama-cpp` or `stub`), GGUF path, GPU layers |
| `POST` | `/api/generate` | `{ "prompt": "...", "max_tokens": 128 }` → `{ "text", "backend", "elapsedMs" }` |
| `GET` | `/metrics` | Prometheus counters |

---

## Deploy modes

```mermaid
flowchart TB
  subgraph local["Host dev — run_local.ps1"]
    Win[Windows]
    Venv[.venv + llama-cpp-python]
    Win --> Venv
    Venv --> Serve1[127.0.0.1:8095]
  end

  subgraph podman["Podman — profile ai-model"]
    Container[ai-model container]
    Mount[./apps/ai-model/models mounted]
    Container --> Mount
    Container --> Serve2[0.0.0.0:8095 → host :8095]
  end

  GGUF[(GGUF file)]
  Mount --> GGUF
  Venv --> GGUF
```

---

## File map (code path)

```
scripts/run_local.ps1          → starts ai-model-serve
src/ai_model/cli.py            → uvicorn entrypoint
src/ai_model/app.py            → FastAPI routes (/api/generate)
src/ai_model/engine.py         → LlamaCppEngine → llama_cpp.Llama
src/ai_model/config.py         → AI_MODEL_GGUF_PATH, N_GPU_LAYERS, …
models/*.gguf                  → weights (Qwen, SmolLM, etc.)
```

---

## Quick mental model

1. **Download** a `.gguf` file to `models/`.
2. **Start** `ai-model-serve` (Python + FastAPI on port 8095).
3. At startup, **llama-cpp-python loads the GGUF into RAM** (and optionally GPU).
4. **Clients send HTTP JSON** with a prompt; Python runs inference and returns text.
5. **No Ollama** — you own the whole stack: one process, one file, one API.
