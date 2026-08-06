[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [ValidateSet(
        "all",
        "ai-down",
        "minio-down",
        "malformed",
        "checksum",
        "timeout",
        "stale"
    )]
    [string]$Scenario = "all",
    [int]$FrontendPort = 3000,
    [int]$BackendPort = 8080,
    [int]$AiServerPort = 8000,
    [int]$PostgresPort = 5432,
    [int]$MinioPort = 9000,
    [int]$MinioConsolePort = 9001
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$normalSmoke = Join-Path $PSScriptRoot "docker-e2e-smoke.ps1"
$composeFiles = @(
    "-f", (Join-Path $root "compose.yaml"),
    "-f", (Join-Path $root "compose.e2e.yaml")
)
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    $resolvedEnv = (Resolve-Path -LiteralPath $EnvFile).Path
    $composeFiles = @("--env-file", $resolvedEnv) + $composeFiles
}

$env:FRONTEND_PORT = "$FrontendPort"
$env:BACKEND_PORT = "$BackendPort"
$env:AI_SERVER_PORT = "$AiServerPort"
$env:POSTGRES_PORT = "$PostgresPort"
$env:MINIO_API_PORT = "$MinioPort"
$env:MINIO_CONSOLE_PORT = "$MinioConsolePort"
$env:AI_SERVER_READ_TIMEOUT = "2s"
$env:DOCUMENT_JOB_POLL_INTERVAL = "1s"
$env:DOCUMENT_JOB_RECOVERY_INTERVAL = "1s"
$env:DOCUMENT_JOB_EXECUTION_TIMEOUT = "10s"
$env:DOCUMENT_JOB_STALE_TIMEOUT = "11s"

$frontendBase = "http://127.0.0.1:$FrontendPort"
$backendBase = "http://127.0.0.1:$BackendPort"
$aiBase = "http://127.0.0.1:$AiServerPort"
$minioBase = "http://127.0.0.1:$MinioPort"
$sampleImage = Join-Path ([IO.Path]::GetTempPath()) (
    "aivle-failure-e2e-" + [guid]::NewGuid().ToString("N") + ".png"
)

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker compose @composeFiles @Args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Args -join ' ')"
    }
}

function Wait-Http {
    param([string]$Uri, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri `
                -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Uri"
}

function Wait-HttpReachable {
    param([string]$Uri, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $null = Invoke-WebRequest -UseBasicParsing -Uri $Uri `
                -TimeoutSec 3
            return
        } catch {
            if ($null -ne $_.Exception.Response) {
                return
            }
            Start-Sleep -Milliseconds 500
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for reachable HTTP service at $Uri"
}

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers `
        -ContentType "application/json" -Body ($Body | ConvertTo-Json) `
        -TimeoutSec 30
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
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for job $JobId"
}

function Get-Job {
    param([long]$JobId, [hashtable]$Headers)
    (Invoke-RestMethod -Uri (
        "$frontendBase/api/v1/jobs/$JobId"
    ) -Headers $Headers -TimeoutSec 10).data
}

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
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
    return $redacted -replace (
        '(?i)(X-Amz-(Credential|Signature|Security-Token)=)[^&\s]+'
    ), '$1[REDACTED]'
}

function Assert-FailedJob {
    param(
        [object]$Job,
        [string]$ErrorCode,
        [bool]$Retryable
    )
    Assert-Condition ($Job.status -eq "FAILED") (
        "Expected FAILED, got $($Job.status)"
    )
    Assert-Condition ($Job.errorCode -eq $ErrorCode) (
        "Expected $ErrorCode, got $($Job.errorCode)"
    )
    Assert-Condition ([bool]$Job.retryable -eq $Retryable) (
        "Unexpected retryable value for job $($Job.jobId)"
    )
    Assert-Condition ($null -eq $Job.resultReferenceId) (
        "Failed job has a result reference."
    )
    Assert-Condition (
        ([string]$Job.message) -notmatch (
            "(?i)traceback|stack trace|exception|connection refused|" +
            "x-amz-|https?://"
        )
    ) "Failed job exposed unsafe internal details."
}

