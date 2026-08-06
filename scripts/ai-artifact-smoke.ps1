[CmdletBinding()]
param(
    [int]$MinioPort = 9000,
    [int]$MinioConsolePort = 9001,
    [int]$AiPort = 18000,
    [int]$SpringPort = 8080,
    [string]$MinioExecutable = "",
    [switch]$MarketingSmoke
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "compose.infrastructure.yaml"
$runtimeRoot = Join-Path ([IO.Path]::GetTempPath()) (
    "aivle-minio-smoke-" + [guid]::NewGuid().ToString("N")
)
$minioProcess = $null
$usingCompose = $false
$accessKey = "aivle-local"
$secretKey = "replace-with-local-secret"

function Wait-Ready {
    param([string]$Url)
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing `
                -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for MinIO."
}

try {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not [string]::IsNullOrWhiteSpace($MinioExecutable)) {
        if (-not (Test-Path -LiteralPath $MinioExecutable)) {
            throw "MinIO executable does not exist."
        }
        New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
        $env:MINIO_ROOT_USER = $accessKey
        $env:MINIO_ROOT_PASSWORD = $secretKey
        $minioProcess = Start-Process `
            -FilePath $MinioExecutable `
            -ArgumentList @(
                "server", $runtimeRoot,
                "--address", "127.0.0.1:$MinioPort",
                "--console-address", "127.0.0.1:$MinioConsolePort"
            ) `
            -WindowStyle Hidden `
            -PassThru
    } elseif ($null -ne $docker) {
        $env:MINIO_ROOT_USER = $accessKey
        $env:MINIO_ROOT_PASSWORD = $secretKey
        $env:MINIO_BUCKET = "aivle-ai-artifacts"
        & docker compose -f $composeFile up -d --wait
        if ($LASTEXITCODE -ne 0) {
            throw "MinIO Compose startup failed."
        }
        $usingCompose = $true
    } else {
        throw (
            "Docker is unavailable. Pass -MinioExecutable with an " +
            "official MinIO server binary."
        )
    }

    Wait-Ready "http://127.0.0.1:$MinioPort/minio/health/ready"
    & (Join-Path $PSScriptRoot "ai-local-smoke.ps1") `
        -AiPort $AiPort `
        -SpringPort $SpringPort `
        -ArtifactSmoke `
        -MarketingSmoke:$MarketingSmoke `
        -ObjectStorageEndpoint "http://127.0.0.1:$MinioPort" `
        -ObjectStorageAccessKey $accessKey `
        -ObjectStorageSecretKey $secretKey
    if ($LASTEXITCODE -ne 0) {
        throw "Artifact smoke failed."
    }
} finally {
    if ($usingCompose) {
        & docker compose -f $composeFile down -v *> $null
    }
    if ($null -ne $minioProcess -and -not $minioProcess.HasExited) {
        & taskkill.exe /PID $minioProcess.Id /T /F *> $null
    }
    if (Test-Path -LiteralPath $runtimeRoot) {
        Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
    }
}
