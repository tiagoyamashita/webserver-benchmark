from __future__ import annotations

import uvicorn

from ai_model.config import load_settings


def main() -> None:
    settings = load_settings()
    uvicorn.run(
        "ai_model.app:app",
        host=settings.host,
        port=settings.port,
        reload=False,
    )


if __name__ == "__main__":
    main()
