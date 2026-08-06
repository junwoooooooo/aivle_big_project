# Conversational Intake Runtime Hotfix Docker 검증 절차

이 문서는 사용자가 Docker/PostgreSQL/OpenAI 환경에서 Runtime Hotfix를 검증하기 위한 절차다. Codex는 브라우저 수동 검증이나 실제 OpenAI 호출 완료를 주장하지 않는다.

## Provider DTO strict-schema 최종 Smoke와 1 Turn

Docker rebuild 후 브라우저로 진행하기 전에 반드시 Provider smoke를 먼저 실행한다.

```powershell
docker compose build --no-cache ai-server backend
docker compose up -d ai-server backend
docker compose exec ai-server python -m app.tools.idea_conversation_provider_smoke
```

다음 여섯 줄이 모두 있어야 다음 단계로 진행한다.

```text
provider=openai
model=<configured model>
responseFormat=json_schema
providerStatus=2xx
providerSchemaValidation=PASSED
domainMappingValidation=PASSED
```

실패 출력은 `upstreamStatus`, `safeErrorType`, `safeErrorParam`, `schemaName` 네 항목만 제공한다. `PROVIDER_RESPONSE_SCHEMA_REJECTED`이면 AI container가 최신 코드인지 확인하고, Prompt/Provider body/API key를 수집하지 않는다.

Smoke 통과 후 새 Project에서 다음 synthetic 성격의 입력을 한 번만 전송한다.

> 도서관 좌석 이용 불편을 줄이는 예약 서비스를 검토하고 있습니다. 주요 사용자는 지역 주민이며 우선 지역은 서울입니다.

성공 조건:

- AI access log의 `POST /internal/v1/ai/executions`가 2xx
- USER Message와 ASSISTANT `QUESTION_SET|BRIEF_REVIEW` Message가 각각 존재
- Opportunity Brief가 null이 아님
- Conversation `domainState=NEEDS_INPUT|READY_FOR_CONFIRMATION`
- `activeJobId=null`
- TaskRun `SUCCEEDED`
- terminal `job.completed`
- 실제 schema Repair가 발생한 경우에만 `RESULT_SCHEMA_REPAIRED` warning과 `job.idea.result.repairing` 존재

Smoke가 PASSED가 아니면 브라우저 검증이나 G7로 진행하지 않는다.

## R3 Provider Result Schema 및 단일 Repair 확인

R3가 포함된 AI/Backend 이미지를 다시 빌드한다.

```powershell
docker compose build --no-cache ai-server backend
docker compose up -d ai-server backend
docker compose logs --since=2m ai-server backend
```

새 Project의 Conversation에서 다음처럼 일부 정보가 빠진 입력을 전송한다.

> 서울 아파트 주민이 재활용품 배출을 예약하고 포인트를 받는 서비스를 만들고 싶습니다. 플랫폼은 직접 수거하지 않고 허가된 파트너가 운반합니다.

