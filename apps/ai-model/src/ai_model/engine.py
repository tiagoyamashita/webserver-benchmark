from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from ai_model.config import Settings


class InferenceEngine(Protocol):
    def info(self) -> dict[str, object]: ...

    def generate(self, prompt: str, max_tokens: int) -> str: ...


@dataclass
class StubEngine:
    settings: Settings
    error: str | None = None

    def info(self) -> dict[str, object]:
        payload: dict[str, object] = {
            "backend": "stub",
            "ready": False,
            "ggufPath": str(self.settings.gguf_path) if self.settings.gguf_path else None,
            "hint": (
                "Set AI_MODEL_GGUF_PATH to a local .gguf file and install llama-cpp-python "
                "(pip install -r requirements-llm.txt)."
            ),
        }
        if self.error:
            payload["error"] = self.error
        return payload

    def generate(self, prompt: str, max_tokens: int) -> str:
        raise RuntimeError(self.info()["hint"])


@dataclass
class LlamaCppEngine:
    settings: Settings
    _llama: object

    def info(self) -> dict[str, object]:
        return {
            "backend": "llama-cpp",
            "ready": True,
            "ggufPath": str(self.settings.gguf_path),
            "nCtx": self.settings.n_ctx,
            "nGpuLayers": self.settings.n_gpu_layers,
        }

    def generate(self, prompt: str, max_tokens: int) -> str:
        completion = self._llama.create_completion(  # type: ignore[attr-defined]
            prompt=prompt,
            max_tokens=max_tokens,
            temperature=0.7,
        )
        choices = completion.get("choices") or []
        if not choices:
            return ""
        text = choices[0].get("text", "")
        return str(text).strip()


def build_engine(settings: Settings) -> InferenceEngine:
    if not settings.model_ready:
        return StubEngine(settings)

    try:
        from llama_cpp import Llama
    except ImportError as exc:
        stub = StubEngine(settings)
        raise RuntimeError(str(stub.info()["hint"])) from exc

    llama = Llama(
        model_path=str(settings.gguf_path),
        n_ctx=settings.n_ctx,
        n_gpu_layers=settings.n_gpu_layers,
        verbose=False,
    )
    return LlamaCppEngine(settings=settings, _llama=llama)
