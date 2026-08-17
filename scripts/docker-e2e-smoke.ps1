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
    if ($null -eq (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        return @()
    }
    return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Sort-Object OwningProcess -Unique | ForEach-Object {
            $process = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
            [pscustomobject]@{
                Port = $Port
                Pid = $_.OwningProcess
                Process = if ($null -eq $process) { "unknown" } else { $process.ProcessName }
            }
        })
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
    if ($ports | Group-Object Port | Where-Object Count -gt 1) {
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
    if ($conflicts.Count -eq 0) { return }
    foreach ($conflict in $conflicts) {
        [Console]::Error.WriteLine(
            "Port conflict: service=$($conflict.Service), port=$($conflict.Port), " +
            "pid=$($conflict.Pid), process=$($conflict.Process), override=$($conflict.Option)"
        )
    }
    throw "One or more Docker E2E published ports are already in use."
}

function Protect-DiagnosticText {
    param([AllowEmptyString()][string]$Text)
    $redacted = [string]$Text
    foreach ($name in @(
        "POSTGRES_PASSWORD", "JWT_SECRET", "MINIO_ROOT_PASSWORD",
        "OBJECT_STORAGE_SECRET_KEY", "AI_INTERNAL_SERVICE_TOKEN",
        "AI_SERVER_INTERNAL_API_KEY"
    )) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            $redacted = $redacted.Replace($value, "[REDACTED]")
        }
    }
    return $redacted -replace '(?i)(X-Amz-(Credential|Signature|Security-Token)=)[^&\s]+', '$1[REDACTED]'
}

function Write-ComposeDiagnostics {
    try {
        $output = & docker compose @composeFiles ps --all 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $output))
        $logs = & docker compose @composeFiles logs --no-color --tail 200 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $logs))
    } catch {
        [Console]::Error.WriteLine("Could not collect full Docker diagnostics.")
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
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Uri"
}

function Invoke-JsonPost {
    param([string]$Uri, [hashtable]$Headers, [object]$Body)
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers `
        -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10) `
        -TimeoutSec 30
}

function Start-CurrentTask {
    param(
        [long]$ProjectId,
        [hashtable]$Headers,
        [string]$IdempotencyKey,
        [ValidateSet("NORMAL", "ARTIFACT")][string]$Scenario
    )
    $commandHeaders = $Headers.Clone()
    $commandHeaders["Idempotency-Key"] = $IdempotencyKey
    return Invoke-JsonPost (
        "$backendBase/internal/e2e/projects/$ProjectId/task-runs"
    ) $commandHeaders @{ scenario = $Scenario }
}