AI access log에서 `POST /internal/v1/ai/executions`가 최종 `200`인지 확인한다. Provider initial 결과가 이미 유효하면 repair event가 없어도 정상이다. Initial 결과가 구조적으로 잘못되어 Repair가 성공한 경우 `job.idea.result.repairing`이 한 번만 존재하고, 이후 `job.idea.brief.draft.started`, terminal `job.completed`가 이어져야 한다.

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "select job_id, sequence, event_type, message_key, message_params_json, technical_code from job_events where job_id='<JOB_ID>' order by sequence;"
docker compose exec postgres psql -U aivle -d aivle -c "select state, attempt_count, retryable, last_error_code from task_runs where id='<JOB_ID>';"
docker compose exec postgres psql -U aivle -d aivle -c "select role, message_type, task_run_id from idea_messages where conversation_id=<CONVERSATION_ID> order by sequence_number;"
docker compose exec postgres psql -U aivle -d aivle -c "select id, version_number, state, task_run_id from opportunity_brief_versions where conversation_id=<CONVERSATION_ID> order by version_number;"
```

Repair event params는 `attemptPhase=REPAIR`와 `issueCount`만 포함해야 한다. 성공 기준은 Assistant `QUESTION_SET` 또는 `BRIEF_REVIEW` 한 개, 새 Brief Draft 한 개, provenance 보존, Conversation `NEEDS_INPUT` 또는 `READY_FOR_CONFIRMATION`, TaskRun `SUCCEEDED`, `activeJobId=null`이다. AI 제안은 `USER_CONFIRMED` 또는 `LOCKED`가 아니어야 한다.

Initial과 Repair가 모두 schema-invalid이면 AI endpoint는 `502`, Backend TaskRun은 `RESULT_SCHEMA_INVALID`, `attempt_count=1`, `retryable=false`, `FAILED`여야 한다. 동일 Turn에서 세 번째 Provider 호출이나 durable retry가 발생하면 실패다. 로그에서는 `taskType`, `phase=initial|repair`, validation path/type만 수집한다. request/response body, Prompt, 사용자 원문, Authorization/API Key는 수집하지 않는다.

운영 OpenAI-compatible endpoint가 `response_format.type=json_schema`를 지원하지 않아 4xx가 발생하면 HTTP status, taskType, safe error code, 발생 시각만 수집한다.

## 1. 준비와 재빌드

`.env`에 최소 다음 값을 설정한다. 비밀값은 저장소에 commit하거나 명령 출력에 복사하지 않는다.

- `POSTGRES_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_SERVICE_TOKEN`
- `AI_PROVIDER=openai`, `AI_API_KEY`, `AI_MODEL`
- `AI_FIXTURE_MODE=false`
- `VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true`

```powershell
docker compose config
docker compose build --no-cache ai-server backend frontend
docker compose up -d --build postgres minio minio-init ai-server backend frontend
docker compose ps
docker compose logs --tail=100 backend ai-server frontend
```

## 2. 새 Project와 Conversation

1. `http://localhost:3000`에 로그인하고 새 Project를 만든다.
2. Idea 단계에서 대화형 Workspace가 표시되는지 확인한다.
3. 다음 예시를 사용자 메시지로 한 번 전송한다.

> 서울 아파트 주민이 재활용품 배출을 예약하고 포인트를 받는 서비스를 만들고 싶습니다. 플랫폼은 직접 수거하지 않고 허가된 파트너가 운반합니다.

브라우저 Network에서 메시지 요청이 `jobId`를 반환하는지 확인하되 Authorization 값을 기록하거나 공유하지 않는다.

## 3. 성공 기준

다음 durable event가 실제 sequence 순서로 나타나야 한다. 내부 `job.claimed`/`job.started`는 사용자 Timeline에서 중복 일반 문구로 표시되지 않아도 된다.

- `job.idea.brief.draft.queued`
- `job.claimed`
- `job.started`
- `job.idea.information.extraction.started`
- `job.idea.brief.draft.started`
- 추가 질문이 필요하면 `job.idea.questions.completed`
- 정보가 충분하면 `job.idea.brief.draft.completed`
- Conversation 성공 terminal의 `eventType`은 `job.completed`

AI 서버 로그에는 health check 외에 internal execution 요청이 있어야 한다. 성공 후에는 다음이 모두 충족되어야 한다.

- Assistant `QUESTION_SET` 또는 `BRIEF_REVIEW` Message가 표시된다.
- Opportunity Brief Draft와 Field provenance가 표시된다.
- 추가 질문이면 Conversation `NEEDS_INPUT`, 충분하면 `READY_FOR_CONFIRMATION`이다.
- `activeJobId`가 비워지고 TaskRun은 `SUCCEEDED`다.
- AI 제안 Field가 자동 `USER_CONFIRMED`, `LOCKED`, 확정된 `DEFAULT_ASSUMPTION`이 되지 않는다.
- Message와 Timeline 시간이 모두 브라우저 local time 기준으로 같은 시각대를 표시한다.
- Timeline/UI에 technicalCode, Prompt, provider body, 전체 사용자 원문이 노출되지 않는다.

## 4. 새로고침·복구·SSE 연결

실행 중 페이지를 새로고침한다. current Conversation과 durable Job Event replay로 같은 Message/Brief/Timeline이 복원되어야 하며, 완료 event 이후 별도 2초 polling 없이 Assistant Message와 Brief가 나타나야 한다.

Worker가 `RUNNING`인 동안 Backend를 한 번 재시작해 recovery를 확인한다.

```powershell
docker compose restart backend
docker compose logs --since=5m backend ai-server
```

lease 만료 후 bounded retry/recovery되어야 하며 Task가 영구 `RUNNING`으로 남거나 Assistant Message/Brief가 중복 생성되면 실패다.

