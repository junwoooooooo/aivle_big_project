# MODULE-STATUS-JOB-CENTER USER VERIFICATION

아래 검증은 repository root `C:\Users\seewo\Desktop\big_proj_01\new_3`에서 수행한다. 기존 로그인 사용자의 bearer token과 그 사용자가 소유한 project id를 사용한다.

## 1. 빌드와 실행

```powershell
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps backend frontend
```

## 2. Query API 소유권과 복원

```powershell
$projectId = '<OWNED_PROJECT_ID>'
$token = '<OWNER_BEARER_TOKEN>'
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod -Headers $headers -Uri "http://localhost/api/v3/projects/$projectId/modules"
Invoke-RestMethod -Headers $headers -Uri "http://localhost/api/v3/projects/$projectId/active-jobs"
Invoke-RestMethod -Headers $headers -Uri "http://localhost/api/v3/projects/$projectId/recent-jobs"
```

확인 항목:

- IDEA가 project description이 아니라 current Idea Brief status와 activeTaskRunId/confirmedSnapshotId를 반영한다.
- Concept Factory가 current run, eligibleCount, source snapshot, activeJobId를 반영한다.
- Marketing이 finalized planning snapshot과 최신 content/task 상태를 반영한다.
- 각 Job에 task/subject/status/module/terminal/retryable/targetRoute 및 안전한 message key가 있다.
- 다른 사용자의 token으로 같은 project id를 조회하면 404 또는 프로젝트 접근 거부 응답이며 목록이 노출되지 않는다.

## 3. Project Shell Job Center

브라우저에서 다음 주소를 연다.

```text
http://localhost/app/projects/<OWNED_PROJECT_ID>/overview
```

확인 항목:

1. 새로고침 직후 LocalStorage 등록 없이 서버의 진행/대기/입력 필요/최근 완료/최근 실패 작업이 복원된다.
2. 작업을 선택하면 Timeline은 선택한 Job 하나에 대해서만 연결된다.
3. `모듈로 이동`으로 IDEA, Concept Factory, Marketing 페이지에 진입할 수 있다.
4. NOT_READY/NOT_CONNECTED badge인 페이지도 직접 URL 및 sidebar로 진입 가능하다.
5. `수동 새로고침`이 목록을 다시 조회한다.
6. SSE를 차단하면 기존 `useJobEvents` polling fallback으로 전환되고 `연결 재시도`가 동작한다.
7. Job이 COMPLETED, FAILED 또는 NEEDS_INPUT으로 끝나면 Shell 알림이 보이고 Active/Recent 목록과 Module badge가 갱신된다.
8. 다른 프로젝트 페이지로 이동해도 ProjectLayout의 Job Center 상태를 계속 확인할 수 있다.

## 4. 서버 로그 확인

```powershell
docker compose logs --since=10m backend | Select-String -Pattern 'active-jobs|recent-jobs|/api/v2/jobs/'
```

응답 및 로그에 authorization, raw user input, provider body, prompt, secret 또는 stack trace가 사용자 payload로 노출되지 않는지 확인한다.

## 판정

- 위 항목이 모두 통과하면 MODULE-STATUS-JOB-CENTER runtime acceptance를 통과한 것으로 기록한다.
- 실패하면 project id, endpoint, HTTP status, 안전한 error code, taskRunId/jobId, 마지막 event sequence만 기록한다. token이나 원문 입력은 기록하지 않는다.
