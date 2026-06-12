$ErrorActionPreference = "Stop"

$ContainerName = if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "exercises-redis" }
$Image = if ($env:REDIS_IMAGE) { $env:REDIS_IMAGE } else { "docker.io/library/redis:7-alpine" }
$HostPort = if ($env:HOST_PORT) { $env:HOST_PORT } else { "6379" }

$exists = podman container exists $ContainerName 2>$null
if ($LASTEXITCODE -eq 0) {
  Write-Host "Container '$ContainerName' already exists. Remove it first: .\scripts\stop.ps1"
  exit 1
}

podman volume create exercises-redis-data 2>$null | Out-Null

podman run -d `
  --name $ContainerName `
  -p "${HostPort}:6379" `
  -v exercises-redis-data:/data `
  $Image `
  redis-server --appendonly yes

Write-Host "Redis listening on localhost:${HostPort}."
Write-Host "Stop with: .\scripts\stop.ps1"
