<#
.SYNOPSIS
    TWIN_SURVEY 실스택 스모크. 백엔드-AI 왕복을 실제로 태운다.

.DESCRIPTION
    통합 테스트도 컴포넌트 테스트도 구조적으로 못 보는 이음새가 있다:
    등록 분기, 900초 클라이언트 선택, 계약 검증, 뱅크 마운트, 경계 데이터의 생존.
    여기서만 잡힌다.

    기본은 **무료**다 — 거절 경로와 뱅크 점검은 LLM 을 한 번도 부르지 않는다.
    그런데도 배선 전체(라우팅·워커·클라이언트·오류 사상)를 지나간다.
    -Paid 를 주면 실제 조사(n=50·2쌍 = 약 400셀)를 태운다. **돈이 든다.**

.PARAMETER Paid
    실제 조사를 실행한다. AI_API_KEY 지갑에서 비용이 나간다.

.EXAMPLE
    pwsh -File scripts/twin-survey-smoke.ps1
    pwsh -File scripts/twin-survey-smoke.ps1 -Paid
#>
[CmdletBinding()]
param(
    [switch]$Paid,
    [ValidateSet(50, 100, 300)][int]$SampleSize = 50,
    [string]$BaseUrl = "http://localhost:3000",
    [int]$BudgetSeconds = 780
)

$ErrorActionPreference = "Stop"
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step { param([string]$Text) Write-Output "`n== $Text" }
function Add-Failure { param([string]$Text) $failures.Add($Text); Write-Output "  FAIL $Text" }
function Write-Pass { param([string]$Text) Write-Output "  ok   $Text" }

function Invoke-Json {
    param([string]$Method, [string]$Uri, $Headers, $Body)
    # ⚠ **본문을 바이트로 만들어 보낸다.** PowerShell 5.1 의 Invoke-RestMethod 는 문자열
    #   본문을 ANSI 로 보내서 한글이 «????» 가 된다. 실측: 그 때문에 윤리·가치형 자극의
    #   속성 이름이 뭉개져 게이트를 그냥 통과했고, 막혔어야 할 조사가 실제로 돌았다
    #   (LLM 206회 = 돈). 스모크가 **경계를 검사하는 척만** 하게 되는 자리다.
    $arguments = @{ Method = $Method; Uri = $Uri; Headers = $Headers
                    ContentType = "application/json; charset=utf-8" }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        $arguments.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @arguments
}

# ── 0. 뱅크가 컨테이너에 붙어 있나 ────────────────────────────────────
Write-Step "bank mount"
$bankProbe = @'
import os
from app.twin.bank import load
print("TWIN_BANK_DIR=" + (os.getenv("TWIN_BANK_DIR") or "<unset>"))
cards, frame = load()
print("cards=%d frame=%d" % (len(cards), len(frame)))
'@
$bankOutput = $bankProbe | docker compose exec -T ai-server python -
Write-Output ($bankOutput | ForEach-Object { "  $_" })
if ($LASTEXITCODE -ne 0) {
    Add-Failure "뱅크가 붙어 있지 않다. compose 의 :ro 바인드와 TWIN_BANK_DIR 을 확인하라."
} else {
    Write-Pass "뱅크 로드"
}

# ── 1. 뱅크가 없으면 시끄럽게 죽나 ────────────────────────────────────
#     조용히 빈 표본으로 도는 것이 이 기능에서 가장 위험한 실패다.
Write-Step "bank unavailable"
$unavailableProbe = @'
import asyncio, os
os.environ.pop("TWIN_BANK_DIR", None)
from app.twin import execute_twin_survey
from app.providers import ProviderFailure
payload = {"situation": "가게에서 하나를 고릅니다.", "sampleSize": 50, "pairs": [{
    "pairId": "P1",
    "X": {"label": "A", "attrs": {"형태": "신선"}, "priceKrw": 4500},
    "Y": {"label": "B", "attrs": {"형태": "냉동"}, "priceKrw": 4500}}]}
try:
    asyncio.run(execute_twin_survey(payload, 60))
    print("NO_FAILURE")
except ProviderFailure as failure:
    print("reason=" + failure.reason)
'@
$unavailableOutput = ($unavailableProbe | docker compose exec -T ai-server python -) -join "`n"
Write-Output "  $unavailableOutput"
if ($unavailableOutput -match "TWIN_BANK_UNAVAILABLE") { Write-Pass "뱅크 미마운트 = 시끄러운 실패" }
else { Add-Failure "뱅크가 없는데 TWIN_BANK_UNAVAILABLE 이 아니다" }

# ── 2. 계정·프로젝트 ──────────────────────────────────────────────────
Write-Step "account"
$suffix = [guid]::NewGuid().ToString("N")
$username = "twin" + $suffix.Substring(0, 12)
$password = "Q7!" + $suffix.Substring(0, 20)
Invoke-Json POST "$BaseUrl/api/v1/auth/signup" @{} @{
    username = $username
    password = $password
    displayName = "Twin Smoke"
    email = "twin-$suffix@example.com"
    organizationName = $null; departmentName = $null; jobTitle = $null
} | Out-Null
# 가입은 토큰을 주지 않는다 — 로그인이 별도다. X-User-Id 헤더로는 통과하지 못한다.
$login = Invoke-Json POST "$BaseUrl/api/v1/auth/login" @{} @{ username = $username; password = $password }
$headers = @{ "Authorization" = "Bearer $($login.data.tokens.accessToken)" }
$project = Invoke-Json POST "$BaseUrl/api/v1/projects" $headers @{
    title = "Twin smoke " + $suffix.Substring(0, 8)
    description = "disposable twin survey smoke"
    industryCategory = "test"
}
$projectId = $project.data.id
Write-Pass "project $projectId"

