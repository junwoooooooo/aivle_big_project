# ASYNC-RUNTIME-RESTORE User Verification

아래 명령은 저장소 루트 `C:\Users\seewo\Desktop\big_proj_01\new_3`의 PowerShell에서 실행한다. `.env`의 필수 Compose 값과 실제 AI provider 설정이 준비되어 있어야 한다.

## 1. Build and restart

```powershell
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps backend frontend ai-server postgres
```

예상 소요는 캐시 상태에 따라 2~10분이다. backend/frontend가 `healthy`, ai-server/postgres가 실행 중이어야 한다.

## 2. Existing queued TaskRun claim

```powershell
$taskRunId = '2617c8d9-6148-4347-bbb3-5db458c6fe25'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -x -c "SELECT id, project_id, task_type, subject_id, state, attempt_count, current_attempt_id, last_error_code, started_at, finished_at, updated_at FROM task_runs WHERE id = ''2617c8d9-6148-4347-bbb3-5db458c6fe25'';"'
Start-Sleep -Seconds 5
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -x -c "SELECT id, project_id, task_type, subject_id, state, attempt_count, current_attempt_id, last_error_code, started_at, finished_at, updated_at FROM task_runs WHERE id = ''2617c8d9-6148-4347-bbb3-5db458c6fe25'';"'
```

성공 기준: row가 존재하고 재시작 후 `attempt_count`가 0보다 커지며 상태가 `RUNNING` 또는 terminal(`SUCCEEDED`, `FAILED`, `TIMED_OUT`)로 이동한다. DB에서 상태를 직접 수정하지 않는다.

## 3. AI Server execution request

```powershell
docker compose logs --since=10m backend | Select-String '2617c8d9-6148-4347-bbb3-5db458c6fe25|Idea Brief worker'
docker compose logs --since=10m ai-server | Select-String 'POST /internal/v1/ai/executions|/internal/v1/ai/executions'
```

성공 기준: 기존 TaskRun 처리 시점에 AI Server의 `POST /internal/v1/ai/executions` 요청이 확인된다. 로그에 token, Authorization, prompt 또는 provider raw body를 복사하지 않는다.

## 4. Job Event sequence

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT sequence, event_type, status, message_key, technical_code, occurred_at FROM job_events WHERE job_id = ''2617c8d9-6148-4347-bbb3-5db458c6fe25'' AND deleted_at IS NULL ORDER BY sequence;"'
```

성공 기준: 기존 sequence 1 `QUEUED` 뒤에 `CLAIMED`/진행/terminal event가 중복 sequence 없이 증가한다.

## 5. Idea Brief leaves DERIVING

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -x -c "SELECT id, project_id, status, active_task_run_id, updated_at FROM idea_briefs WHERE active_task_run_id = ''2617c8d9-6148-4347-bbb3-5db458c6fe25'' AND deleted_at IS NULL;"'
```

성공 기준: provider 결과에 따라 `NEEDS_INPUT`, `READY_FOR_REVIEW`, 또는 `FAILED`로 이동하고 `DERIVING`에 고착되지 않는다.

## 6. Polling API and 90-second SSE

TaskRun 조회 결과의 `project_id`와 인증 토큰을 설정한다.

```powershell
$projectId = '<PROJECT_ID>'
$accessToken = '<ACCESS_TOKEN>'
$headers = @{ Authorization = "Bearer $accessToken" }
Invoke-RestMethod -Headers $headers -Uri "http://localhost:3000/api/v2/projects/$projectId/task-runs/$taskRunId"
Invoke-RestMethod -Headers $headers -Uri "http://localhost:3000/api/v2/jobs/$taskRunId/events?after=0"
curl.exe -N --max-time 100 -H "Accept: text/event-stream" -H "Authorization: Bearer $accessToken" -H "Last-Event-ID: 0" "http://localhost:3000/api/v2/jobs/$taskRunId/events"
```

성공 기준:

- Polling JSON은 sequence 1부터 정렬해 반환한다.
- SSE 응답은 `:connected` 또는 replay event를 즉시 출력한다.
- terminal이 아닌 stream은 heartbeat comment를 받으며 90초 이상 `ERR_INCOMPLETE_CHUNKED_ENCODING` 없이 유지된다.
- terminal event가 이미 존재하면 replay 후 정상 종료되는 것이 정상이다.

## 7. Browser polling fallback

1. 브라우저 DevTools Network에서 `/api/v2/jobs/<jobId>/events` SSE 요청만 차단하거나 Offline 대신 해당 URL에 대한 request blocking을 켠다.
2. 실행 중인 Idea Brief 또는 Concept Factory 페이지를 연다.
3. 최대 약 45초의 inactivity watchdog과 제한된 SSE 재시도 뒤 `events?after=<sequence>` JSON 요청이 나타나는지 확인한다.
4. polling 간격이 2초 고정이 아니라 점차 늘어나고 최대 약 30초로 제한되는지 확인한다.
5. 탭을 hidden 상태로 두면 간격이 더 길어지고, 새 event 수신 후 간격이 초기화되는지 확인한다.
6. terminal event 후 해당 도메인 Query API가 다시 호출되어 실제 상태가 화면에 반영되는지 확인한다.

성공 기준: 프런트가 성공/진행 상태를 임의 생성하지 않고 Polling API의 실제 event와 terminal 후 Domain Query 결과만 표시한다. 401/403에서는 polling으로 우회하지 않고 즉시 오류가 표시되어야 한다.

## Failure evidence

실패하면 아래만 수집한다. secret, Authorization, prompt, raw provider body 및 사용자 원문은 제거한다.

```powershell
docker compose ps
docker compose logs --since=10m backend
docker compose logs --since=10m ai-server
docker compose logs --since=10m frontend
```

다음 실행 단위 진행 조건은 기존 TaskRun의 claim/attempt 증가, AI execution 호출, terminal 전이, event sequence, 90초 SSE 또는 정상 terminal completion, SSE 차단 시 polling fallback이 모두 확인되는 것이다.
