# Start exercises-gpu-exporter inside the Podman Machine VM (NVIDIA GPU + nvidia-smi required).
$ErrorActionPreference = "Stop"
$shPath = Join-Path $PSScriptRoot "start-gpu-exporter.sh"

if (-not (Test-Path $shPath)) {
    throw "Missing script: $shPath"
}

$sh = Get-Content -Raw -Path $shPath
$sh = $sh -replace "`r`n", "`n"

Write-Host "Starting exercises-gpu-exporter (NVIDIA required on Podman Machine)..." -ForegroundColor Cyan
$sh | podman machine ssh sh -s

try {
    curl.exe -s -X POST http://127.0.0.1:9090/-/reload | Out-Null
    Write-Host "Prometheus reloaded." -ForegroundColor Green
} catch {
    Write-Host "Prometheus reload skipped." -ForegroundColor Yellow
}

Write-Host "Check: curl.exe http://127.0.0.1:9066/metrics" -ForegroundColor Green
