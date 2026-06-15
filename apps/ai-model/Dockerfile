# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm
WORKDIR /app

# Base image stays small; mount a GGUF under /app/models and set AI_MODEL_GGUF_PATH.
# For inference inside the container, rebuild with:
#   podman build --build-arg INSTALL_LLM=1 -t exercises-ai-model ./apps/ai-model
ARG INSTALL_LLM=0

COPY requirements.txt requirements-llm.txt pyproject.toml README.md ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip \
    && pip install -r requirements.txt \
    && if [ "$INSTALL_LLM" = "1" ]; then pip install -r requirements-llm.txt; fi

COPY src ./src
COPY tests ./tests
COPY docker-entrypoint-dev.sh ./
RUN chmod +x docker-entrypoint-dev.sh
RUN pip install --no-deps .

RUN mkdir -p models logs

ENV AI_MODEL_HOST=0.0.0.0
ENV AI_MODEL_PORT=8095
ENV AI_MODEL_GGUF_PATH=/app/models/model.gguf
ENV LOG_PATH=/app/logs
EXPOSE 8095
CMD ["ai-model-serve"]
