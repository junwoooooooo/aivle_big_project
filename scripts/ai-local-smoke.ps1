[CmdletBinding()]
param(
    [int]$AiPort = 8000,
    [int]$SpringPort = 8080,
    [int]$StartupTimeoutSeconds = 120,
    [switch]$JobSmoke,
    [switch]$ArtifactSmoke,
    [switch]$MarketingSmoke,
    [string]$ObjectStorageEndpoint = "http://127.0.0.1:9000",
    [string]$ObjectStorageAccessKey = "aivle-local",
    [string]$ObjectStorageSecretKey = "replace-with-local-secret"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$aiRoot = Join-Path $repoRoot "ai"
$backendRoot = Join-Path $repoRoot "backend"
$python = Join-Path $aiRoot ".venv\Scripts\python.exe"
$gradle = Join-Path $backendRoot "gradlew.bat"
$runtimeRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "aivle-ai-smoke-" + [guid]::NewGuid().ToString("N")
)
$aiOut = Join-Path $runtimeRoot "ai.stdout.log"
$aiErr = Join-Path $runtimeRoot "ai.stderr.log"
$springOut = Join-Path $runtimeRoot "spring.stdout.log"
$springErr = Join-Path $runtimeRoot "spring.stderr.log"
$sampleImage = Join-Path $runtimeRoot "smoke.png"
$generatedMock = $null
$aiProcess = $null
$springProcess = $null

