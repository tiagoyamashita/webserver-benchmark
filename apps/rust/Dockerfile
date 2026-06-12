# syntax=docker/dockerfile:1
# icu_* needs rustc >= 1.86; home pinned to 0.5.11 (0.5.12 needs 1.88).
FROM rust:1.86-bookworm AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends cmake pkg-config libssl-dev libsasl2-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY templates ./templates
RUN cargo build --locked --release

# Runtime must match glibc from rust:1.86-bookworm (needs GLIBC_2.39+; bookworm-slim is too old).
FROM debian:trixie-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8082/health || exit 1
WORKDIR /app
COPY --from=build /build/target/release/exercises-web /app/exercises-web
COPY templates ./templates
RUN mkdir -p reports/target/nextest/dashboard logs
ENV EXERCISES_WEB_HOST=0.0.0.0
ENV EXERCISES_WEB_PORT=8082
ENV EXERCISES_RUST_ROOT=/app
EXPOSE 8082
CMD ["/app/exercises-web"]
