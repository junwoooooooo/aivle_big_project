[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [int]$FrontendPort = 3000,
    [int]$BackendPort = 8080,
    [int]$AiServerPort = 8000,
    [int]$PostgresPort = 5432,
    [int]$MinioPort = 9000,
    [int]$MinioConsolePort = 9001,
    [switch]$PreflightOnly,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$composeFiles = @(
    "-f", (Join-Path $root "compose.yaml"),
    "-f", (Join-Path $root "compose.e2e.yaml")
)
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    $resolvedEnv = (Resolve-Path -LiteralPath $EnvFile).Path
    $composeFiles = @("--env-file", $resolvedEnv) + $composeFiles
}

$frontendBase = "http://127.0.0.1:$FrontendPort"
$backendBase = "http://127.0.0.1:$BackendPort"
$aiBase = "http://127.0.0.1:$AiServerPort"
$sampleImage = Join-Path ([IO.Path]::GetTempPath()) (
    "aivle-docker-e2e-" + [guid]::NewGuid().ToString("N") + ".png"
)
$locationPushed = $false
$composeAttempted = $false
$scriptFailed = $false
$originalFailure = $null

$env:FRONTEND_PORT = "$FrontendPort"
$env:BACKEND_PORT = "$BackendPort"
$env:AI_SERVER_PORT = "$AiServerPort"
$env:POSTGRES_PORT = "$PostgresPort"
$env:MINIO_API_PORT = "$MinioPort"
$env:MINIO_CONSOLE_PORT = "$MinioConsolePort"

function Get-PortOwners {
    param([int]$Port)
    if ($null -ne (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen `
            -ErrorAction SilentlyContinue |
            Sort-Object OwningProcess -Unique | ForEach-Object {
                $process = Get-Process -Id $_.OwningProcess `
                    -ErrorAction SilentlyContinue
                [pscustomobject]@{
                    Port = $Port
                    Pid = $_.OwningProcess
                    Process = if ($null -eq $process) {
                        "unknown"
                    } else {
                        $process.ProcessName
                    }
                }
            })
    }
    return @()
}

function Assert-PublicPortsAvailable {
    $ports = @(
        [pscustomobject]@{ Name = "frontend"; Port = $FrontendPort; Option = "-FrontendPort" }
        [pscustomobject]@{ Name = "backend"; Port = $BackendPort; Option = "-BackendPort" }
        [pscustomobject]@{ Name = "ai-server"; Port = $AiServerPort; Option = "-AiServerPort" }
        [pscustomobject]@{ Name = "postgres"; Port = $PostgresPort; Option = "-PostgresPort" }
        [pscustomobject]@{ Name = "minio"; Port = $MinioPort; Option = "-MinioPort" }
        [pscustomobject]@{ Name = "minio-console"; Port = $MinioConsolePort; Option = "-MinioConsolePort" }
    )
    $duplicates = $ports | Group-Object Port | Where-Object Count -gt 1
    if ($duplicates) {
        throw "Published ports must be unique."
    }
    $conflicts = @()
    foreach ($entry in $ports) {
        foreach ($owner in @(Get-PortOwners $entry.Port)) {
            $conflicts += [pscustomobject]@{
                Service = $entry.Name
                Port = $entry.Port
                Pid = $owner.Pid
                Process = $owner.Process
                Option = $entry.Option
            }
        }
    }
    if ($conflicts.Count -eq 0) {
        return
    }
    [Console]::Error.WriteLine("Docker E2E port preflight failed:")
    foreach ($conflict in $conflicts) {
        [Console]::Error.WriteLine(
            ("  {0}: port={1}, pid={2}, process={3}" -f @(
                $conflict.Service,
                $conflict.Port,
                $conflict.Pid,
                $conflict.Process
            ))
        )
    }
    $example = $conflicts[0]
    $suggested = 18000
    while (@(Get-PortOwners $suggested).Count -gt 0) {
        $suggested++
    }
    [Console]::Error.WriteLine(
        "Override example: powershell -ExecutionPolicy Bypass " +
        "-File scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example " +
        "$($example.Option) $suggested"
    )
    throw "One or more Docker E2E published ports are already in use."
}

