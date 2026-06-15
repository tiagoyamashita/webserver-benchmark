# Exercises — AI Model

Local **AI model** inference service for your PC. Runs as a separate app on port **8095** with an optional Compose profile so the main stack stays unchanged until you opt in.

## Layout

| Path | Purpose |
|------|---------|
| `src/ai_model/` | FastAPI app (`/health`, `/api/generate`, `/metrics`) |
| `models/` | Put a **GGUF** file here (not committed — see `.gitignore`) |
| `requirements.txt` | API server only |
| `requirements-llm.txt` | Adds `llama-cpp-python` for CPU/GPU inference |
| `scripts/` | Local run + model download helpers |

## Recommended model (PC-friendly)

Start with a **sub-1B** instruct GGUF, for example:

- [SmolLM2-360M-Instruct-GGUF](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF) — `Q4_K_M` (~250 MB)

For coding, use the `qwen7b` preset (~4.5 GB).

Copy or symlink the file to:

```text
apps/ai-model/models/<model-name>.gguf
```

The download script saves using the filename from the Hugging Face URL (for example `qwen2.5-coder-7b-instruct-q4_k_m.gguf`).

Or set `AI_MODEL_GGUF_PATH` to any absolute path.

## Run on the host (Windows)

```powershell
cd apps/ai-model
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt -r requirements-llm.txt
pip install -e .
$env:AI_MODEL_GGUF_PATH = (Resolve-Path .\models\*.gguf | Select-Object -First 1)
ai-model-serve
```

Open http://127.0.0.1:8095/health and POST http://127.0.0.1:8095/api/generate:

```json
{ "prompt": "Say hello in one sentence.", "max_tokens": 64 }
```

Helper script (use **one line**, or a backtick `` ` `` at end of line 1 for continuation):

```powershell
.\scripts\download_model.ps1 -Preset qwen7b
```

Presets: `qwen7b`, `qwen3b`, `smol360`. Or pass `-Url` on the **same** command:

```powershell
.\scripts\download_model.ps1 -Url "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf"
```

Then start the server:

```powershell
.\scripts\run_local.ps1
```

## Run in Podman (optional profile)

Does **not** start with a normal `compose up`. Enable explicitly:

```powershell
podman compose -f docker-compose.apps.yml --profile ai-model up -d --build ai-model
```

With dev reload:

```powershell
podman compose -f docker-compose.apps.yml -f docker-compose.dev.yml --profile ai-model up -d --build ai-model
```

Build image **with** llama-cpp inside the container:

```powershell
podman build --build-arg INSTALL_LLM=1 -t exercises-ai-model ./apps/ai-model
```

### GPU on your PC

Set in compose or `.env.apps`:

```env
AI_MODEL_N_GPU_LAYERS=20
```

Requires NVIDIA Container Toolkit / CDI and `nvidia-smi` in the Podman VM. Default is **CPU** (`0` layers).

## Environment

| Variable | Default | Meaning |
|----------|---------|---------|
| `AI_MODEL_HOST` | `0.0.0.0` | Bind address |
| `AI_MODEL_PORT` | `8095` | HTTP port |
| `AI_MODEL_GGUF_PATH` | — | Path to `.gguf` weights |
| `AI_MODEL_N_CTX` | `2048` | Context window |
| `AI_MODEL_N_GPU_LAYERS` | `0` | llama.cpp GPU offload layers |
| `AI_MODEL_MAX_TOKENS` | `256` | Default completion length |
| `LOG_PATH` | `logs` | Log directory mount |

## API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | `ok` when model loaded, else `degraded` |
| `GET` | `/api/model/info` | Backend + GGUF path |
| `POST` | `/api/generate` | `{ "prompt", "max_tokens?" }` → `{ "text", "backend", "elapsedMs" }` |
| `GET` | `/metrics` | Prometheus text format |

## Tests

```powershell
cd apps/ai-model
pip install -e ".[dev]"
pytest
```
