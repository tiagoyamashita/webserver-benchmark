# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm
WORKDIR /app

# Install third-party deps first — this layer is reused when only src/ or tests/ change.
COPY requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --upgrade pip \
    && pip install -r requirements.txt

COPY pyproject.toml README.md ./
COPY src ./src
COPY tests ./tests
RUN pip install --no-deps .

RUN mkdir -p reports logs

COPY docker-entrypoint-dev.sh /usr/local/bin/docker-entrypoint-dev.sh
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint-dev.sh \
  && chmod +x /usr/local/bin/docker-entrypoint-dev.sh

ENV FLASK_RUN_HOST=0.0.0.0
ENV FLASK_RUN_PORT=5000
ENV EXERCISES_PROJECT_ROOT=/app
EXPOSE 5000
CMD ["exercises-web"]
