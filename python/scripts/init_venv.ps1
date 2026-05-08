$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $ProjectRoot

$VenvDir = Join-Path $ProjectRoot "exercises"
if (-not (Test-Path $VenvDir)) {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        & py -3 -m venv $VenvDir
    } else {
        & python -m venv $VenvDir
    }
}

$Activate = Join-Path $VenvDir "Scripts\Activate.ps1"
if (-not (Test-Path $Activate)) {
    throw "venv activate script missing: $Activate"
}
. $Activate

python -m pip install --upgrade pip
pip install -r (Join-Path $ProjectRoot "requirements-dev.txt")

Write-Host "venv ready at $VenvDir (activated in this session)."
