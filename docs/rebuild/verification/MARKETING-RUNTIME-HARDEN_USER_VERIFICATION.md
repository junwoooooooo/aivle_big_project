# MARKETING-RUNTIME-HARDEN USER VERIFICATION

Repository root: `C:\Users\seewo\Desktop\big_proj_01\new_3`

## 1. Provider smoke

AI Provider 환경변수를 설정한 터미널에서 실행한다.

```powershell
Set-Location C:\Users\seewo\Desktop\big_proj_01\new_3\ai
.\.venv\Scripts\python.exe -m app.tools.marketing_content_provider_smoke
```

성공 기준:

- exit code 0
- `ok`, contract, contentType, boolean/개수 metadata만 출력
- prompt, authorization, secret, provider raw body, 생성 본문 전체는 출력하지 않음
- `PROVIDER_RESPONSE_SCHEMA_REJECTED`가 발생하지 않음

## 2. Docker build 및 runtime

```powershell
Set-Location C:\Users\seewo\Desktop\big_proj_01\new_3
docker compose build ai backend frontend
docker compose up -d ai backend frontend
docker compose ps ai backend frontend
```

## 3. Marketing 생성 API

```powershell
$projectId = '<OWNED_PROJECT_ID>'
$planningSnapshotId = '<FINALIZED_PLANNING_SNAPSHOT_ID>'
$token = '<OWNER_BEARER_TOKEN>'
$idempotencyKey = [guid]::NewGuid().ToString()
$headers = @{
  Authorization = "Bearer $token"
  'Idempotency-Key' = $idempotencyKey
  'X-Correlation-Id' = $idempotencyKey
}
$body = @{
  contract = 'marketing-content-request-v1'
  planningSnapshotId = $planningSnapshotId
  contentType = 'SOCIAL_POST'
  channel = 'Instagram'
  purpose = '서비스 소개'
  tone = '명확하고 친근하게'
  length = 'SHORT'
  requiredPhrases = @()
  excludedPhrases = @()
  additionalInstruction = $null
} | ConvertTo-Json

$created = Invoke-RestMethod -Method Post -Headers $headers -ContentType 'application/json' -Body $body -Uri "http://localhost/api/v3/projects/$projectId/marketing-contents"
$created.data.content | Format-List contentId,status,activeJobId,sourceSnapshotId,updatedAt
```

QUEUED 응답에 contentId, activeJobId, sourceSnapshotId, updatedAt이 존재해야 한다.

## 4. TaskRun, Event, Detail 확인

```powershell
$contentId = $created.data.content.contentId
$jobId = $created.data.content.activeJobId

Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } -Uri "http://localhost/api/v3/projects/$projectId/marketing-contents/$contentId"
Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } -Uri "http://localhost/api/v2/jobs/$jobId/events?after=0"
docker compose logs --since=10m backend | Select-String -Pattern 'job.marketing|MARKETING_CONTENT_GENERATION'
docker compose logs --since=10m ai | Select-String -Pattern 'MARKETING_CONTENT_GENERATION'
```

Event sequence에 queued, started, source_prepared, copy_generating, legal_checking, completed 또는 failed가 실제 순서대로 존재해야 한다. Terminal 후 Detail의 Content status와 TaskRun status가 일치해야 한다.

## 5. 브라우저 새로고침 복원

```text
http://localhost/app/projects/<OWNED_PROJECT_ID>/marketing
```

1. 생성 직후 QUEUED/RUNNING 동안 페이지를 새로고침한다.
2. Detail이 자동 복원되고 같은 activeJobId의 Event replay가 연결되는지 확인한다.
3. 정적 단계가 회전하지 않고 실제 수신 Event만 Timeline에 나타나는지 확인한다.
4. Terminal Event 후 Detail이 재조회되고 결과 Canvas가 표시되는지 확인한다.
5. SSE를 차단했을 때 공통 polling fallback으로 최종 Detail이 복원되는지 확인한다.

## 6. Failure 및 Source 경계

- 오래된 planningSnapshotId로 생성 요청: `MODULE_INPUT_STALE`
- 금지 표현을 사용자 편집 결과에 넣고 저장/최종화: `MARKETING_PROHIBITED_CLAIM`
- Provider schema invalid/timeout/rate limit: FAILED terminal Event와 안전한 사용자 문구
- BM·재무/Persona 결과가 없어도 FinalizedPlanningSnapshot만 있으면 생성 가능
- 응답과 로그에 prompt, provider body, authorization, raw user input, stack trace가 노출되지 않음

## 7. Asset 범위

- Copy, HTML/CSS Preview, Image Brief 텍스트가 표시되는지 확인한다.
- 다운로드는 `.txt` 콘텐츠임을 확인한다.
- PNG/JPEG/Banner binary 생성이나 이미지 artifact 다운로드를 기대하지 않는다.
- `artifactRefs`는 빈 배열이어야 한다.

모든 항목이 통과한 뒤에만 이 Unit의 runtime acceptance를 완료로 기록한다.