function Wait-TaskRun {
    param(
        [long]$ProjectId,
        [string]$TaskRunId,
        [hashtable]$Headers,
        [int]$TimeoutSeconds = 60
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri (
            "$frontendBase/api/v2/projects/$ProjectId/task-runs/$TaskRunId"
        ) -Headers $Headers -TimeoutSec 10
        if ($response.data.state -in @(
            "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED", "NEEDS_INPUT"
        )) {
            return $response.data
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for TaskRun $TaskRunId"
}

function Get-CurrentResult {
    param([long]$ProjectId, [string]$TaskRunId, [hashtable]$Headers)
    return Invoke-RestMethod -Uri (
        "$backendBase/internal/e2e/projects/$ProjectId/task-runs/$TaskRunId/result"
    ) -Headers $Headers -TimeoutSec 15
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
    # A proxied backend endpoint closes the frontend-ready/backend-not-ready race.
    Wait-Http "$frontendBase/api/v1/service-policy" 60

    $initExit = (& docker compose @composeFiles ps --all `
        --format "{{.ExitCode}}" minio-init).Trim()
    if ($LASTEXITCODE -ne 0 -or $initExit -ne "0") {
        throw "MinIO bucket initialization did not complete successfully."
    }

    $suffix = [guid]::NewGuid().ToString("N")
    $requestId = [guid]::NewGuid().ToString()
    $signup = Invoke-JsonPost "$frontendBase/api/v1/auth/signup" @{} @{
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
        "X-Correlation-Id" = $requestId
    }
    $project = Invoke-JsonPost "$frontendBase/api/v1/projects" $headers @{
        title = "Docker E2E " + $suffix.Substring(0, 8)
        description = "Disposable current-pipeline integration project"
        industryCategory = "test"
    }
    $projectId = [long]$project.data.id

    $normalKey = "current-system-$suffix"
    $normal = Start-CurrentTask $projectId $headers $normalKey "NORMAL"
    $normalId = [string]$normal.data.taskRunId
    $normalTerminal = Wait-TaskRun $projectId $normalId $headers
    if ($normalTerminal.state -ne "SUCCEEDED") {
        throw "Current TaskRun normal smoke failed: $($normalTerminal.errorSummary.code)"
    }
    $normalResult = Get-CurrentResult $projectId $normalId $headers
    if ($normalResult.data.status -ne "processed") {
        throw "Current TaskResult was not adopted."
    }

    $replay = Start-CurrentTask $projectId $headers $normalKey "NORMAL"
    if ([string]$replay.data.taskRunId -ne $normalId -or -not $replay.data.replayed) {
        throw "Same idempotency key and canonical input did not replay the TaskRun."
    }

    $artifact = Start-CurrentTask $projectId $headers "current-artifact-$suffix" "ARTIFACT"
    $artifactTaskRunId = [string]$artifact.data.taskRunId
    $artifactTerminal = Wait-TaskRun $projectId $artifactTaskRunId $headers
    if ($artifactTerminal.state -ne "SUCCEEDED") {
        throw "Current artifact TaskRun failed: $($artifactTerminal.errorSummary.code)"
    }
    $artifactResult = Get-CurrentResult $projectId $artifactTaskRunId $headers
    $storedArtifactId = [string]$artifactResult.data.artifactId
    $artifactDownload = Invoke-WebRequest -UseBasicParsing -Uri (
        "$frontendBase/api/v3/projects/$projectId/evidence-artifacts/$storedArtifactId/download"
    ) -Headers $headers -TimeoutSec 15
    $artifactText = if ($artifactDownload.Content -is [byte[]]) {
        [Text.Encoding]::UTF8.GetString($artifactDownload.Content)
    } else {
        [string]$artifactDownload.Content
    }
    if ($artifactText -notmatch "current-task-run-artifact") {
        throw "Current evidence artifact content is invalid."
    }

    $recent = Invoke-RestMethod -Uri (
        "$frontendBase/api/v3/projects/$projectId/recent-jobs"
    ) -Headers $headers -TimeoutSec 15
    $recentIds = @($recent.data | ForEach-Object { [string]$_.taskRunId })
    if ($normalId -notin $recentIds -or $artifactTaskRunId -notin $recentIds) {
        throw "Current project job projection omitted completed TaskRuns."
    }

    $preserved = Get-CurrentResult $projectId $normalId $headers
    if ($preserved.data.status -ne "processed") {
        throw "A later run replaced the previous successful TaskResult."
    }

    Write-Output (
        "Docker E2E passed: currentTaskRun=$normalId, replay=$($replay.data.taskRunId), " +
        "artifactTaskRun=$artifactTaskRunId, artifact=$storedArtifactId"
    )
} catch {
    $scriptFailed = $true
    $originalFailure = $_
    [Console]::Error.WriteLine("Docker E2E failed: " + $_.Exception.Message)
    if ($composeAttempted) { Write-ComposeDiagnostics }
} finally {
    if ($composeAttempted -and -not $KeepEnvironment) {
        try { & docker compose @composeFiles down --volumes --remove-orphans }
        catch { Write-Warning "Docker Compose cleanup failed." }
    }
    if ($locationPushed) { Pop-Location }
}

if ($scriptFailed) {
    [Console]::Error.WriteLine(
        "Docker E2E original failure preserved: " + $originalFailure.Exception.Message
    )
    exit 1
}
