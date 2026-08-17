[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [ValidateSet("all", "ai-down", "minio-down", "malformed", "checksum", "timeout", "stale")]
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
$env:AI_SERVER_CONNECT_TIMEOUT = "1s"
$env:AI_SERVER_READ_TIMEOUT = "2s"

$frontendBase = "http://127.0.0.1:$FrontendPort"
$backendBase = "http://127.0.0.1:$BackendPort"
$aiBase = "http://127.0.0.1:$AiServerPort"

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & docker compose @composeFiles @Args
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Args -join ' ')"
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

function Wait-HttpUnavailable {
    param([string]$Uri, [int]$TimeoutSeconds = 30)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $null = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2
        } catch {
            if ($null -eq $_.Exception.Response) { return }
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Service remained reachable at $Uri"
}

function Invoke-JsonPost {
    param([string]$Uri, [hashtable]$Headers, [object]$Body)
    Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers `
        -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 10) `
        -TimeoutSec 30
}

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Start-Stack {
    Invoke-Compose config --quiet
    Invoke-Compose up --build --detach
    Wait-Http "$frontendBase/healthz" 180
    Wait-Http "$backendBase/actuator/health" 180
    Wait-Http "$aiBase/health/live" 60
    Wait-Http "$aiBase/health/ready" 60
    Wait-Http "$frontendBase/api/v1/service-policy" 60
    $initExit = (& docker compose @composeFiles ps --all `
        --format "{{.ExitCode}}" minio-init).Trim()
    Assert-Condition ($LASTEXITCODE -eq 0 -and $initExit -eq "0") `
        "MinIO initialization did not succeed."
}

function New-Fixture {
    $suffix = [guid]::NewGuid().ToString("N")
    $requestId = [guid]::NewGuid().ToString()
    $signup = Invoke-JsonPost "$frontendBase/api/v1/auth/signup" @{} @{
        username = "failure" + $suffix.Substring(0, 10)
        password = "Q7!" + $suffix.Substring(0, 20)
        displayName = "Failure E2E User"
        email = "failure-e2e-$suffix@example.com"
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
        title = "Failure E2E " + $suffix.Substring(0, 8)
        description = "Disposable current TaskRun failure fixture"
        industryCategory = "test"
    }
    return [pscustomobject]@{
        Suffix = $suffix
        ProjectId = [long]$project.data.id
        Headers = $headers
    }
}

function Start-CurrentTask {
    param([object]$Fixture, [string]$Scenario, [string]$Key)
    $headers = $Fixture.Headers.Clone()
    $headers["Idempotency-Key"] = $Key
    $response = Invoke-JsonPost (
        "$backendBase/internal/e2e/projects/$($Fixture.ProjectId)/task-runs"
    ) $headers @{ scenario = $Scenario }
    return [string]$response.data.taskRunId
}

function Wait-TaskRun {
    param([object]$Fixture, [string]$TaskRunId, [int]$TimeoutSeconds = 60)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $response = Invoke-RestMethod -Uri (
            "$frontendBase/api/v2/projects/$($Fixture.ProjectId)/task-runs/$TaskRunId"
        ) -Headers $Fixture.Headers -TimeoutSec 10
        if ($response.data.state -in @(
            "SUCCEEDED", "FAILED", "TIMED_OUT", "CANCELLED", "NEEDS_INPUT"
        )) { return $response.data }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for TaskRun $TaskRunId"
}

function Get-CurrentResult {
    param([object]$Fixture, [string]$TaskRunId)
    return Invoke-RestMethod -Uri (
        "$backendBase/internal/e2e/projects/$($Fixture.ProjectId)/task-runs/$TaskRunId/result"
    ) -Headers $Fixture.Headers -TimeoutSec 15
}

function Invoke-DbScalar {
    param([string]$Sql)
    $value = & docker compose @composeFiles exec -T postgres `
        psql -U aivle -d aivle -tAc $Sql
    if ($LASTEXITCODE -ne 0) { throw "Database verification query failed." }
    return ([string]$value).Trim()
}

function Assert-NoAdoptedResult {
    param([string]$TaskRunId)
    $count = Invoke-DbScalar (
        "select count(*) from task_results where task_run_id='$TaskRunId' " +
        "and validation_state='ADOPTED'"
    )
    Assert-Condition ($count -eq "0") "Failed TaskRun promoted a canonical TaskResult."
}

function Assert-BaselinePreserved {
    param([object]$Fixture, [string]$TaskRunId)
    $result = Get-CurrentResult $Fixture $TaskRunId
    Assert-Condition ($result.data.status -eq "processed") `
        "Previous successful TaskResult was not preserved."
    $count = Invoke-DbScalar (
        "select count(*) from task_results where task_run_id='$TaskRunId' " +
        "and validation_state='ADOPTED'"
    )
    Assert-Condition ($count -eq "1") "Previous successful TaskResult authority changed."
}

function New-Baseline {
    param([object]$Fixture)
    $id = Start-CurrentTask $Fixture "NORMAL" ("baseline-" + $Fixture.Suffix)
    $terminal = Wait-TaskRun $Fixture $id
    Assert-Condition ($terminal.state -eq "SUCCEEDED") "Baseline TaskRun failed."
    return $id
}

function Assert-PublicFailureSafe {
    param([object]$Terminal)
    $serialized = $Terminal | ConvertTo-Json -Depth 10 -Compress
    foreach ($forbidden in @("Bearer ", "AI_INTERNAL_SERVICE_TOKEN", "AI_SERVER_INTERNAL_API_KEY")) {
        Assert-Condition (-not $serialized.Contains($forbidden)) `
            "Public TaskRun response exposed internal provider detail."
    }
}

function Scenario-AiDown {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    Invoke-Compose stop ai-server
    Wait-HttpUnavailable "$aiBase/health/ready" 30
    $failedId = Start-CurrentTask $fixture "AI_DEPENDENCY" ("ai-down-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "FAILED") "ai-down did not reach FAILED."
    Assert-Condition ($failed.retryable -eq $true) "ai-down should remain manually retryable."
    Assert-NoAdoptedResult $failedId
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
}

function Scenario-MinIoDown {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    $before = Invoke-DbScalar (
        "select count(*) from project_evidence_artifacts where project_id=$($fixture.ProjectId)"
    )
    Invoke-Compose stop minio
    Wait-HttpUnavailable "http://127.0.0.1:$MinioPort/minio/health/live" 30
    $failedId = Start-CurrentTask $fixture "ARTIFACT" ("minio-down-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "FAILED") "minio-down did not reach FAILED."
    Assert-NoAdoptedResult $failedId
    $after = Invoke-DbScalar (
        "select count(*) from project_evidence_artifacts where project_id=$($fixture.ProjectId)"
    )
    Assert-Condition ($after -eq $before) "Failed artifact storage promoted metadata."
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
}

function Scenario-Malformed {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    $failedId = Start-CurrentTask $fixture "MALFORMED" ("malformed-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "FAILED") "malformed did not reach FAILED."
    Assert-NoAdoptedResult $failedId
    $rejected = Invoke-DbScalar (
        "select count(*) from task_results where task_run_id='$failedId' and validation_state='REJECTED'"
    )
    Assert-Condition ($rejected -eq "1") "Malformed result was not rejected explicitly."
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
}

function Scenario-Checksum {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    $failedId = Start-CurrentTask $fixture "CHECKSUM" ("checksum-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "FAILED") "checksum did not reach FAILED."
    Assert-NoAdoptedResult $failedId
    $rejected = Invoke-DbScalar (
        "select count(*) from task_results where task_run_id='$failedId' " +
        "and validation_state='REJECTED' and rejection_code='HASH_MISMATCH'"
    )
    Assert-Condition ($rejected -eq "1") "Checksum mismatch was not rejected by TaskResult authority."
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
}

function Scenario-Timeout {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    $failedId = Start-CurrentTask $fixture "TIMEOUT" ("timeout-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "TIMED_OUT") "timeout did not reach TIMED_OUT."
    $attempts = Invoke-DbScalar "select attempt_count from task_runs where id='$failedId'"
    Assert-Condition ($attempts -eq "1") "Timeout was silently replayed."
    Assert-NoAdoptedResult $failedId
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
}

function Scenario-Stale {
    Start-Stack
    $fixture = New-Fixture
    $baseline = New-Baseline $fixture
    $failedId = Start-CurrentTask $fixture "STALE" ("stale-" + $fixture.Suffix)
    $failed = Wait-TaskRun $fixture $failedId
    Assert-Condition ($failed.state -eq "TIMED_OUT") "stale did not preserve terminal timeout."
    Assert-NoAdoptedResult $failedId
    $rejected = Invoke-DbScalar (
        "select count(*) from task_results where task_run_id='$failedId' " +
        "and validation_state='REJECTED' and rejection_code='LATE_OR_DUPLICATE_RESULT'"
    )
    Assert-Condition ($rejected -eq "1") "Late completion was not retained as rejected history."
    Assert-BaselinePreserved $fixture $baseline
    Assert-PublicFailureSafe $failed
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
    return $redacted
}

function Write-Diagnostics {
    try {
        $output = & docker compose @composeFiles ps --all 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $output))
        $logs = & docker compose @composeFiles logs --no-color --tail 160 2>&1 | Out-String
        [Console]::Error.WriteLine((Protect-DiagnosticText $logs))
    } catch {
        [Console]::Error.WriteLine("Failure E2E diagnostics could not be collected.")
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
        try { & docker compose @composeFiles down --volumes --remove-orphans }
        catch { [Console]::Error.WriteLine("Failure scenario cleanup failed: $Name") }
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

$preflightArguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $normalSmoke,
    "-FrontendPort", "$FrontendPort", "-BackendPort", "$BackendPort",
    "-AiServerPort", "$AiServerPort", "-PostgresPort", "$PostgresPort",
    "-MinioPort", "$MinioPort", "-MinioConsolePort", "$MinioConsolePort",
    "-PreflightOnly"
)
if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    $preflightArguments += @("-EnvFile", $resolvedEnv)
}
& powershell @preflightArguments
if ($LASTEXITCODE -ne 0) { throw "Failure E2E port preflight failed." }

if ($Scenario -eq "all") {
    foreach ($entry in $scenarios.GetEnumerator()) {
        Invoke-IsolatedScenario $entry.Key $entry.Value
    }
} else {
    Invoke-IsolatedScenario $Scenario $scenarios[$Scenario]
}
Write-Output "All requested Docker failure scenarios passed."