function Wait-Terminal {
    param([int]$TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $current = Invoke-Json GET "$BaseUrl/api/v2/projects/$projectId/twin-survey/current" $headers $null
        $state = $current.data.run.state
        if ($state -eq "SUCCEEDED" -or $state -eq "FAILED") { return $current.data }
        Start-Sleep -Seconds 3
    }
    return $null
}

# ── 3. 윤리·가치형은 LLM 0회로 거절되나 ──────────────────────────────
#     영구 금지 유형이다. 실행 뒤에 거절하면 사용자는 기다린 뒤 빈손이 되고,
#     무엇보다 성적이 없는 유형에 돈이 나간다.
Write-Step "ethical stimulus refused"
$ethical = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey" $headers @{
    situation = "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
    sampleSize = 50
    pairs = @(@{
        pairId = "E1"
        X = @{ label = "인증 제품"; attrs = @{ "인증" = "친환경 인증" }; priceKrw = 4500 }
        Y = @{ label = "일반 제품"; attrs = @{ "인증" = "없음" }; priceKrw = 4500 }
    })
}
Write-Output "  enqueued taskRunId=$($ethical.data.taskRunId)"
$ethicalResult = Wait-Terminal 120
if ($null -eq $ethicalResult) {
    Add-Failure "윤리형 실행이 2분 안에 끝나지 않았다 — 거절은 즉시여야 한다(LLM 0회)"
} elseif ($ethicalResult.run.state -ne "FAILED") {
    Add-Failure "윤리형이 거절되지 않았다: $($ethicalResult.run.state) — 영구 금지 유형이다"
} elseif ($ethicalResult.run.errorCode -ne "TWIN_TASK_TYPE_NOT_SERVICEABLE") {
    # 「거절됐다」로는 부족하다. 이유가 살아 와야 화면이 왜 거절인지 말할 수 있다.
    # 실측: 이유가 화이트리스트에 없으면 AI_RESULT_INVALID 로 뭉개진다.
    Add-Failure "거절 이유가 뭉개졌다: $($ethicalResult.run.errorCode)"
} else {
    Write-Pass "윤리형 거절 errorCode=$($ethicalResult.run.errorCode)"
}

# ── 4. 실제 조사 (유료) ───────────────────────────────────────────────
if (-not $Paid) {
    Write-Output "`n(유료 구간 건너뜀 — 실제 조사를 태우려면 -Paid)"
} else {
    Write-Step "paid survey n=$SampleSize pairs=2"
    $started = Get-Date
    $run = Invoke-Json POST "$BaseUrl/api/v2/projects/$projectId/twin-survey" $headers @{
        situation = "가게에서 하나를 고릅니다. 아래 두 상품이 있습니다."
        sampleSize = $SampleSize
        pairs = @(
            @{ pairId = "P1"
               X = @{ label = "신선 냉장"; attrs = @{ "형태" = "신선(냉장)" }; priceKrw = 4500 }
               Y = @{ label = "냉동"; attrs = @{ "형태" = "냉동" }; priceKrw = 4500 } },
            @{ pairId = "P2"
               X = @{ label = "신선 냉장(비쌈)"; attrs = @{ "형태" = "신선(냉장)" }; priceKrw = 6600 }
               Y = @{ label = "냉동"; attrs = @{ "형태" = "냉동" }; priceKrw = 4500 } }
        )
    }
    Write-Output "  enqueued taskRunId=$($run.data.taskRunId)"
    $done = Wait-Terminal $BudgetSeconds
    $spent = [int]((Get-Date) - $started).TotalSeconds

    if ($null -eq $done) {
        Add-Failure "예산 ${BudgetSeconds}초 안에 끝나지 않았다"
    } elseif ($done.run.state -ne "SUCCEEDED") {
        Add-Failure "실패했다: state=$($done.run.state) errorCode=$($done.run.errorCode)"
    } else {
        Write-Pass "COMPLETED in ${spent}s"
        $version = $done.version
        $result = $version.result

        # 응답 크기 — 셀 원장을 실으면 여기서 터진다.
        $bytes = ([System.Text.Encoding]::UTF8.GetBytes(($result | ConvertTo-Json -Depth 20 -Compress))).Length
        if ($bytes -ge 2MB) { Add-Failure "응답이 2 MiB 이상이다: $bytes" }
        else { Write-Pass "응답 $([math]::Round($bytes / 1KB, 1)) KiB" }

        # 경계는 **쌍마다** 있어야 한다. 계약이 이미 막지만 여기서 눈으로 본다.
        foreach ($pair in $result.pairs) {
            if (-not $pair.caveats -or $pair.caveats.Count -eq 0) {
                Add-Failure "$($pair.pairId): caveats 가 비었다"
            } else {
                Write-Pass "$($pair.pairId) $($pair.taskType) winner=$($pair.winner) measurable=$($pair.measurable) caveats=$($pair.caveats.Count)"
            }
        }
        if ($version.caveatCount -le 0) { Add-Failure "물질화된 caveatCount 가 0이다" }
        Write-Output "  sampling: requested=$($result.sampling.requested) drawn=$($result.sampling.drawn)"
        Write-Output "  telemetry: cells=$($result.telemetry.cells) formatViolations=$($result.telemetry.formatViolations) failures=$($result.telemetry.failures)"
    }
}

# ── 결과 ──────────────────────────────────────────────────────────────
Write-Output ""
if ($failures.Count -gt 0) {
    Write-Output "TWIN SURVEY SMOKE FAILED ($($failures.Count))"
    $failures | ForEach-Object { Write-Output "- $_" }
    exit 1
}
Write-Output "TWIN SURVEY SMOKE PASSED"