function Protect-DiagnosticText {
    param([AllowEmptyString()][string]$Text)
    $redacted = [string]$Text
    foreach ($name in @(
        "POSTGRES_PASSWORD",
        "JWT_SECRET",
        "MINIO_ROOT_PASSWORD",
        "OBJECT_STORAGE_SECRET_KEY",
        "AI_INTERNAL_SERVICE_TOKEN",
        "AI_SERVER_INTERNAL_API_KEY"
    )) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            $redacted = $redacted.Replace($value, "[REDACTED]")
        }
    }
    $redacted = $redacted -replace (
        '(?i)(X-Amz-(Credential|Signature|Security-Token)=)[^&\s]+'
    ), '$1[REDACTED]'
    return $redacted
}

function Write-ComposeDiagnostics {
    param([int]$ExitCode)
    [Console]::Error.WriteLine("Docker E2E diagnostic exitCode=$ExitCode")
    try {
        $psOutput = & docker compose @composeFiles ps --all 2>&1 |
            Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $psOutput))
    } catch {
        [Console]::Error.WriteLine(
            "Could not collect docker compose ps: " + $_.Exception.Message
        )
    }
    try {
        $containerIds = @(& docker compose @composeFiles ps --all -q 2>$null)
        foreach ($containerId in $containerIds) {
            if ([string]::IsNullOrWhiteSpace($containerId)) {
                continue
            }
            $state = & docker inspect --format (
                'name={{.Name}} state={{json .State}}'
            ) $containerId 2>&1 | Out-String
            if ($state -notmatch '"Status":"running".*"Health":\{"Status":"healthy"') {
                [Console]::Error.WriteLine(
                    "Unhealthy/exited container inspect:"
                )
                [Console]::Error.WriteLine(
                    (Protect-DiagnosticText $state)
                )
            }
        }
    } catch {
        [Console]::Error.WriteLine(
            "Could not inspect unhealthy containers: " +
            $_.Exception.Message
        )
    }
    try {
        $logs = & docker compose @composeFiles logs `
            --no-color --tail 200 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $logs))
    } catch {
        [Console]::Error.WriteLine(
            "Could not collect docker compose logs: " +
            $_.Exception.Message
        )
    }
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker compose @composeFiles @Args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed: $($Args -join ' ')"
    }
}

function Wait-Http {
    param([string]$Uri, [int]$TimeoutSeconds = 120)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri `
                -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Uri"
}

