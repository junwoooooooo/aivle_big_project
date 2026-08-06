[CmdletBinding()]
param(
    [int]$MinioPort = 9000,
    [int]$MinioConsolePort = 9001,
    [int]$AiPort = 18000,
    [int]$SpringPort = 8080,
    [string]$MinioExecutable = ""
)

$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "ai-artifact-smoke.ps1") `
    -MinioPort $MinioPort `
    -MinioConsolePort $MinioConsolePort `
    -AiPort $AiPort `
    -SpringPort $SpringPort `
    -MinioExecutable $MinioExecutable `
    -MarketingSmoke
exit $LASTEXITCODE