function New-Fixture {
    $suffix = [guid]::NewGuid().ToString("N")
    $requestId = [guid]::NewGuid().ToString()
    $signup = Invoke-JsonPost "$frontendBase/api/v1/auth/signup" @{} @{
        username = "fail" + $suffix.Substring(0, 12)
        password = "Q7!" + $suffix.Substring(0, 20)
        displayName = "Failure E2E User"
        email = "failure-$suffix@example.com"
        organizationName = $null
        departmentName = $null
        jobTitle = $null
    }
    $headers = @{
        "X-User-Id" = "$($signup.data.user.id)"
        "X-User-Role" = "USER"
        "X-Request-Id" = $requestId
    }
    $project = Invoke-JsonPost "$frontendBase/api/v1/projects" $headers @{
        title = "Failure E2E " + $suffix.Substring(0, 8)
        description = "Disposable failure scenario"
        industryCategory = "test"
    }
    [pscustomobject]@{
        Suffix = $suffix
        UserId = $signup.data.user.id
        ProjectId = $project.data.id
        Headers = $headers
    }
}

function Start-SystemJob {
    param([object]$Fixture, [string]$Key)
    $Fixture.Headers["Idempotency-Key"] = $Key
    (Invoke-JsonPost (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/ai-tasks/smoke"
    ) $Fixture.Headers @{}).data
}

function Start-ArtifactJob {
    param([object]$Fixture, [string]$Key)
    $Fixture.Headers["Idempotency-Key"] = $Key
    (Invoke-JsonPost (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/ai-tasks/artifact-smoke"
    ) $Fixture.Headers @{}).data
}

function Wake-Job {
    param([object]$Fixture, [long]$JobId)
    Invoke-RestMethod -Method Post -Uri (
        "$backendBase/internal/e2e/jobs/$JobId/wake"
    ) -Headers $Fixture.Headers -TimeoutSec 30
}

function Download-Artifact {
    param([object]$Fixture, [long]$JobId)
    Invoke-WebRequest -UseBasicParsing -Uri (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/ai-tasks/$JobId/artifacts/result"
    ) -Headers $Fixture.Headers -TimeoutSec 15
}

function New-MarketingContent {
    param([object]$Fixture)
    $content = Invoke-JsonPost (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/marketing-contents"
    ) $Fixture.Headers @{
        title = "Failure Campaign"
        purpose = "PRODUCT_INTRODUCTION"
        channel = "SOCIAL"
        format = "SQUARE_1080"
        width = $null
        height = $null
        personaId = $null
        targetOffer = "Failure-safe service"
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
    [pscustomobject]@{
        ContentId = $content.data.content.id
        VersionId = $content.data.current.id
    }
}

function Start-MarketingJob {
    param(
        [object]$Fixture,
        [object]$Content,
        [string]$Key
    )
    $curlCommand = Get-Command curl.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $curlCommand) {
        $curlCommand = Get-Command curl -CommandType Application `
            -ErrorAction Stop |
            Select-Object -First 1
    }
    $url = (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/marketing-contents/$($Content.ContentId)/generate" +
        "?sourceVersionId=$($Content.VersionId)"
    )
    $text = & $curlCommand.Source --silent --show-error `
        --fail-with-body `
        -H "X-User-Id: $($Fixture.UserId)" `
        -H "X-User-Role: USER" `
        -H "X-Request-Id: $($Fixture.Headers['X-Request-Id'])" `
        -H "Idempotency-Key: $Key" `
        -F "image=@$sampleImage;type=image/png" $url
    if ($LASTEXITCODE -ne 0) {
        throw "Marketing request failed: $text"
    }
    ($text | ConvertFrom-Json).data
}

function Start-MarketingRerun {
    param(
        [object]$Fixture,
        [object]$Content,
        [long]$OriginalJobId,
        [string]$Key
    )
    $Fixture.Headers["Idempotency-Key"] = $Key
    (Invoke-JsonPost (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/marketing-contents/$($Content.ContentId)/rerun"
    ) $Fixture.Headers @{ originalJobId = $OriginalJobId }).data
}

function Get-MarketingVersions {
    param([object]$Fixture, [object]$Content)
    (Invoke-RestMethod -Uri (
        "$frontendBase/api/v1/projects/$($Fixture.ProjectId)" +
        "/marketing-contents/$($Content.ContentId)/versions"
    ) -Headers $Fixture.Headers -TimeoutSec 15).data
}

function Invoke-DbScalar {
    param([string]$Sql)
    $value = & docker compose @composeFiles exec -T postgres `
        psql -U aivle -d aivle -tAc $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "Database verification query failed."
    }
    return ([string]$value).Trim()
}

function Wait-DbJobTerminal {
    param([long]$JobId, [int]$TimeoutSeconds = 60)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $status = Invoke-DbScalar (
            "select status from analysis_jobs where id = $JobId"
        )
        if ($status -in @("SUCCEEDED", "FAILED")) {
            return $status
        }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for database job $JobId"
}

