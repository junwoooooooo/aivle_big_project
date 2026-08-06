$ErrorActionPreference = "Stop"

chcp 65001 > $null

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$root = Split-Path -Parent $PSScriptRoot
$runtimePath = Join-Path $root ".demo-runtime"
$processFile = Join-Path $runtimePath "processes.env"

if (-not (Test-Path -LiteralPath $processFile)) {
    Write-Host "기록된 데모 프로세스가 없습니다."
    exit 0
}

$processes = @{}

Get-Content -LiteralPath $processFile -Encoding UTF8 | ForEach-Object {
    $parts = $_ -split "=", 2

    if ($parts.Count -eq 2) {
        $processes[$parts[0].Trim()] = $parts[1].Trim()
    }
}

foreach ($name in @("BACKEND_PID", "FRONTEND_PID")) {
    if (-not $processes.ContainsKey($name)) {
        continue
    }

    $processId = [int]$processes[$name]
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue

    if ($null -ne $process) {
        Stop-Process -Id $processId -Force
        Write-Host "$name 프로세스를 종료했습니다: $processId"
    }
}

Remove-Item -LiteralPath $processFile -Force