function Wait-Job {
    param(
        [long]$JobId,
        [hashtable]$Headers,
        [int]$TimeoutSeconds = 60
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri (
            "$frontendBase/api/v1/jobs/$JobId"
        ) -Headers $Headers -TimeoutSec 10
        if ($response.data.status -in @("SUCCEEDED", "FAILED")) {
            return $response.data
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for job $JobId"
}

function New-JsonPost {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers `
        -ContentType "application/json" -Body ($Body | ConvertTo-Json) `
        -TimeoutSec 30
}

try {
    Assert-PublicPortsAvailable
    if ($PreflightOnly) {
        Write-Output "Docker E2E port preflight passed."
        exit 0
    }
    Push-Location $root
    $locationPushed = $true
    Invoke-Compose config --quiet
    $composeAttempted = $true
    Invoke-Compose up --build --detach

    Wait-Http "$frontendBase/healthz" 180
    Wait-Http "$backendBase/actuator/health" 180
    Wait-Http "$aiBase/health/live" 60
    Wait-Http "$aiBase/health/ready" 60

    $initExit = (& docker compose @composeFiles ps --all `
        --format "{{.ExitCode}}" minio-init).Trim()
    if ($LASTEXITCODE -ne 0 -or $initExit -ne "0") {
        throw "MinIO bucket initialization did not complete successfully."
    }

    $suffix = [guid]::NewGuid().ToString("N")
    $requestId = [guid]::NewGuid().ToString()
    $signup = New-JsonPost "$frontendBase/api/v1/auth/signup" @{} @{
        username = "e2e" + $suffix.Substring(0, 12)
        password = "Q7!" + $suffix.Substring(0, 20)
        displayName = "Docker E2E User"
        email = "docker-e2e-$suffix@example.com"
        organizationName = $null
        departmentName = $null
        jobTitle = $null
    }
    $headers = @{
        "X-User-Id" = "$($signup.data.user.id)"
        "X-User-Role" = "USER"
        "X-Request-Id" = $requestId
    }
    $project = New-JsonPost "$frontendBase/api/v1/projects" $headers @{
        title = "Docker E2E " + $suffix.Substring(0, 8)
        description = "Disposable full-stack integration project"
        industryCategory = "test"
    }
    $projectId = $project.data.id

    $headers["Idempotency-Key"] = "system-$suffix"
    $system = New-JsonPost (
        "$frontendBase/api/v1/projects/$projectId/ai-tasks/smoke"
    ) $headers @{}
    $systemJob = Wait-Job $system.data.jobId $headers
    if ($systemJob.status -ne "SUCCEEDED") {
        throw "SYSTEM_SMOKE_TEST failed: $($systemJob.errorCode)"
    }

    $headers["Idempotency-Key"] = "artifact-$suffix"
    $artifact = New-JsonPost (
        "$frontendBase/api/v1/projects/$projectId/ai-tasks/artifact-smoke"
    ) $headers @{}
    $artifactJob = Wait-Job $artifact.data.jobId $headers
    if ($artifactJob.status -ne "SUCCEEDED") {
        throw "SYSTEM_ARTIFACT_SMOKE_TEST failed: $($artifactJob.errorCode)"
    }
    $artifactDownload = Invoke-WebRequest -UseBasicParsing -Uri (
        "$frontendBase/api/v1/projects/$projectId/ai-tasks/" +
        "$($artifact.data.jobId)/artifacts/result"
    ) -Headers $headers -TimeoutSec 15
    $artifactText = if ($artifactDownload.Content -is [byte[]]) {
        [Text.Encoding]::UTF8.GetString($artifactDownload.Content)
    } else {
        [string]$artifactDownload.Content
    }
    $artifactJson = $artifactText | ConvertFrom-Json
    if ($artifactJson.status -ne "processed") {
        throw "Artifact download content was not the expected result."
    }

    $content = New-JsonPost (
        "$frontendBase/api/v1/projects/$projectId/marketing-contents"
    ) $headers @{
        title = "Docker Campaign"
        purpose = "PRODUCT_INTRODUCTION"
        channel = "SOCIAL"
        format = "SQUARE_1080"
        width = $null
        height = $null
        personaId = $null
        targetOffer = "Container verified service"
        emphasisMessage = "Reliable"
        requiredText = ""
        avoidedText = ""
        brandName = "Aivle"
        brandColor = "#0f8878"
        callToAction = "Learn more"
        tone = "PROFESSIONAL"
        template = "HERO_CENTER"
        panelInterviewId = $null
        marketResponseId = $null
    }
    $contentId = $content.data.content.id
    $sourceVersionId = $content.data.current.id
    [IO.File]::WriteAllBytes(
        $sampleImage,
        [Convert]::FromBase64String(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
    )
    $headers["Idempotency-Key"] = "marketing-$suffix"
    $nativeCurl = Get-Command curl.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
    $curl = if ($null -ne $nativeCurl) {
        $nativeCurl.Source
    } else {
        (Get-Command curl -CommandType Application -ErrorAction Stop |
            Select-Object -First 1).Source
    }
    $generateUrl = (
        "$frontendBase/api/v1/projects/$projectId/marketing-contents/" +
        "$contentId/generate?sourceVersionId=$sourceVersionId"
    )
    $generateText = & $curl --silent --show-error --fail-with-body `
        -H "X-User-Id: $($signup.data.user.id)" `
        -H "X-User-Role: USER" `
        -H "X-Request-Id: $requestId" `
        -H "Idempotency-Key: marketing-$suffix" `
        -F "image=@$sampleImage;type=image/png" $generateUrl
    if ($LASTEXITCODE -ne 0) {
        throw "Marketing generation request failed: $generateText"
    }
    $marketing = $generateText | ConvertFrom-Json
    $marketingJob = Wait-Job $marketing.data.jobId $headers
    if ($marketingJob.status -ne "SUCCEEDED") {
        throw "MARKETING_GENERATION failed: $($marketingJob.errorCode)"
    }
    $versions = Invoke-RestMethod -Uri (
        "$frontendBase/api/v1/projects/$projectId/marketing-contents/" +
        "$contentId/versions"
    ) -Headers $headers -TimeoutSec 15
    if ($versions.data.Count -ne 2 -or -not $versions.data[0].aiGenerated) {
        throw "Generated marketing version was not appended."
    }
    $marketingDownload = Invoke-WebRequest -UseBasicParsing -Uri (
        "$frontendBase/api/v1/projects/$projectId/ai-tasks/" +
        "$($marketing.data.jobId)/artifacts/result"
    ) -Headers $headers -TimeoutSec 15
    if ($marketingDownload.RawContentLength -ne (
        Get-Item -LiteralPath $sampleImage
    ).Length) {
        throw "Marketing result artifact content is invalid."
    }

    $headers["Idempotency-Key"] = "rerun-$suffix"
    $rerun = New-JsonPost (
        "$frontendBase/api/v1/projects/$projectId/marketing-contents/" +
        "$contentId/rerun"
    ) $headers @{ originalJobId = $marketing.data.jobId }
    $rerunJob = Wait-Job $rerun.data.jobId $headers
    if (
        $rerunJob.status -ne "SUCCEEDED" -or
        $rerun.data.jobId -eq $marketing.data.jobId
    ) {
        throw "Marketing rerun did not create a successful new job."
    }
    $rerunVersions = Invoke-RestMethod -Uri (
        "$frontendBase/api/v1/projects/$projectId/marketing-contents/" +
        "$contentId/versions"
    ) -Headers $headers -TimeoutSec 15
    if (
        $rerunVersions.data.Count -ne 3 -or
        $rerunVersions.data[1].analysisJobId -ne $marketing.data.jobId
    ) {
        throw "Rerun did not preserve the previous marketing result."
    }

    Write-Output (
        "Docker E2E passed: system=$($system.data.jobId), " +
        "artifact=$($artifact.data.jobId), marketing=$($marketing.data.jobId), " +
        "rerun=$($rerun.data.jobId)"
    )
} catch {
    $scriptFailed = $true
    $originalFailure = $_
    [Console]::Error.WriteLine(
        "Docker E2E failed: " + $originalFailure.Exception.Message
    )
    if ($composeAttempted) {
        Write-ComposeDiagnostics -ExitCode 1
    } else {
        [Console]::Error.WriteLine(
            "Compose was not started; no service diagnostics are available."
        )
    }
} finally {
    if (Test-Path -LiteralPath $sampleImage) {
        Remove-Item -LiteralPath $sampleImage -Force
    }
    if ($composeAttempted -and -not $KeepEnvironment) {
        try {
            & docker compose @composeFiles down --volumes --remove-orphans
        } catch {
            Write-Warning "Docker Compose cleanup failed."
        }
    }
    if ($locationPushed) {
        Pop-Location
    }
}
if ($scriptFailed) {
    [Console]::Error.WriteLine(
        "Docker E2E original failure preserved: " +
        $originalFailure.Exception.Message
    )
    exit 1
}
