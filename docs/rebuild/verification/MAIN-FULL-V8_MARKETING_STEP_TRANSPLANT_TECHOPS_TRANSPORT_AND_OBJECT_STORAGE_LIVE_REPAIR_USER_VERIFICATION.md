# MAIN-FULL-V8 사용자 LIVE 검증

실제 secret, provider raw body, prompt 전체를 출력하지 않는다. 아래 검증은 저장 여부와 browser 표시 여부를 별도로 기록한다.

## 1. 사전 확인과 필요한 서비스 갱신

```powershell
python scripts/check_local_env.py --compose
docker compose config | Select-String -Pattern 'AI_SERVER_READ_TIMEOUT|AI_SERVER_LONG_READ_TIMEOUT|OBJECT_STORAGE_ENDPOINT|OBJECT_STORAGE_PUBLIC_ENDPOINT|9000:9000' -Context 0,1
docker compose build backend frontend
docker compose up -d minio minio-init postgres ai-server backend frontend
docker compose ps
```

기대:

- generic timeout: `30s`
- long timeout: `7m`
- internal object endpoint: `http://minio:9000`
- public endpoint: `http://localhost:9000` 또는 배포 환경의 browser-reachable domain
- public endpoint가 `http://minio:9000`이면 중단하고 실제 `.env`의 `OBJECT_STORAGE_PUBLIC_ENDPOINT` 이름/값을 local browser endpoint로 수정한다. 값을 보고서에 복사하지 않는다.

## 2. 이전 TechOps 실패 reason 확인

먼저 Work Center 또는 Network response에서 최근 `<TASK_RUN_ID>`를 복사한다. 기본 DB 이름/사용자를 바꿨다면 아래 `aivle`만 실제 이름으로 바꾼다.

```powershell
docker compose exec -T postgres psql -U aivle -d aivle -P pager=off -c "SELECT id,state,last_error_code,retryable,current_attempt_id,started_at,finished_at FROM task_runs WHERE id='<TASK_RUN_ID>';"
docker compose exec -T postgres psql -U aivle -d aivle -P pager=off -c "SELECT id,state,normalized_error_code,normalized_error_reason,retryable,started_at,finished_at FROM task_attempts WHERE task_run_id='<TASK_RUN_ID>' ORDER BY attempt_number DESC;"
docker compose exec -T postgres psql -U aivle -d aivle -P pager=off -c "SELECT stage,status,technical_code,occurred_at FROM job_events WHERE task_run_id='<TASK_RUN_ID>' ORDER BY sequence;"
```

기록할 값:

- taskRunId / attemptId
- `last_error_code`
- `normalized_error_code`
- `normalized_error_reason`
- retryable
- JobEvent `technical_code`

허용 taxonomy:

- `REQUEST_DEADLINE_EXCEEDED`
- `MODEL_DEPENDENCY_UNAVAILABLE`
- `DEPENDENCY_RATE_LIMITED`
- `PROVIDER_RESPONSE_SCHEMA_REJECTED`
- `AI_RESULT_INVALID`
- `UNEXPECTED_INTERNAL_ERROR`

## 3. TechOps Advisory 1회

```powershell
docker compose logs --since 2m -f backend ai-server | Select-String -Pattern 'TECH_OPS_ADVISORY|taskRunId|taskAttemptId|REQUEST_DEADLINE_EXCEEDED|MODEL_DEPENDENCY_UNAVAILABLE|RATE_LIMITED|AI_RESULT_INVALID|UNEXPECTED_INTERNAL_ERROR'
```

UI에서 TechOps Advisory를 한 번 실행한다.

PASS:

- 약 30초 경계에서 종료되지 않는다.
- terminal까지 progress가 유지된다.
- 성공하면 report가 표시된다.
- 실패하면 위 taxonomy 중 실제 safe reason이 TaskAttempt/JobEvent에 남는다.

## 4. Marketing 생성 1회

Marketing 화면에서 순서대로 수행한다.

1. Step 1에서 Concept/Marketing Source 확인
2. `이 컨셉으로 콘텐츠 만들기` 선택
3. Step 2 설정 후 콘텐츠 생성
4. 페이지를 이동하거나 새로고침하지 않고 기다림

PASS:

```text
QUEUED → RUNNING → COMPLETED → Step 3
```

- SSE terminal을 놓쳐도 1~2초 REST polling으로 완료된다.
- content list card도 같은 화면에서 `COMPLETED`로 바뀐다.
- 실패 시 polling이 중단되고 실패 상태가 보인다.

DB 보조 확인:

```powershell
docker compose exec -T postgres psql -U aivle -d aivle -P pager=off -c "SELECT id,status,task_run_id,current_revision_number,updated_at FROM pipeline_marketing_contents ORDER BY created_at DESC LIMIT 3;"
```

## 5. MinIO object 존재 확인

최신 artifact key를 확인한다.

```powershell
docker compose exec -T postgres psql -U aivle -d aivle -P pager=off -c "SELECT artifact_ref,created_at FROM pipeline_marketing_assets ORDER BY created_at DESC LIMIT 3;"
```

출력된 key 하나를 `<OBJECT_KEY>`에 넣는다. 기본 bucket을 바꿨다면 bucket 이름만 바꾼다.

```powershell
docker compose exec -T minio sh -lc 'mc alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc stat "local/aivle-ai-artifacts/<OBJECT_KEY>"'
```

성공 시 별도로 기록:

```text
OBJECT_STORED=YES
```

## 6. Browser artifact URL과 이미지 렌더

Browser DevTools Network에서 Marketing detail response의 마지막 `artifactRefs` URL을 확인한다.

금지:

```text
http://minio:9000/...
http://backend/...
http://ai-server/...
```

local 허용:

```text
http://localhost:9000/...
```

검증:

- URL query에 `X-Amz-Algorithm`, `X-Amz-Signature`가 있음
- Network status 200
- Content-Type `image/jpeg`, `image/png`, `image/webp` 중 하나
- `<img>`가 실제 렌더됨

성공 시:

```text
BROWSER_URL_REACHABLE=YES
IMAGE_RENDERED=YES
```

DNS 오류면 endpoint 문제이고, DNS가 해결된 뒤 CORS 오류가 발생할 때만 CORS를 별도 조사한다.

## 7. Step UI·mobile·action footer

Desktop과 DevTools mobile viewport에서 확인한다.

- Step 1/2/3 순서와 keyboard Tab/Enter 동작
- Step 3에서 Preview, Style, Legal, Copy Editor, Revision 표시
- 저장된 콘텐츠 클릭 시 Step 3 이동
- action footer가 편집 영역 아래 normal flow에 있음
- scroll 중 action footer가 viewport 위에 떠서 콘텐츠를 가리지 않음

DevTools computed style 기대:

```text
.mk-actions position = static
```

## 8. 최종 기록

| Gate | 결과 |
|---|---|
| TechOps 30초 경계 통과 | PASS/FAIL |
| TechOps terminal/result | PASS/FAIL |
| Marketing navigation 없는 완료 | PASS/FAIL |
| list/detail 동시 완료 | PASS/FAIL |
| OBJECT_STORED | YES/NO |
| BROWSER_URL_REACHABLE | YES/NO |
| IMAGE_RENDERED | YES/NO |
| action footer non-floating | PASS/FAIL |
| mobile/keyboard | PASS/FAIL |

모든 live 항목이 PASS가 되기 전에는 `COMPLETE`로 판정하지 않는다.
