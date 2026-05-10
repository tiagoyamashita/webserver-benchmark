# syntax=docker/dockerfile:1
FROM rust:1.85-bookworm AS build
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY src ./src
COPY templates ./templates
RUN cargo build --locked --release

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/release/exercises-web /app/exercises-web
COPY templates ./templates
RUN mkdir -p reports/target/nextest/dashboard
ENV EXERCISES_WEB_HOST=0.0.0.0
ENV EXERCISES_WEB_PORT=8082
ENV EXERCISES_RUST_ROOT=/app
EXPOSE 8082
CMD ["/app/exercises-web"]
