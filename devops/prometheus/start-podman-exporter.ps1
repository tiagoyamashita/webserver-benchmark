# Start podman-exporter inside the Podman Machine VM (Windows / macOS).
# Uses the same rootless podman as compose (do NOT use sudo — root podman has no exercises network).
param(
    [switch]$UseSudo
)

$ErrorActionPreference = "Stop"
$shPath = Join-Path $PSScriptRoot "start-podman-exporter.sh"

if (-not (Test-Path $shPath)) {
    throw "Missing script: $shPath"
}

$sh = Get-Content -Raw -Path $shPath
$sh = $sh -replace "`r`n", "`n"

Write-Host "Starting podman-exporter inside Podman Machine (exercises network)..." -ForegroundColor Cyan

if ($UseSudo) {
    Write-Host "Warning: sudo uses root podman and usually cannot see the exercises network on Podman Machine." -ForegroundColor Yellow
    $sh | podman machine ssh sudo sh -s
} else {
    $sh | podman machine ssh sh -s
}

try {
    curl.exe -s -X POST http://127.0.0.1:9090/-/reload | Out-Null
    Write-Host "Prometheus reloaded." -ForegroundColor Green
} catch {
    Write-Host "Prometheus reload skipped (is observability up?)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Check: curl.exe http://127.0.0.1:9882/metrics" -ForegroundColor Green
Write-Host "Prometheus: curl.exe -X POST http://127.0.0.1:9090/-/reload" -ForegroundColor Green
Write-Host "Grafana: Dashboards -> WebServer BenchMark -> WebServer BenchMark — Container resources (Podman)" -ForegroundColor Green
