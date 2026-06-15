from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    gguf_path: Path | None
    n_ctx: int
    n_gpu_layers: int
    max_tokens_default: int
    log_path: Path

    @property
    def model_ready(self) -> bool:
        return self.gguf_path is not None and self.gguf_path.is_file()


def load_settings() -> Settings:
    raw_path = os.environ.get("AI_MODEL_GGUF_PATH", "").strip()
    gguf_path = Path(raw_path).expanduser() if raw_path else None
    if gguf_path is not None:
        gguf_path = gguf_path.resolve()

    return Settings(
        host=os.environ.get("AI_MODEL_HOST", "0.0.0.0"),
        port=int(os.environ.get("AI_MODEL_PORT", "8095")),
        gguf_path=gguf_path,
        n_ctx=int(os.environ.get("AI_MODEL_N_CTX", "2048")),
        n_gpu_layers=int(os.environ.get("AI_MODEL_N_GPU_LAYERS", "0")),
        max_tokens_default=int(os.environ.get("AI_MODEL_MAX_TOKENS", "256")),
        log_path=Path(os.environ.get("LOG_PATH", "logs")).expanduser(),
    )
