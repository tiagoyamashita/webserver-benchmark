#Requires -Version 5.1
<#
.SYNOPSIS
  Re-applies Rust tooling for rust/ (rustup stable, cargo-nextest, cargo build).

.PARAMETER SkipNextest
  Do not run cargo install --locked cargo-nextest.

.PARAMETER SkipBuild
  Do not run cargo build.

.EXAMPLE
  .\scripts\resetup_rust_env.ps1

.EXAMPLE
  .\scripts\resetup_rust_env.ps1 -SkipNextest
#>
param(
    [switch]$SkipNextest,
    [switch]$SkipBuild
)

# ASCII-only messages here so the file parses under Windows PowerShell default encodings.
$ErrorActionPreference = "Stop"
$RustRoot = Split-Path -Parent $PSScriptRoot

Write-Host ""
Write-Host "=== rust / resetup_rust_env.ps1 ===" -ForegroundColor Cyan
Write-Host "Rust project: $RustRoot"
Write-Host ""

if (-not (Test-Path (Join-Path $RustRoot "Cargo.toml"))) {
    throw "No Cargo.toml in ${RustRoot}. Run this script from rust/scripts/ inside the repo."
}

function Get-RustHostTriple {
    if (-not (Get-Command rustc -ErrorAction SilentlyContinue)) {
        return $null
    }
    foreach ($line in (& rustc -vV 2>$null)) {
        if ($line -match '^host:\s*(.+)$') {
            return $Matches[1].Trim()
        }
    }
    return $null
}

function Test-LinkExeOnPath {
    return $null -ne (Get-Command link.exe -ErrorAction SilentlyContinue)
}

function Get-VisualStudioInstallPath {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vswhere)) {
        return $null
    }
    $p = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
    if ($LASTEXITCODE -eq 0 -and $p) {
        return $p.Trim()
    }
    $p = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Workload.VCTools -property installationPath 2>$null
    if ($LASTEXITCODE -eq 0 -and $p) {
        return $p.Trim()
    }
    return $null
}

$rustHost = Get-RustHostTriple
$needMsvcLinker = ($rustHost -match 'windows-msvc') -and -not (Test-LinkExeOnPath)

Push-Location $RustRoot
try {
    $ErrorActionPreference = "Continue"
    Write-Host "== rustup update stable ==" -ForegroundColor Green
    & rustup update stable

    if ($needMsvcLinker) {
        Write-Host ""
        $vsPath = Get-VisualStudioInstallPath
        $vsHint = ""
        if ($vsPath) {
            $vsHint = @"

Visual Studio / Build Tools with C++ may already be installed at:
  $vsPath
link.exe is not on this shell's PATH. Use one of these, then re-run this script from the rust folder:
  - Start Menu: 'Developer PowerShell for VS' or 'x64 Native Tools Command Prompt for VS'
  - Or run VsDevCmd.bat / vcvars64.bat for your edition so 'where.exe link' finds link.exe
"@
        }
        Write-Warning @"
MSVC linker (link.exe) not on PATH. Host: $rustHost
cargo cannot compile until the MSVC tools are installed and visible to this terminal.
Install: Visual Studio Build Tools with workload 'Desktop development with C++' (see rust/README.md).
Skipping: cargo install --locked cargo-nextest and cargo build.
$vsHint
"@
    }
    elseif (-not $SkipNextest) {
        Write-Host '== cargo-nextest: try latest, fallback 0.9.128 ==' -ForegroundColor Green
        $rustcVer = $null
        try {
            $rvLine = (& rustc -V 2>$null) | Select-Object -First 1
            if ($rvLine -match '^rustc (\d+)\.(\d+)\.(\d+)') {
                $rustcVer = [version]("$($matches[1]).$($matches[2]).$($matches[3])")
            }
        }
        catch { }

        $needsPinnedNextest = ($rustcVer -ne $null) -and ($rustcVer -lt [version]'1.91.0')
        if ($needsPinnedNextest) {
            Write-Host "rustc $rustcVer is below 1.91; installing cargo-nextest 0.9.128 directly."
            & cargo install --locked cargo-nextest --version 0.9.128
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "cargo install --locked cargo-nextest 0.9.128 failed (exit $LASTEXITCODE)."
            }
        }
        else {
            & cargo install --locked cargo-nextest
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Latest cargo-nextest failed; trying 0.9.128 (older rustc)."
                & cargo install --locked cargo-nextest --version 0.9.128
                if ($LASTEXITCODE -ne 0) {
                    Write-Warning "cargo-nextest 0.9.128 also failed (exit $LASTEXITCODE). If errors mentioned link.exe, install MSVC Build Tools - see rust/README.md."
                }
            }
        }
    }
    else {
        Write-Host "Skipping cargo-nextest (-SkipNextest)." -ForegroundColor DarkGray
    }

    $ErrorActionPreference = "Stop"
    if (-not $needMsvcLinker) {
        if (-not $SkipBuild) {
            Write-Host "== cargo build ==" -ForegroundColor Green
            & cargo build
            if ($LASTEXITCODE -ne 0) {
                Write-Warning "cargo build failed (exit $LASTEXITCODE). On Windows you often need MSVC Build Tools for link.exe - see rust/README.md."
            }
        }
        else {
            Write-Host "Skipping cargo build (-SkipBuild)." -ForegroundColor DarkGray
        }
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
Write-Host ""
