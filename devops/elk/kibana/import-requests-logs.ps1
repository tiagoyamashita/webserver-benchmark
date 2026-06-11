# Provision Kibana saved objects for HTTP + Postgres log correlation.
# Run from repo root after docker-compose.observability.yml is up.
# Usage: .\devops\elk\kibana\import-requests-logs.ps1

$ErrorActionPreference = "Stop"
$Kibana = if ($env:KIBANA_URL) { $env:KIBANA_URL } else { "http://127.0.0.1:5601" }
$Root = Join-Path $PSScriptRoot "saved_objects"
$Bundle = Join-Path $Root "requests-logs"

function Import-KibanaObject {
    param(
        [string]$Type,
        [string]$Id,
        [string]$JsonPath
    )
    Write-Host "POST $Type/$Id ..."
    curl.exe -sS -X POST "$Kibana/api/saved_objects/$Type/${Id}?overwrite=true" `
        -H "kbn-xsrf: true" `
        -H "Content-Type: application/json" `
        --data-binary "@$JsonPath"
    Write-Host ""
}

Import-KibanaObject -Type "index-pattern" -Id "logstash-data-view" -JsonPath (Join-Path $Root "index-pattern.json")
Import-KibanaObject -Type "search" -Id "requests-logs-http-search" -JsonPath (Join-Path $Bundle "search-http-requests.json")
Import-KibanaObject -Type "search" -Id "requests-logs-sql-search" -JsonPath (Join-Path $Bundle "search-sql-crud.json")
Import-KibanaObject -Type "search" -Id "requests-logs-postgres-stream-search" -JsonPath (Join-Path $Bundle "search-postgres-stream.json")
Import-KibanaObject -Type "search" -Id "requests-logs-http-handlers" -JsonPath (Join-Path $Bundle "search-http-handlers.json")
Import-KibanaObject -Type "dashboard" -Id "exercises-requests-logs-kibana" -JsonPath (Join-Path $Bundle "dashboard.json")

Write-Host "Open: $Kibana/app/dashboards#/view/exercises-requests-logs-kibana"
