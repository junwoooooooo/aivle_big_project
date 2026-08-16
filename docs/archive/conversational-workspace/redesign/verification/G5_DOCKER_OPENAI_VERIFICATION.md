# G5 Docker/OpenAI 검증 절차

이 문서는 사용자가 PostgreSQL·OpenAI 환경에서 G5를 검증하는 절차다. Codex는 브라우저 수동 검증을 수행했다고 주장하지 않는다.

## 1. 환경과 실행

`.env`에 실제 secret을 출력하거나 commit하지 말고 다음 값을 설정한다.

```dotenv
AI_PROVIDER=openai
AI_API_KEY=<provider key>
AI_MODEL=<supported model>
AI_INTERNAL_SERVICE_TOKEN=<long random token>
AI_FIXTURE_MODE=false
AI_CONCEPT_GENERATION_CONCURRENCY=1
AI_CONCEPT_TEST_FAILURE_INJECTION=false
AI_CONCEPT_TEST_FAILURE_PLAN=
MOLEG_API_KEY=<official source key>
JWT_SECRET=<32 bytes or longer>
POSTGRES_PASSWORD=<local password>
MINIO_ROOT_PASSWORD=<local password>
VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true
```

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --since=10m backend
docker compose logs --since=10m ai-server
```

초기화가 필요하고 기존 로컬 데이터 삭제가 허용될 때만 `docker compose down -v` 후 다시 실행한다.

## 2. READY 입력과 Batch 실행

UI에서 Confirmed Opportunity Brief와 그 Brief ID/hash를 참조하는 READY Regulatory Boundary를 만든다. Draft, NEEDS_INPUT, BLOCKED, FAILED, STALE Boundary는 start가 거부되어야 한다.

```powershell
$headers = @{ Authorization = "Bearer <access token>"; Accept = "application/json" }
$body = @{ confirmedBriefVersionId = <brief id>; regulatoryBoundaryVersionId = <boundary id> } | ConvertTo-Json
$start = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v2/projects/<project id>/concept-explorations" -Headers $headers -ContentType "application/json" -Body $body
$start
Invoke-RestMethod -Uri "http://localhost:8080/api/v2/projects/<project id>/concept-explorations/current" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/api/v2/projects/<project id>/concept-explorations/<batch id>/slots" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/api/v2/projects/<project id>/concepts?contract=concept-core-v1" -Headers $headers
```

G2 Event 확인은 `GET /api/v2/jobs/{jobId}/events?after=0`에 `Accept: application/json`을 사용한다. terminal 전 공개 Concept는 0개이고 COMPLETED 후 정확히 3개여야 한다.

## 3. 혼합 실패 재현

실패 주입은 개발 검증에서만 사용한다. 운영 모드에서는 두 값을 반드시 false/empty로 둔다. 계획 형식은 `slotIndex -> attemptNumber -> outcome`이다.

```dotenv
AI_CONCEPT_TEST_FAILURE_INJECTION=true
AI_CONCEPT_TEST_FAILURE_PLAN={"0":{"1":"SCHEMA_INVALID"},"1":{"1":"TRANSIENT_PROVIDER_FAILURE"}}
```

변경 후 `docker compose up -d --force-recreate ai-server`를 실행하고 새 Brief/Boundary Version으로 Batch를 시작한다. 다음 계획으로 각각 검증한다.

- 모두 정상: failure flag false
- schema invalid 1 + 정상 2: `{"0":{"1":"SCHEMA_INVALID"}}`
- transient 1 + 정상 2: `{"0":{"1":"TRANSIENT_PROVIDER_FAILURE"}}`
- permanent 1 + schema invalid 1 + 정상 1: `{"0":{"1":"PERMANENT_PROVIDER_FAILURE"},"1":{"1":"SCHEMA_INVALID"}}`
- redesign required: G4 READY Boundary에 `REQUIRED_PARTNER` Rule을 두고 첫 후보가 partner requirement를 누락하는 입력을 사용
- hard block: `PROHIBITED_ACTIVITY`와 후보 physical activity가 같은 `structureKey`를 사용
- 중복 후보: fixture/stub가 2개 Slot에 같은 7개 canonical 구조 필드를 반환하도록 설정
- 적격 3개 미달: failure plan을 여러 replacement Slot까지 제한 범위 내 설정

각 시나리오 후 flag를 false로 되돌리고 AI container를 재생성한다. 전체 Concept JSON이나 provider body를 로그로 수집하지 않는다.

## 4. Recovery

Batch가 QUEUED/RUNNING일 때 Backend를 재시작한다.

```powershell
docker compose stop backend
docker compose start backend
docker compose logs --since=10m backend
```

lease 만료 후 같은 TaskRun이 claim되고 `job.concept.batch.recovered`가 기록되어야 한다. 동일 input의 Batch, Slot, Attempt, 공개 Concept, Assessment가 중복 생성되면 실패다.

## 5. DB와 로그 확인

```powershell
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version, success from flyway_schema_history order by installed_rank;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select id,status,eligible_count,inspected_candidates,brief_snapshot_hash,boundary_snapshot_hash,input_snapshot_hash from concept_exploration_batches order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select batch_id,slot_index,status,current_phase,attempt_count,legal_state,eligible from concept_slots order by batch_id desc,slot_index;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select slot_id,attempt_number,phase,outcome,provider_failure_type,duplicate_status,concept_snapshot_hash from concept_attempts order by id desc limit 50;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select status,brief_hash,boundary_hash,concept_snapshot_hash,validated_snapshot_hash from concept_legal_assessments order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select batch_id,display_order,legal_state,validated_snapshot_hash from exploration_concepts order by batch_id desc,display_order;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select sequence,status,message_key,message_params_json from job_events where message_key like '"'"'job.concept.%'"'"' order by id desc limit 100;"'
docker compose logs --since=30m backend
docker compose logs --since=30m ai-server
```

성공 기준은 Flyway V5, 같은 input의 Batch 1개, Slot index/Attempt number 무중복, mixed failure 격리, exactly 3 public concepts, assessment hash 일치, terminal Event가 domain 결과 이후 존재, 재시작 복구다. 로그/Event에는 전체 Brief/Concept/Prompt/provider body/법률 원문/Authorization이 없어야 한다.

실패 시 `docker compose ps`, 위의 식별자·상태·hash query, `docker compose logs --since=30m backend ai-server postgres`, `docker compose config`를 수집한다. Secret, 사용자 전체 원문, candidate JSON, TaskRun input/result JSON은 제거한다.
