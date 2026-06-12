#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-exercises-kafka}"
IMAGE="${KAFKA_IMAGE:-docker.io/apache/kafka:3.9.1}"
HOST_PORT="${HOST_PORT:-9092}"

if podman container exists "$CONTAINER_NAME" 2>/dev/null; then
  echo "Container '$CONTAINER_NAME' already exists. Remove it first: ./scripts/stop.sh"
  exit 1
fi

podman volume create exercises-kafka-data >/dev/null 2>&1 || true

podman run -d \
  --name "$CONTAINER_NAME" \
  --hostname kafka \
  -p "${HOST_PORT}:9092" \
  -v exercises-kafka-data:/var/lib/kafka/data \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -e KAFKA_LOG_DIRS=/var/lib/kafka/data \
  "$IMAGE"

echo "Kafka listening on port ${HOST_PORT} (bootstrap from other containers on this host: kafka:9092 if attached to the same network)."
echo "Stop with: ./scripts/stop.sh"
