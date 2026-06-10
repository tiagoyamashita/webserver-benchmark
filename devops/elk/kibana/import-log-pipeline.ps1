# Provision Kibana saved objects for log pipeline monitoring.
# Run from repo root after docker-compose.observability.yml is up.
# Usage: .\devops\elk\kibana\import-log-pipeline.ps1

$ErrorActionPreference = "Stop"
$Kibana = if ($env:KIBANA_URL) { $env:KIBANA_URL } else { "http://127.0.0.1:5601" }
$Dir = Join-Path $PSScriptRoot "saved_objects"

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

Import-KibanaObject -Type "index-pattern" -Id "logstash-data-view" -JsonPath (Join-Path $Dir "index-pattern.json")
Import-KibanaObject -Type "search" -Id "log-pipeline-all-search" -JsonPath (Join-Path $Dir "search-all.json")
Import-KibanaObject -Type "search" -Id "log-pipeline-errors-search" -JsonPath (Join-Path $Dir "search-errors.json")
Import-KibanaObject -Type "dashboard" -Id "exercises-log-pipeline-kibana" -JsonPath (Join-Path $Dir "dashboard.json")

Write-Host "Open: $Kibana/app/dashboards#/view/exercises-log-pipeline-kibana"
