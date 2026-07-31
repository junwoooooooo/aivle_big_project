$ErrorActionPreference = "Stop"

chcp 65001 > $null

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env.demo"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw @"
.env.demo 파일이 없습니다.

다음 명령으로 예제 파일을 복사하세요.

Copy-Item ".env.demo.example" ".env.demo"
"@
}

function Import-DotEnv {
    param([string]$Path)

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()

        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split "=", 2

        if ($parts.Count -ne 2) {
            throw "잘못된 환경변수 항목입니다: $line"
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable(
            $name,
            $value,
            [EnvironmentVariableTarget]::Process
        )
    }
}

Import-DotEnv -Path $envFile

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw "JWT_SECRET이 설정되지 않았습니다."
}

if ($env:JWT_SECRET.Length -lt 32) {
    throw "JWT_SECRET은 최소 32자 이상이어야 합니다."
}

if ([string]::IsNullOrWhiteSpace($env:VITE_API_BASE_URL)) {
    throw "VITE_API_BASE_URL이 설정되지 않았습니다."
}

$backendPath = Join-Path $root "backend"
$frontendPath = Join-Path $root "frontEnd"
$runtimePath = Join-Path $root ".demo-runtime"

New-Item -ItemType Directory -Force -Path $runtimePath | Out-Null

$backendOut = Join-Path $runtimePath "backend.out.log"
$backendErr = Join-Path $runtimePath "backend.err.log"
$frontendOut = Join-Path $runtimePath "frontend.out.log"
$frontendErr = Join-Path $runtimePath "frontend.err.log"

$aiServer = $null

# AI 서버(ai/)는 저장소 안에 있고 경로가 ASCII다.
$aiDir = Join-Path $root "ai"

if (-not (Test-Path -LiteralPath (Join-Path $aiDir "main.py"))) {
    throw "AI 서버를 찾지 못했습니다: $aiDir"
}

# 법령 조사는 LLM을 부른다. 없으면 기동 후에야 실패하므로 여기서 먼저 막는다.
if ($env:LEGAL_PROVIDER -eq "pipeline") {
    $hasApiKey = -not [string]::IsNullOrWhiteSpace($env:ANTHROPIC_API_KEY)
    $hasClaudeCli = $null -ne (Get-Command claude -ErrorAction SilentlyContinue)

    if (-not $hasApiKey -and -not $hasClaudeCli) {
        throw "LEGAL_PROVIDER=pipeline 은 ANTHROPIC_API_KEY 또는 로그인된 claude CLI가 필요합니다."
    }

    if (-not $hasApiKey) {
        Write-Host "법령 조사 LLM: claude CLI 세션 사용 (API 키 없음)"
    }
}

$aiPort = if ([string]::IsNullOrWhiteSpace($env:AI_SERVER_PORT)) { "8000" } else { $env:AI_SERVER_PORT }
$aiOut = Join-Path $runtimePath "ai.out.log"
$aiErr = Join-Path $runtimePath "ai.err.log"

$aiServer = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c", "python -m uvicorn main:app --host 127.0.0.1 --port $aiPort --workers 1" `
    -WorkingDirectory $aiDir `
    -RedirectStandardOutput $aiOut `
    -RedirectStandardError $aiErr `
    -PassThru

$ready = $false

for ($i = 0; $i -lt 40 -and -not $ready; $i++) {
    try {
        Invoke-RestMethod "http://127.0.0.1:$aiPort/health" -TimeoutSec 2 | Out-Null
        $ready = $true
    } catch {
        Start-Sleep -Milliseconds 500
    }
}

if (-not $ready) {
    Write-Host "경고: AI 서버가 아직 응답하지 않습니다. 로그를 확인하세요: $aiErr"
} else {
    Write-Host "AI 서버 준비 완료: http://127.0.0.1:$aiPort"
}

$backend = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c", ".\gradlew.bat bootRun" `
    -WorkingDirectory $backendPath `
    -RedirectStandardOutput $backendOut `
    -RedirectStandardError $backendErr `
    -PassThru

$frontend = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c", "npm.cmd run dev -- --host 127.0.0.1" `
    -WorkingDirectory $frontendPath `
    -RedirectStandardOutput $frontendOut `
    -RedirectStandardError $frontendErr `
    -PassThru

$processLines = @(
    "BACKEND_PID=$($backend.Id)",
    "FRONTEND_PID=$($frontend.Id)"
)

if ($null -ne $aiServer) {
    $processLines += "AI_PID=$($aiServer.Id)"
}

$processLines -join "`n" | Set-Content `
    -LiteralPath (Join-Path $runtimePath "processes.env") `
    -Encoding UTF8

Write-Host ""
Write-Host "데모 서버를 시작했습니다."
Write-Host "Frontend : http://localhost:5173"
Write-Host "Backend  : http://localhost:8080"

if ($null -ne $aiServer) {
    Write-Host "AI       : http://127.0.0.1:$aiPort  (법령 조사 파이프라인 포함)"
}

Write-Host ""
Write-Host "Backend log : $backendOut"
Write-Host "Frontend log: $frontendOut"
Write-Host ""
Write-Host "종료: .\scripts\demo-stop.ps1"