function Assert-NoResultMetadata {
    param([long]$JobId)
    $resultCount = Invoke-DbScalar (
        "select count(*) from ai_task_results " +
        "where analysis_job_id = $JobId"
    )
    $artifactCount = Invoke-DbScalar (
        "select count(*) from ai_task_artifacts " +
        "where analysis_job_id = $JobId and role = 'RESULT'"
    )
    $versionCount = Invoke-DbScalar (
        "select count(*) from marketing_content_versions " +
        "where analysis_job_id = $JobId"
    )
    Assert-Condition ($resultCount -eq "0") "Failed job has AiTaskResult."
    Assert-Condition ($artifactCount -eq "0") "Failed job has RESULT artifact."
    Assert-Condition ($versionCount -eq "0") "Failed job has marketing version."
}

function Get-MinIoObjectCount {
    $inventory = @'
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc ls --recursive "local/$MINIO_BUCKET"
'@
    $lines = @(& docker compose @composeFiles run --rm --no-deps `
        --entrypoint /bin/sh minio-init -ec $inventory)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect MinIO object inventory."
    }
    return @($lines | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_)
    }).Count
}

function Set-AiFault {
    param([string]$Mode)
    $env:AI_E2E_FAULTS_ENABLED = if ($Mode) { "true" } else { "false" }
    $env:AI_E2E_FAULT_MODE = $Mode
    Invoke-Compose up --no-deps --force-recreate --detach ai-server
    Wait-Http "$aiBase/health/ready"
}

function Set-RunnerEnabled {
    param(
        [bool]$Enabled,
        [bool]$AcceptUnhealthy = $false
    )
    $env:DOCUMENT_JOB_RUNNER_ENABLED = if ($Enabled) { "true" } else { "false" }
    Invoke-Compose up --no-deps --force-recreate --detach backend
    if ($AcceptUnhealthy) {
        Wait-HttpReachable "$backendBase/actuator/health"
    } else {
        Wait-Http "$backendBase/actuator/health"
    }
}

function Start-Stack {
    param(
        [bool]$RunnerEnabled = $true,
        [bool]$DeferArtifactWake = $false
    )
    $env:AI_E2E_FAULTS_ENABLED = "false"
    $env:AI_E2E_FAULT_MODE = ""
    $env:DOCUMENT_JOB_RUNNER_ENABLED = if ($RunnerEnabled) {
        "true"
    } else {
        "false"
    }
    $env:APP_E2E_DEFER_ARTIFACT_WAKE = if ($DeferArtifactWake) {
        "true"
    } else {
        "false"
    }
    $env:DOCUMENT_JOB_POLL_INTERVAL = if ($DeferArtifactWake) {
        "1h"
    } else {
        "1s"
    }
    $env:DOCUMENT_JOB_RECOVERY_INTERVAL = if ($DeferArtifactWake) {
        "1h"
    } else {
        "1s"
    }
    Invoke-Compose up --build --detach
    Wait-Http "$frontendBase/healthz" 180
    Wait-Http "$backendBase/actuator/health" 180
    Wait-Http "$aiBase/health/ready" 90
    Wait-Http "$minioBase/minio/health/ready" 90
}

function Assert-BaselineJobPreserved {
    param([long]$JobId, [hashtable]$Headers)
    $job = Get-Job $JobId $Headers
    Assert-Condition ($job.status -eq "SUCCEEDED") (
        "Previous successful job was not preserved."
    )
    Assert-Condition ($null -ne $job.resultReferenceId) (
        "Previous successful result reference was lost."
    )
}

function Scenario-AiDown {
    Start-Stack
    $fixture = New-Fixture
    $content = New-MarketingContent $fixture
    $first = Start-MarketingJob $fixture $content (
        "ai-down-ok-" + $fixture.Suffix
    )
    $firstJob = Wait-Job $first.jobId $fixture.Headers
    Assert-Condition ($firstJob.status -eq "SUCCEEDED") "Baseline marketing failed."
    $beforeVersions = @(Get-MarketingVersions $fixture $content).Count
    Invoke-Compose stop ai-server
    $failedStart = Start-MarketingRerun $fixture $content $first.jobId (
        "ai-down-fail-" + $fixture.Suffix
    )
    $failed = Wait-Job $failedStart.jobId $fixture.Headers
    Assert-FailedJob $failed "AI_SERVER_TIMEOUT" $true
    Assert-NoResultMetadata $failed.jobId
    Assert-Condition (
        @(Get-MarketingVersions $fixture $content).Count -eq $beforeVersions
    ) "AI outage created a marketing version."
    Assert-BaselineJobPreserved $first.jobId $fixture.Headers
    $null = Download-Artifact $fixture $first.jobId
}

function Scenario-MinIoDown {
    Start-Stack $true $true
    $fixture = New-Fixture
    $baseline = Start-ArtifactJob $fixture (
        "minio-ok-" + $fixture.Suffix
    )
    Wake-Job $fixture $baseline.jobId
    $baselineJob = Wait-Job $baseline.jobId $fixture.Headers
    Assert-Condition ($baselineJob.status -eq "SUCCEEDED") "Baseline artifact failed."
    $queued = Start-ArtifactJob $fixture (
        "minio-fail-" + $fixture.Suffix
    )
    Assert-Condition ($queued.status -eq "QUEUED") "Artifact job was not queued."
    $beforeObjects = Get-MinIoObjectCount
    Invoke-Compose stop minio
    try {
        Wake-Job $fixture $queued.jobId
    } catch {
        # The wake request can outlive the client timeout while the runner
        # records the terminal failure. The database remains authoritative.
    }
    $terminalStatus = Wait-DbJobTerminal $queued.jobId
    Assert-Condition ($terminalStatus -eq "FAILED") (
        "MinIO outage did not fail the artifact job."
    )
    Invoke-Compose start minio
    Wait-Http "$minioBase/minio/health/ready"
    Wait-HttpReachable "$backendBase/actuator/health"
    $failed = Get-Job $queued.jobId $fixture.Headers
    Assert-Condition (
        $failed.errorCode -in @(
            "AI_TASK_EXECUTION_FAILED",
            "AI_TASK_TIMEOUT"
        )
    ) "Unexpected MinIO failure code: $($failed.errorCode)"
    Assert-FailedJob $failed $failed.errorCode $false
    Assert-NoResultMetadata $failed.jobId
    Assert-Condition (
        (Get-MinIoObjectCount) -eq $beforeObjects
    ) "MinIO outage left an unexpected output object."
    Assert-BaselineJobPreserved $baseline.jobId $fixture.Headers
    $null = Download-Artifact $fixture $baseline.jobId
}

function Scenario-Malformed {
    Start-Stack
    $fixture = New-Fixture
    $baseline = Start-SystemJob $fixture (
        "malformed-ok-" + $fixture.Suffix
    )
    $null = Wait-Job $baseline.jobId $fixture.Headers
    Set-AiFault "malformed_response"
    $start = Start-SystemJob $fixture (
        "malformed-fail-" + $fixture.Suffix
    )
    $failed = Wait-Job $start.jobId $fixture.Headers
    Assert-FailedJob $failed "AI_SERVER_INVALID_RESPONSE" $false
    Assert-NoResultMetadata $failed.jobId
    Assert-BaselineJobPreserved $baseline.jobId $fixture.Headers
}

function Scenario-Checksum {
    Start-Stack
    $fixture = New-Fixture
    $baseline = Start-ArtifactJob $fixture (
        "checksum-ok-" + $fixture.Suffix
    )
    $null = Wait-Job $baseline.jobId $fixture.Headers
    $beforeObjects = Get-MinIoObjectCount
    Set-AiFault "checksum_mismatch"
    $start = Start-ArtifactJob $fixture (
        "checksum-fail-" + $fixture.Suffix
    )
    $failed = Wait-Job $start.jobId $fixture.Headers
    Assert-FailedJob $failed "AI_TASK_EXECUTION_FAILED" $false
    Assert-NoResultMetadata $failed.jobId
    Assert-Condition (
        (Get-MinIoObjectCount) -eq ($beforeObjects + 1)
    ) "Checksum failure did not clean the generated output object."
    Assert-BaselineJobPreserved $baseline.jobId $fixture.Headers
    $null = Download-Artifact $fixture $baseline.jobId
}

function Scenario-Timeout {
    Start-Stack
    $fixture = New-Fixture
    $baseline = Start-SystemJob $fixture (
        "timeout-ok-" + $fixture.Suffix
    )
    $null = Wait-Job $baseline.jobId $fixture.Headers
    Set-AiFault "timeout"
    $start = Start-SystemJob $fixture (
        "timeout-fail-" + $fixture.Suffix
    )
    $failed = Wait-Job $start.jobId $fixture.Headers
    Assert-FailedJob $failed "AI_SERVER_TIMEOUT" $true
    Assert-Condition ($failed.attempt -eq 1) "Timeout was automatically retried."
    Start-Sleep -Seconds 3
    $terminal = Get-Job $failed.jobId $fixture.Headers
    Assert-Condition (
        $terminal.status -eq "FAILED" -and $terminal.attempt -eq 1
    ) "Timeout failure was automatically replayed."
    Assert-NoResultMetadata $failed.jobId
    Assert-BaselineJobPreserved $baseline.jobId $fixture.Headers
}

function Scenario-Stale {
    Start-Stack
    $fixture = New-Fixture
    $baseline = Start-SystemJob $fixture (
        "stale-ok-" + $fixture.Suffix
    )
    $null = Wait-Job $baseline.jobId $fixture.Headers
    Set-RunnerEnabled $false
    $queued = Start-SystemJob $fixture (
        "stale-fail-" + $fixture.Suffix
    )
    $old = "now() - interval '10 minutes'"
    $null = Invoke-DbScalar (
        "update analysis_jobs set status='RUNNING', attempt_count=1, " +
        "progress=5, current_step='CLAIMED', claim_token='e2e-stale', " +
        "claimed_by='e2e', claimed_at=$old, heartbeat_at=$old " +
        "where id=$($queued.jobId); select count(*) from analysis_jobs " +
        "where id=$($queued.jobId) and status='RUNNING'"
    )
    Set-RunnerEnabled $true
    $failed = Wait-Job $queued.jobId $fixture.Headers
    Assert-FailedJob $failed "STALE_EXECUTION" $false
    Assert-Condition ($failed.attempt -eq 1) "Stale AI job was replayed."
    Assert-NoResultMetadata $failed.jobId
    Assert-BaselineJobPreserved $baseline.jobId $fixture.Headers
}

function Write-Diagnostics {
    try {
        $psOutput = & docker compose @composeFiles ps --all 2>&1 |
            Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $psOutput))
    } catch {
        [Console]::Error.WriteLine(
            "Failure E2E compose ps could not be collected."
        )
    }
    try {
        $logs = & docker compose @composeFiles logs `
            --no-color --tail 120 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $logs))
    } catch {
        [Console]::Error.WriteLine(
            "Failure E2E logs could not be collected."
        )
    }
}

