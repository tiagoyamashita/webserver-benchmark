from __future__ import annotations

from fastapi.testclient import TestClient

from ai_model.app import create_app
from ai_model.config import Settings
from ai_model.engine import StubEngine


def test_health_reports_degraded_without_model() -> None:
    settings = Settings(
        host="127.0.0.1",
        port=8095,
        gguf_path=None,
        n_ctx=512,
        n_gpu_layers=0,
        max_tokens_default=64,
        log_path=__import__("pathlib").Path("logs"),
    )
    client = TestClient(create_app(settings=settings, engine=StubEngine(settings)))
    response = client.get("/health")
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "degraded"
    assert payload["model"]["backend"] == "stub"


def test_generate_returns_503_when_model_missing() -> None:
    settings = Settings(
        host="127.0.0.1",
        port=8095,
        gguf_path=None,
        n_ctx=512,
        n_gpu_layers=0,
        max_tokens_default=64,
        log_path=__import__("pathlib").Path("logs"),
    )
    client = TestClient(create_app(settings=settings, engine=StubEngine(settings)))
    response = client.post("/api/generate", json={"prompt": "hello"})
    assert response.status_code == 503
