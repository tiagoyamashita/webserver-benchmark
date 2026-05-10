$ErrorActionPreference = "Stop"

$ContainerName = if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "exercises-postgre" }
$Image = if ($env:POSTGRES_IMAGE) { $env:POSTGRES_IMAGE } else { "docker.io/library/postgres:16" }
$PgUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "postgres" }
$PgPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres" }
$PgDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "demo" }
$HostPort = if ($env:HOST_PORT) { $env:HOST_PORT } else { "5432" }

$exists = podman container exists $ContainerName 2>$null
if ($LASTEXITCODE -eq 0) {
  Write-Host "Container '$ContainerName' already exists. Remove it first: .\scripts\stop.ps1"
  exit 1
}

podman volume create exercises-postgre-data 2>$null | Out-Null

podman run -d `
  --name $ContainerName `
  -e "POSTGRES_USER=$PgUser" `
  -e "POSTGRES_PASSWORD=$PgPassword" `
  -e "POSTGRES_DB=$PgDb" `
  -p "${HostPort}:5432" `
  -v exercises-postgre-data:/var/lib/postgresql/data `
  $Image

Write-Host "PostgreSQL listening on localhost:${HostPort} (database: $PgDb, user: $PgUser)."
Write-Host "Stop with: .\scripts\stop.ps1"