function Invoke-IsolatedScenario {
    param([string]$Name, [scriptblock]$Body)
    Write-Output "Starting isolated failure scenario: $Name"
    try {
        Invoke-Compose down --volumes --remove-orphans
        & $Body
        Write-Output "Failure scenario passed: $Name"
    } catch {
        [Console]::Error.WriteLine(
            "Failure scenario failed: $Name - " + $_.Exception.Message
        )
        Write-Diagnostics
        throw
    } finally {
        try {
            & docker compose @composeFiles down `
                --volumes --remove-orphans
        } catch {
            [Console]::Error.WriteLine(
                "Failure scenario cleanup failed: $Name"
            )
        }
    }
}

$scenarios = [ordered]@{
    "ai-down" = { Scenario-AiDown }
    "minio-down" = { Scenario-MinIoDown }
    "malformed" = { Scenario-Malformed }
    "checksum" = { Scenario-Checksum }
    "timeout" = { Scenario-Timeout }
    "stale" = { Scenario-Stale }
}

try {
    [IO.File]::WriteAllBytes(
        $sampleImage,
        [Convert]::FromBase64String(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
    )
    $preflightArguments = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $normalSmoke,
        "-FrontendPort", "$FrontendPort",
        "-BackendPort", "$BackendPort",
        "-AiServerPort", "$AiServerPort",
        "-PostgresPort", "$PostgresPort",
        "-MinioPort", "$MinioPort",
        "-MinioConsolePort", "$MinioConsolePort",
        "-PreflightOnly"
    )
    if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
        $preflightArguments += @("-EnvFile", $resolvedEnv)
    }
    & powershell @preflightArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Failure E2E port preflight failed."
    }

    if ($Scenario -eq "all") {
        foreach ($entry in $scenarios.GetEnumerator()) {
            Invoke-IsolatedScenario $entry.Key $entry.Value
        }
    } else {
        Invoke-IsolatedScenario $Scenario $scenarios[$Scenario]
    }
    Write-Output "All requested Docker failure scenarios passed."
} finally {
    if (Test-Path -LiteralPath $sampleImage) {
        Remove-Item -LiteralPath $sampleImage -Force
    }
}