function Test-PortInUse {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(250)) {
            return $false
        }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-Http {
    param(
        [string]$Url,
        [int]$TimeoutSeconds,
        [hashtable]$Headers = @{}
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url `
                -Headers $Headers -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for $Url"
}

function Stop-ProcessTree {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process -or $Process.HasExited) {
        return
    }
    & taskkill.exe /PID $Process.Id /T /F *> $null
}

if (-not (Test-Path -LiteralPath $python)) {
    throw "Missing AI virtual environment: $python"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Missing Gradle wrapper: $gradle"
}
if (Test-PortInUse $AiPort) {
    throw "Port $AiPort is already in use."
}
if (Test-PortInUse $SpringPort) {
    throw "Port $SpringPort is already in use."
}

New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
[System.IO.File]::WriteAllBytes(
    $sampleImage,
    [byte[]](137, 80, 78, 71, 13, 10, 26, 10, 65, 73, 100, 101, 118)
)

try {
    $aiProcess = Start-Process -FilePath $python `
        -ArgumentList @(
            "-m", "uvicorn", "main:app",
            "--host", "127.0.0.1",
            "--port", "$AiPort"
        ) `
        -WorkingDirectory $aiRoot `
        -RedirectStandardOutput $aiOut `
        -RedirectStandardError $aiErr `
        -WindowStyle Hidden `
        -PassThru
    Wait-Http "http://127.0.0.1:$AiPort/health/ready" 30

    $env:AI_SERVER_BASE_URL = "http://127.0.0.1:$AiPort"
    $env:JWT_SECRET = (
        [guid]::NewGuid().ToString("N") +
        [guid]::NewGuid().ToString("N")
    )
    $env:SPRING_DATASOURCE_URL = (
        "jdbc:h2:mem:ai_smoke;MODE=PostgreSQL;" +
        "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    )
    $env:FILE_STORAGE_ROOT = Join-Path $runtimeRoot "files"
    $env:SPRING_PROFILES_ACTIVE = "local,dev-header-auth"
    $env:SERVER_PORT = "$SpringPort"
    $env:DOCUMENT_JOB_RUNNER_ENABLED = if (
        $JobSmoke -or $ArtifactSmoke -or $MarketingSmoke
    ) {
        "true"
    } else {
        "false"
    }
    if ($ArtifactSmoke -or $MarketingSmoke) {
        $env:OBJECT_STORAGE_PROVIDER = "s3"
        $env:OBJECT_STORAGE_ENDPOINT = $ObjectStorageEndpoint
        $env:OBJECT_STORAGE_PUBLIC_ENDPOINT = $ObjectStorageEndpoint
        $env:OBJECT_STORAGE_ACCESS_KEY = $ObjectStorageAccessKey
        $env:OBJECT_STORAGE_SECRET_KEY = $ObjectStorageSecretKey
        $env:OBJECT_STORAGE_BUCKET = "aivle-ai-artifacts"
        $env:AI_ARTIFACT_ALLOWED_ORIGINS = $ObjectStorageEndpoint
    }

    $springProcess = Start-Process -FilePath $gradle `
        -ArgumentList "bootRun" `
        -WorkingDirectory $backendRoot `
        -RedirectStandardOutput $springOut `
        -RedirectStandardError $springErr `
        -WindowStyle Hidden `
        -PassThru
    $startupHeaders = @{
        "X-User-Id" = "1"
        "X-User-Role" = "USER"
        "X-Request-Id" = "smoke-startup"
    }
    Wait-Http (
        "http://127.0.0.1:$SpringPort" +
        "/api/v1/test/ai-server/health"
    ) $StartupTimeoutSeconds $startupHeaders

    $requestId = "smoke-" + [guid]::NewGuid().ToString("N")
    $commonHeaders = @{
        "X-User-Id" = "1"
        "X-User-Role" = "USER"
        "X-Request-Id" = $requestId
    }
    $health = Invoke-RestMethod `
        -Uri "http://127.0.0.1:$SpringPort/api/v1/test/ai-server/health" `
        -Headers $commonHeaders `
        -TimeoutSec 10
    if ($health.status -ne "ready" -or $health.request_id -ne $requestId) {
        throw "Spring health relay did not preserve the request ID."
    }

    $mood = -join [char[]](
        0xC2E0, 0xB8B0, 0xAC10, 0x20, 0xC788, 0xB294
    )
    $bannerFormat = -join [char[]](
        0xAC00, 0xB85C, 0xD615, 0x20, 0xBC30, 0xB108
    )
    $curlArguments = @(
        "--silent", "--show-error", "--fail-with-body",
        "-H", "X-User-Id: 1",
        "-H", "X-User-Role: USER",
        "-H", "X-Request-Id: $requestId",
        "-F", "promotion_name=Smoke promotion",
        "-F", "main_banner=Smoke banner",
        "-F", "supporting_copy=Smoke copy",
        "-F", "mood=$mood",
        "-F", "banner_format=$bannerFormat",
        "-F", "emphasis_keywords=smoke,contract,smoke",
        "-F", "image=@$sampleImage;type=image/png;filename=smoke.png",
        "http://127.0.0.1:$SpringPort/api/v1/test/ai-server/marketing/banners/generate"
    )
    $marketingText = & curl.exe @curlArguments
    if ($LASTEXITCODE -ne 0) {
        throw (
            "Marketing relay failed with curl exit code " +
            "$LASTEXITCODE. Body: $marketingText"
        )
    }
    $marketing = $marketingText | ConvertFrom-Json
    if ($marketing.request_id -ne $requestId) {
        throw "Marketing relay did not preserve the request ID."
    }
    if (-not $marketing.banner.mock) {
        throw "Marketing relay did not return a Mock banner."
    }

    $generatedMock = Join-Path $aiRoot (
        "outputs\banner_" + $marketing.banner.banner_id + ".png"
    )
    if (-not (Test-Path -LiteralPath $generatedMock)) {
        throw "Expected Mock output was not created: $generatedMock"
    }

    $jobSummary = ""
    if ($JobSmoke) {
        $identitySuffix = [guid]::NewGuid().ToString("N")
        $signupBody = @{
            username = "smk" + $identitySuffix.Substring(0, 12)
            password = "Q7!" + $identitySuffix.Substring(0, 20)
            displayName = "Smoke User"
            email = "smoke-" + $identitySuffix + "@example.com"
            organizationName = $null
            departmentName = $null
            jobTitle = $null
        } | ConvertTo-Json
        $signup = Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/auth/signup" `
            -ContentType "application/json" `
            -Body $signupBody `
            -TimeoutSec 15
        $userId = $signup.data.user.id
        if ($null -eq $userId) {
            throw "Smoke signup did not return a user ID."
        }

        $jobHeaders = @{
            "X-User-Id" = "$userId"
            "X-User-Role" = "USER"
            "X-Request-Id" = $requestId
            "Idempotency-Key" = "job-" + $identitySuffix
        }
        $projectBody = @{
            title = "AI task smoke " + $identitySuffix.Substring(0, 8)
            description = "Temporary SYSTEM_SMOKE_TEST project"
            industryCategory = "test"
        } | ConvertTo-Json
        $project = Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/projects" `
            -Headers $jobHeaders `
            -ContentType "application/json" `
            -Body $projectBody `
            -TimeoutSec 15
        $projectId = $project.data.id

        $accepted = Invoke-RestMethod `
            -Method Post `
            -Uri (
                "http://127.0.0.1:$SpringPort/api/v1/projects/" +
                "$projectId/ai-tasks/smoke"
            ) `
            -Headers $jobHeaders `
            -ContentType "application/json" `
            -Body "{}" `
            -TimeoutSec 30
        $jobId = $accepted.data.jobId
        if ($accepted.data.status -ne "QUEUED") {
            throw "AI task start did not return QUEUED."
        }

        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        $job = $null
        while ([DateTime]::UtcNow -lt $deadline) {
            $job = Invoke-RestMethod `
                -Uri (
                    "http://127.0.0.1:$SpringPort/api/v1/jobs/" +
                    "$jobId"
                ) `
                -Headers $jobHeaders `
                -TimeoutSec 10
            if (
                $job.data.status -eq "SUCCEEDED" -or
                $job.data.status -eq "FAILED"
            ) {
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if ($job.data.status -ne "SUCCEEDED") {
            throw (
                "SYSTEM_SMOKE_TEST job did not succeed. status=" +
                $job.data.status
            )
        }
        if (
            $job.data.resultReferenceType -ne "AI_TASK_RESULT" -or
            $null -eq $job.data.resultReferenceId -or
            [string]::IsNullOrWhiteSpace(
                $job.data.externalRequestId
            )
        ) {
            throw "SYSTEM_SMOKE_TEST result reference is incomplete."
        }
        $jobSummary = ", job=$jobId/SUCCEEDED"
    }

    $artifactSummary = ""
    if ($ArtifactSmoke) {
        $identitySuffix = [guid]::NewGuid().ToString("N")
        $signupBody = @{
            username = "art" + $identitySuffix.Substring(0, 12)
            password = "Q7!" + $identitySuffix.Substring(0, 20)
            displayName = "Artifact Smoke User"
            email = "artifact-" + $identitySuffix + "@example.com"
            organizationName = $null
            departmentName = $null
            jobTitle = $null
        } | ConvertTo-Json
        $signup = Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/auth/signup" `
            -ContentType "application/json" `
            -Body $signupBody `
            -TimeoutSec 15
        $userId = $signup.data.user.id
        $artifactHeaders = @{
            "X-User-Id" = "$userId"
            "X-User-Role" = "USER"
            "X-Request-Id" = $requestId
            "Idempotency-Key" = "artifact-" + $identitySuffix
        }
        $projectBody = @{
            title = "Artifact smoke " + $identitySuffix.Substring(0, 8)
            description = "Temporary SYSTEM_ARTIFACT_SMOKE_TEST project"
            industryCategory = "test"
        } | ConvertTo-Json
        $project = Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/projects" `
            -Headers $artifactHeaders `
            -ContentType "application/json" `
            -Body $projectBody `
            -TimeoutSec 15
        $projectId = $project.data.id
        $accepted = Invoke-RestMethod `
            -Method Post `
            -Uri (
                "http://127.0.0.1:$SpringPort/api/v1/projects/" +
                "$projectId/ai-tasks/artifact-smoke"
            ) `
            -Headers $artifactHeaders `
            -ContentType "application/json" `
            -Body "{}" `
            -TimeoutSec 30
        $artifactJobId = $accepted.data.jobId
        if ($accepted.data.status -ne "QUEUED") {
            throw "Artifact task start did not return QUEUED."
        }
        $deadline = [DateTime]::UtcNow.AddSeconds(45)
        $artifactJob = $null
        while ([DateTime]::UtcNow -lt $deadline) {
            $artifactJob = Invoke-RestMethod `
                -Uri (
                    "http://127.0.0.1:$SpringPort/api/v1/jobs/" +
                    "$artifactJobId"
                ) `
                -Headers $artifactHeaders `
                -TimeoutSec 10
            if (
                $artifactJob.data.status -eq "SUCCEEDED" -or
                $artifactJob.data.status -eq "FAILED"
            ) {
                break
            }
            Start-Sleep -Milliseconds 250
        }
        if ($artifactJob.data.status -ne "SUCCEEDED") {
            throw (
                "SYSTEM_ARTIFACT_SMOKE_TEST did not succeed. status=" +
                $artifactJob.data.status + ", error=" +
                $artifactJob.data.errorCode
            )
        }
        $downloadUrl = (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/ai-tasks/$artifactJobId/artifacts/result"
        )
        $download = Invoke-WebRequest -UseBasicParsing `
            -Uri $downloadUrl `
            -Headers $artifactHeaders `
            -TimeoutSec 15
        $artifactText = if ($download.Content -is [byte[]]) {
            [Text.Encoding]::UTF8.GetString($download.Content)
        } else {
            [string]$download.Content
        }
        $artifactJson = $artifactText | ConvertFrom-Json
        if (
            $artifactJson.status -ne "processed" -or
            $artifactJson.source.message -ne "artifact-smoke"
        ) {
            throw "Downloaded artifact content is invalid."
        }
        if (
            [string]::IsNullOrWhiteSpace(
                $download.Headers["X-Artifact-Id"]
            )
        ) {
            throw "Spring artifact download omitted metadata header."
        }
        $artifactSummary = (
            ", artifactJob=$artifactJobId/SUCCEEDED"
        )
    }

    $marketingJobSummary = ""
    if ($MarketingSmoke) {
        $identitySuffix = [guid]::NewGuid().ToString("N")
        $signupBody = @{
            username = "mkt" + $identitySuffix.Substring(0, 12)
            password = "Q7!" + $identitySuffix.Substring(0, 20)
            displayName = "Marketing Smoke User"
            email = "marketing-" + $identitySuffix + "@example.com"
            organizationName = $null
            departmentName = $null
            jobTitle = $null
        } | ConvertTo-Json
        $signup = Invoke-RestMethod -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/auth/signup" `
            -ContentType "application/json" -Body $signupBody -TimeoutSec 15
        $marketingHeaders = @{
            "X-User-Id" = "$($signup.data.user.id)"
            "X-User-Role" = "USER"
            "X-Request-Id" = $requestId
            "Idempotency-Key" = "marketing-" + $identitySuffix
        }
        $projectBody = @{
            title = "Marketing smoke " + $identitySuffix.Substring(0, 8)
            description = "Temporary MARKETING_GENERATION project"
            industryCategory = "test"
        } | ConvertTo-Json
        $project = Invoke-RestMethod -Method Post `
            -Uri "http://127.0.0.1:$SpringPort/api/v1/projects" `
            -Headers $marketingHeaders -ContentType "application/json" `
            -Body $projectBody -TimeoutSec 15
        $projectId = $project.data.id
        $contentBody = @{
            title = "Smoke Campaign"
            purpose = "PRODUCT_INTRODUCTION"
            channel = "SOCIAL"
            format = "SQUARE_1080"
            width = $null
            height = $null
            personaId = $null
            targetOffer = "Verified service"
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
        } | ConvertTo-Json
        $content = Invoke-RestMethod -Method Post `
            -Uri (
                "http://127.0.0.1:$SpringPort/api/v1/projects/" +
                "$projectId/marketing-contents"
            ) -Headers $marketingHeaders -ContentType "application/json" `
            -Body $contentBody -TimeoutSec 15
        $contentId = $content.data.content.id
        $sourceVersionId = $content.data.current.id
        $generateUrl = (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/marketing-contents/$contentId/generate" +
            "?sourceVersionId=$sourceVersionId"
        )
        $generateText = & curl.exe --silent --show-error --fail-with-body `
            -H "X-User-Id: $($signup.data.user.id)" `
            -H "X-User-Role: USER" `
            -H "X-Request-Id: $requestId" `
            -H "Idempotency-Key: marketing-$identitySuffix" `
            -F "image=@$sampleImage;type=image/png" $generateUrl
        if ($LASTEXITCODE -ne 0) {
            throw "Marketing generation request failed: $generateText"
        }
        $accepted = $generateText | ConvertFrom-Json
        $marketingJobId = $accepted.data.jobId
        $deadline = [DateTime]::UtcNow.AddSeconds(45)
        do {
            $marketingJob = Invoke-RestMethod -Uri (
                "http://127.0.0.1:$SpringPort/api/v1/jobs/$marketingJobId"
            ) -Headers $marketingHeaders -TimeoutSec 10
            if ($marketingJob.data.status -in @("SUCCEEDED", "FAILED")) {
                break
            }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($marketingJob.data.status -ne "SUCCEEDED") {
            throw (
                "MARKETING_GENERATION did not succeed. status=" +
                $marketingJob.data.status + ", error=" +
                $marketingJob.data.errorCode
            )
        }
        $versions = Invoke-RestMethod -Uri (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/marketing-contents/$contentId/versions"
        ) -Headers $marketingHeaders -TimeoutSec 15
        if (
            $versions.data.Count -ne 2 -or
            $versions.data[0].analysisJobId -ne $marketingJobId -or
            -not $versions.data[0].aiGenerated
        ) {
            throw "Generated marketing version linkage is invalid."
        }
        $download = Invoke-WebRequest -UseBasicParsing -Uri (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/ai-tasks/$marketingJobId/artifacts/result"
        ) -Headers $marketingHeaders -TimeoutSec 15
        if ($download.RawContentLength -ne (
            Get-Item -LiteralPath $sampleImage
        ).Length) {
            throw "Generated marketing artifact content is invalid."
        }
        $marketingHeaders["Idempotency-Key"] = (
            "marketing-rerun-" + $identitySuffix
        )
        $rerun = Invoke-RestMethod -Method Post -Uri (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/marketing-contents/$contentId/rerun"
        ) -Headers $marketingHeaders -ContentType "application/json" `
            -Body (@{ originalJobId = $marketingJobId } | ConvertTo-Json) `
            -TimeoutSec 30
        if (
            $rerun.data.jobId -eq $marketingJobId -or
            $rerun.data.rerunOfJobId -ne $marketingJobId
        ) {
            throw "Marketing rerun linkage is invalid."
        }
        $rerunJobId = $rerun.data.jobId
        $deadline = [DateTime]::UtcNow.AddSeconds(45)
        do {
            $rerunJob = Invoke-RestMethod -Uri (
                "http://127.0.0.1:$SpringPort/api/v1/jobs/$rerunJobId"
            ) -Headers $marketingHeaders -TimeoutSec 10
            if ($rerunJob.data.status -in @("SUCCEEDED", "FAILED")) {
                break
            }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($rerunJob.data.status -ne "SUCCEEDED") {
            throw "Marketing rerun did not succeed."
        }
        $rerunVersions = Invoke-RestMethod -Uri (
            "http://127.0.0.1:$SpringPort/api/v1/projects/" +
            "$projectId/marketing-contents/$contentId/versions"
        ) -Headers $marketingHeaders -TimeoutSec 15
        if (
            $rerunVersions.data.Count -ne 3 -or
            $rerunVersions.data[0].analysisJobId -ne $rerunJobId
        ) {
            throw "Marketing rerun version was not appended."
        }
        $marketingJobSummary = (
            ", marketingJob=$marketingJobId/SUCCEEDED" +
            ", rerunJob=$rerunJobId/SUCCEEDED"
        )
    }

    Write-Output (
        "AI local smoke passed: health=ready, marketing=completed, " +
        "requestId=$requestId" + $jobSummary + $artifactSummary +
        $marketingJobSummary
    )
} catch {
    Write-Output ("Smoke failed: " + $_.Exception.Message)
    if (Test-Path -LiteralPath $aiErr) {
        Write-Output "FastAPI stderr:"
        Get-Content -LiteralPath $aiErr -Tail 80
    }
    if (Test-Path -LiteralPath $aiOut) {
        Write-Output "FastAPI stdout:"
        Get-Content -LiteralPath $aiOut -Tail 80
    }
    if (Test-Path -LiteralPath $springOut) {
        Write-Output "Spring stdout:"
        Get-Content -LiteralPath $springOut -Tail 120
    }
    if (Test-Path -LiteralPath $springErr) {
        Write-Output "Spring stderr:"
        Get-Content -LiteralPath $springErr -Tail 80
    }
    exit 1
} finally {
    Stop-ProcessTree $springProcess
    Stop-ProcessTree $aiProcess
    if (
        $null -ne $generatedMock -and
        (Test-Path -LiteralPath $generatedMock)
    ) {
        Remove-Item -LiteralPath $generatedMock -Force
    }
    if (Test-Path -LiteralPath $runtimeRoot) {
        Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
    }
}
