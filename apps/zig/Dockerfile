# syntax=docker/dockerfile:1
# Zig has no official Docker Hub image — download the static tarball in the build stage.
ARG ZIG_VERSION=0.13.0
FROM debian:bookworm-slim AS build
ARG ZIG_VERSION
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates xz-utils libpq-dev \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL "https://ziglang.org/download/${ZIG_VERSION}/zig-linux-x86_64-${ZIG_VERSION}.tar.xz" -o /tmp/zig.tar.xz \
    && mkdir -p /opt/zig \
    && tar -xJf /tmp/zig.tar.xz -C /opt/zig --strip-components=1 \
    && rm /tmp/zig.tar.xz
ENV PATH="/opt/zig:${PATH}"
ENV C_INCLUDE_PATH="/usr/include/postgresql"
WORKDIR /build
COPY build.zig build.zig.zon ./
COPY vendor ./vendor
COPY src ./src
RUN zig build -Doptimize=ReleaseSafe

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl libpq5 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/zig-out/bin/exercises-zig /app/exercises-zig
RUN mkdir -p logs
ENV EXERCISES_WEB_HOST=0.0.0.0
ENV EXERCISES_WEB_PORT=8083
ENV LOG_PATH=/app/logs
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8083/health || exit 1
EXPOSE 8083
CMD ["/app/exercises-zig"]
