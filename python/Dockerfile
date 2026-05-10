# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm
WORKDIR /app
COPY pyproject.toml README.md requirements-dev.txt ./
COPY src ./src
COPY tests ./tests
RUN mkdir -p reports \
    && pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements-dev.txt
ENV FLASK_RUN_HOST=0.0.0.0
ENV FLASK_RUN_PORT=5000
ENV EXERCISES_PROJECT_ROOT=/app
EXPOSE 5000
CMD ["exercises-web"]
