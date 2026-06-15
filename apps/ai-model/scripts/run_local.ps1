param(
    [string]$ModelPath = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

if (-not (Test-Path ".venv")) {
    python -m venv .venv
}

$pip = Join-Path $root ".venv\Scripts\pip.exe"
& $pip install -q -r requirements.txt -r requirements-llm.txt
& $pip install -q -e .

if (-not $ModelPath) {
    $modelsDir = Join-Path $root "models"
    $ggufs = @(Get-ChildItem -Path $modelsDir -Filter "*.gguf" -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
    if ($ggufs.Count -ge 1) {
        $ModelPath = $ggufs[0].FullName
    }
}

if ($ModelPath) {
    $env:AI_MODEL_GGUF_PATH = (Resolve-Path $ModelPath).Path
    Write-Host "Using model: $($env:AI_MODEL_GGUF_PATH)" -ForegroundColor Green
} else {
    Write-Host "No GGUF found. Download one to models\ or pass -ModelPath." -ForegroundColor Yellow
}

$env:AI_MODEL_HOST = "127.0.0.1"
$env:AI_MODEL_PORT = "8095"
& (Join-Path $root ".venv\Scripts\ai-model-serve.exe")