SSE 연결 중 탭을 닫거나 Network를 offline/online으로 바꿔 disconnect/reconnect를 확인한다. Backend에는 반복 Broken pipe ERROR와 JSON 변환 2차 오류가 없어야 하고, 재연결 시 Last-Event-ID 이후 event만 반영되어야 한다.

## 5. DB 확인

Project ID와 job ID를 실제 값으로 바꿔 실행한다.

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "select id, project_id, task_type, state, attempt_count, max_attempts, retryable, last_error_code from task_runs where task_type in ('IDEA_CONVERSATION_TURN','IDEA_ATTACHMENT_PARSE') order by created_at desc limit 10;"
docker compose exec postgres psql -U aivle -d aivle -c "select id, conversation_id, sequence_number, role, message_type, schema_version, task_run_id, created_at from idea_messages order by id desc limit 20;"
docker compose exec postgres psql -U aivle -d aivle -c "select id, conversation_id, version_number, state, snapshot_hash, task_run_id, created_at from opportunity_brief_versions order by id desc limit 10;"
docker compose exec postgres psql -U aivle -d aivle -c "select job_id, sequence, stage, status, message_key, technical_code, occurred_at from job_events where job_id='<JOB_ID>' order by sequence;"
```

확인 기준은 Message sequence 중복 없음, 같은 TaskRun의 Assistant Message/Brief Version 중복 없음, terminal domain commit 뒤 terminal event 존재, Event params에 사용자 전체 원문/provider body 없음이다.

## 6. 실패 시 수집

비밀값과 사용자 전체 원문을 제거한 뒤 다음만 수집한다.

```powershell
docker compose ps
docker compose logs --since=20m backend ai-server frontend
docker compose exec postgres psql -U aivle -d aivle -c "select id, project_id, task_type, state, attempt_count, max_attempts, claimed_by, lease_expires_at, retryable, last_error_code from task_runs order by created_at desc limit 20;"
docker compose exec postgres psql -U aivle -d aivle -c "select job_id, sequence, stage, status, message_key, technical_code, occurred_at from job_events order by id desc limit 100;"
```

Project ID, conversation ID, job ID, 발생 시각, 기대 상태와 실제 상태를 함께 기록한다. API Key, Authorization, Prompt, provider raw body, 전체 사용자 입력은 수집물에서 제거한다.

## 7. R2 Internal AI 계약 확인

Backend와 AI server를 반드시 함께 rebuild한다. 이전 AI image가 남아 있으면 `IDEA_CONVERSATION_TURN`이 계속 legacy `textContents` 검증에서 400이 된다.

```powershell
docker compose build --no-cache ai-server backend
docker compose up -d ai-server backend
docker compose logs --since=2m ai-server backend
```

새 Project에서 다음 입력을 전송한다.

> 서울 아파트 주민이 재활용품 배출을 예약하고 포인트를 받는 서비스를 만들고 싶습니다. 플랫폼은 직접 수거하지 않고 허가된 파트너가 운반합니다.

AI access log에서 `POST /internal/v1/ai/executions`가 `200`인지 확인한다. Backend/AI log에 request body, 사용자 문장, Authorization, Prompt, provider raw body가 없어야 한다. 성공 후 Assistant `QUESTION_SET` 또는 `BRIEF_REVIEW`, Brief Draft, provenance, `NEEDS_INPUT` 또는 `READY_FOR_CONFIRMATION`, TaskRun `SUCCEEDED`, terminal event를 확인한다.

400이면 AI log에서 다음처럼 값 없는 bounded contract metadata만 수집한다.

```text
Internal AI request rejected taskType=IDEA_CONVERSATION_TURN code=REQUEST_SCHEMA_INVALID fields=[...]
```

수집 허용 항목은 `taskType`, error code, validation `path`, `expectedType`, `category`, job/run ID, 발생 시각이다. `inputSnapshot`, 전체 request JSON, 사용자 원문은 수집하지 않는다. `INVALID_REQUEST/FIELD_CONSTRAINT_VIOLATION` TaskRun은 `attempt_count=1`, `retryable=false`, `FAILED`여야 하며 반복 재시도되면 실패다.

후속 질문에 한 번 답변해 두 번째 Turn도 실행한다. AI request에 이전 Assistant Message가 schema `1.0`의 versioned Envelope로 전달되고, 두 번째 Assistant/Brief Version이 중복 없이 생성되는지 화면과 DB에서 확인한다.
