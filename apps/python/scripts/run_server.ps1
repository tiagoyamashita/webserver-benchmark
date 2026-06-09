param(
    [string] $BindHost,
    [ValidateRange(1, 65535)]
    [int] $Port = 0
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $ProjectRoot

$VenvDir = Join-Path $ProjectRoot "exercises"
$Activate = Join-Path $VenvDir "Scripts\Activate.ps1"
if (-not (Test-Path $Activate)) {
    throw "venv not found. Run scripts\init_venv.ps1 first. Expected: $Activate"
}
. $Activate

if ($BindHost) {
    $env:FLASK_RUN_HOST = $BindHost
}
if ($Port -gt 0) {
    $env:FLASK_RUN_PORT = "$Port"
}

if (-not (Get-Command exercises-web -ErrorAction SilentlyContinue)) {
    pip install -r (Join-Path $ProjectRoot "requirements-dev.txt")
}

$displayHost = if ($env:FLASK_RUN_HOST) { $env:FLASK_RUN_HOST } else { "127.0.0.1" }
$displayPort = if ($env:FLASK_RUN_PORT) { $env:FLASK_RUN_PORT } else { "5000" }
Write-Host "Starting exercises web → http://${displayHost}:${displayPort}/" -ForegroundColor Cyan
& exercises-web
