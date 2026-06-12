$ErrorActionPreference = "Stop"

$ContainerName = if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "exercises-kafka" }

$exists = podman container exists $ContainerName 2>$null
if ($LASTEXITCODE -eq 0) {
  podman rm -f $ContainerName
  Write-Host "Removed container '$ContainerName'."
} else {
  Write-Host "No container named '$ContainerName'."
}
