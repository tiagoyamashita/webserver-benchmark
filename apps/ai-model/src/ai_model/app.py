from __future__ import annotations

import logging
import time
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import PlainTextResponse
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest
from pydantic import BaseModel, Field

from ai_model.config import Settings, load_settings
from ai_model.engine import InferenceEngine, StubEngine, build_engine

_HTTP_REQUESTS = Counter(
    "exercises_http_requests_total",
    "HTTP requests handled by the ai-model service",
    ["method", "endpoint"],
)
_LOG = logging.getLogger(__name__)


class GenerateRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=32_000)
    max_tokens: int | None = Field(default=None, ge=1, le=2048)


class GenerateResponse(BaseModel):
    text: str
    backend: str
    elapsedMs: int


def create_app(settings: Settings | None = None, engine: InferenceEngine | None = None) -> FastAPI:
    cfg = settings or load_settings()
    app = FastAPI(title="Exercises AI Model", version="0.1.0")
    app.state.settings = cfg
    app.state.engine = engine if engine is not None else _safe_build_engine(cfg)

    @app.middleware("http")
    async def count_requests(request: Request, call_next):
        response = await call_next(request)
        route = request.scope.get("route")
        endpoint = getattr(route, "path", request.url.path) if route else request.url.path
        _HTTP_REQUESTS.labels(request.method, endpoint).inc()
        return response

    @app.get("/health")
    def health() -> dict[str, object]:
        info = app.state.engine.info()
        return {
            "status": "ok" if info.get("ready") else "degraded",
            "service": "ai-model",
            "model": info,
        }

    @app.get("/api/model/info")
    def model_info() -> dict[str, object]:
        return app.state.engine.info()

    @app.post("/api/generate", response_model=GenerateResponse)
    def generate(body: GenerateRequest) -> GenerateResponse:
        max_tokens = body.max_tokens or cfg.max_tokens_default
        started = time.perf_counter()
        try:
            text = app.state.engine.generate(body.prompt, max_tokens)
        except RuntimeError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        backend = str(app.state.engine.info().get("backend", "unknown"))
        return GenerateResponse(text=text, backend=backend, elapsedMs=elapsed_ms)

    @app.get("/metrics")
    def metrics() -> PlainTextResponse:
        return PlainTextResponse(generate_latest(), media_type=CONTENT_TYPE_LATEST)

    return app


def _safe_build_engine(cfg: Settings) -> InferenceEngine:
    try:
        return build_engine(cfg)
    except Exception as exc:
        _LOG.warning("Model engine unavailable: %s", exc)
        return StubEngine(cfg, error=str(exc))


app = create_app()
