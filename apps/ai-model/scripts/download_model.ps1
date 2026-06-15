param(
    [ValidateSet("", "qwen7b", "qwen3b", "smol360")]
    [string]$Preset = "",
    [string]$OutFile = "",
    [string]$Url = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$presets = @{
    qwen7b = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf"
    qwen3b = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf"
    smol360 = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf"
}

if ($Preset) {
    $Url = $presets[$Preset]
} elseif (-not $Url) {
    $Url = $presets["smol360"]
}

if (-not $OutFile) {
    $OutFile = [System.IO.Path]::GetFileName(($Url -split '\?')[0])
}

$modelsDir = Join-Path $root "models"
$dest = Join-Path $modelsDir $OutFile
New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null

Write-Host "Downloading GGUF to $dest" -ForegroundColor Cyan
Write-Host "URL: $Url"
curl.exe -L $Url -o $dest

$sizeBytes = (Get-Item $dest).Length
if ($sizeBytes -lt 1MB) {
    Remove-Item $dest -Force
    throw "Download failed (only $sizeBytes bytes). Check the URL or your network, then retry."
}

$sizeMb = [math]::Round($sizeBytes / 1MB, 1)
Write-Host "Done ($sizeMb MB). Set AI_MODEL_GGUF_PATH=$dest" -ForegroundColor Green
