$ErrorActionPreference = "Stop"

chcp 65001 > $null

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env.demo"

Write-Warning (
    "Legacy stable-core demo: Backend + Frontend 직접 실행, /api/v1 중심, " +
    "local/H2 설정 사용. 신규 6단계 제품 파이프라인 검증이 아닙니다."
)

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

$backend = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c", "gradlew.bat bootRun" `
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

@"
BACKEND_PID=$($backend.Id)
FRONTEND_PID=$($frontend.Id)
"@ | Set-Content `
    -LiteralPath (Join-Path $runtimePath "processes.env") `
    -Encoding UTF8

Write-Host ""
Write-Host "Legacy stable-core 데모 서버를 시작했습니다."
Write-Host "Frontend : http://localhost:5173"
Write-Host "Backend  : http://localhost:8080"
Write-Host ""
Write-Host "Backend log : $backendOut"
Write-Host "Frontend log: $frontendOut"
Write-Host ""
Write-Host "종료: .\scripts\demo-stop.ps1"
