$ErrorActionPreference = "Stop"

$ContainerName = if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "exercises-kafka" }
$Image = if ($env:KAFKA_IMAGE) { $env:KAFKA_IMAGE } else { "docker.io/apache/kafka:3.9.1" }
$HostPort = if ($env:HOST_PORT) { $env:HOST_PORT } else { "9092" }

$exists = podman container exists $ContainerName 2>$null
if ($LASTEXITCODE -eq 0) {
  Write-Host "Container '$ContainerName' already exists. Remove it first: .\scripts\stop.ps1"
  exit 1
}

podman volume create exercises-kafka-data 2>$null | Out-Null

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$KafkaRoot = Split-Path -Parent $ScriptDir
$LogConfig = Join-Path $KafkaRoot "config\log4j2.yaml"
$LogDir = Join-Path $KafkaRoot "logs"

podman run -d `
  --name $ContainerName `
  --hostname kafka `
  -p "${HostPort}:9092" `
  -v exercises-kafka-data:/var/lib/kafka/data `
  -v "${LogConfig}:/opt/kafka/config/log4j2.yaml:ro" `
  -v "${LogDir}:/var/log/kafka" `
  -e KAFKA_NODE_ID=1 `
  -e KAFKA_PROCESS_ROLES=broker,controller `
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 `
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 `
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER `
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT `
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093 `
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 `
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 `
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 `
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 `
  -e KAFKA_LOG_DIRS=/var/lib/kafka/data `
  $Image

Write-Host "Kafka listening on port ${HostPort} (bootstrap from other containers on this host: kafka:9092 if attached to the same network)."
Write-Host "Stop with: .\scripts\stop.ps1"
