# Provision Kibana data view, saved searches, dashboards, and default landing route.
# Usage: .\devops\elk\kibana\provision-kibana.ps1

$ErrorActionPreference = "Stop"
$Kibana = if ($env:KIBANA_URL) { $env:KIBANA_URL } else { "http://127.0.0.1:5601" }
$Root = Join-Path $PSScriptRoot "saved_objects"
$DefaultRoute = if ($env:KIBANA_DEFAULT_ROUTE) { $env:KIBANA_DEFAULT_ROUTE } else { "/app/dashboards#/view/exercises-requests-logs-kibana" }
$DefaultDataViewId = if ($env:KIBANA_DEFAULT_DATA_VIEW_ID) { $env:KIBANA_DEFAULT_DATA_VIEW_ID } else { "logstash-data-view" }
$WaitSeconds = if ($env:KIBANA_PROVISION_WAIT_SECONDS) { [int]$env:KIBANA_PROVISION_WAIT_SECONDS } else { 180 }
$Ndjson = Join-Path $PSScriptRoot "exercises-kibana.ndjson"

function Wait-ForKibana {
    Write-Host "Waiting for Kibana at $Kibana (up to ${WaitSeconds}s) ..."
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $status = Invoke-RestMethod -Uri "$Kibana/api/status" -Method Get -TimeoutSec 5
            if ($status.status.overall.level -eq "available") {
                Write-Host "Kibana is available."
                return
            }
        } catch {
            # retry
        }
        Start-Sleep -Seconds 2
    }
    throw "Kibana did not become available within ${WaitSeconds}s."
}

Wait-ForKibana

python (Join-Path $PSScriptRoot "build-dashboards.py")
if ($LASTEXITCODE -ne 0) { throw "build-dashboards.py failed with exit code $LASTEXITCODE" }

python (Join-Path $PSScriptRoot "build-ndjson.py")
if ($LASTEXITCODE -ne 0) { throw "build-ndjson.py failed with exit code $LASTEXITCODE" }

# Remove auto-created duplicate data view (same logstash-* pattern) so only logstash-data-view remains.
Write-Host "DELETE duplicate data view (if present) ..."
curl.exe -sS -X DELETE "$Kibana/api/data_views/data_view/57d8bc58-117d-4a14-af02-4a8e4369a633" -H "kbn-xsrf: true" 2>$null
Write-Host ""

Write-Host "POST saved_objects/_import (compatibilityMode) ..."
curl.exe -sS -X POST "$Kibana/api/saved_objects/_import?overwrite=true&compatibilityMode=true" -H "kbn-xsrf: true" -F "file=@$Ndjson;type=application/ndjson"
Write-Host ""

Write-Host "POST data_views/default ($DefaultDataViewId) ..."
curl.exe -sS -X POST "$Kibana/api/data_views/default" -H "kbn-xsrf: true" -H "Content-Type: application/json" --data-binary "@$(Join-Path $Root 'default-data-view.json')"
Write-Host ""

$version = "8.15.5"
try {
    $status = Invoke-RestMethod -Uri "$Kibana/api/status" -Method Get -TimeoutSec 5
    if ($status.version.number) {
        $version = $status.version.number
    }
} catch {
    # keep default image version
}

Write-Host "PUT config/$version defaultRoute=$DefaultRoute ..."
curl.exe -sS -X PUT "$Kibana/api/saved_objects/config/${version}?overwrite=true" -H "kbn-xsrf: true" -H "Content-Type: application/json" --data-binary "@$(Join-Path $Root 'config.json')"
Write-Host ""

Write-Host "Kibana preset ready: $Kibana$DefaultRoute"
