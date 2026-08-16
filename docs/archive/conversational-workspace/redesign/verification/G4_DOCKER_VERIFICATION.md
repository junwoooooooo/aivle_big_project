# G4 Docker 검증 절차

이 문서는 사용자가 PostgreSQL 기반 Regulatory Boundary를 직접 검증하는 절차다. Codex는 수동 브라우저 검증을 수행하지 않았다. 실제 Docker·브라우저 검증은 G6/G11에서 사용자가 수행한다.

## 1. 환경변수와 실행

`.env`에 다음 값을 설정한다. 실제 secret은 출력하거나 commit하지 않는다.

```dotenv
AI_PROVIDER=openai
AI_API_KEY=<provider key>
AI_MODEL=<model id>
AI_INTERNAL_SERVICE_TOKEN=<long random token>
AI_FIXTURE_MODE=false
MOLEG_API_KEY=<법제처 Open API key>
JWT_SECRET=<32 bytes or longer>
POSTGRES_PASSWORD=<local password>
MINIO_ROOT_PASSWORD=<local password>
VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true
```

기존 로컬 데이터가 필요 없을 때만 `down -v`를 실행한다.

```powershell
docker compose down -v
docker compose up --build -d
docker compose ps
```

모든 장기 실행 서비스가 healthy가 된 뒤 `http://localhost:3000`을 연다.

## 2. Confirmed Brief 준비와 Boundary 실행

1. 로그인 후 프로젝트를 생성하고 Idea 단계로 이동한다.
2. 대화와 필드 편집으로 `problem`, 고객 또는 수혜자, `desiredOutcome`, `targetRegion`, `regulatorySensitiveActivities`를 채운다.
3. AI 제안이 아니라 사용자가 확인한 값인지 확인하고 Brief 전체 확인을 실행한다.
4. Brief 상태가 `CONFIRMED`이고 hash가 표시되는지 확인한다.
5. `규제 경계 생성`을 누른다.
6. JobTimeline에서 queued, classification, routing, evidence fetch, screening, normalization, conflict checking과 terminal event를 순서대로 확인한다.

보호 API를 직접 확인할 때 access token은 URL이 아니라 Header에만 넣는다.

```powershell
$headers = @{ Authorization = "Bearer <access token>"; Accept = "application/json" }
$body = @{ confirmedBriefVersionId = <confirmed brief id> } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v2/projects/<project id>/regulatory-boundaries" -Headers $headers -ContentType "application/json" -Body $body
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v2/projects/<project id>/regulatory-boundaries/current" -Headers $headers
```

## 3. 상태별 입력 예시와 UI 확인

### READY

- 대상 지역, 실제 수행자, 개인정보 처리 여부, 자격·허가 보유 주체를 구체적으로 입력한다.
- 직접 규제 활동은 허가된 파트너가 수행하고 플랫폼 역할은 예약·정보 관리로 제한한다고 확인한다.
- UI에서 허용 패턴, 금지 역할·활동, 필수 통제, 파트너·자격, 고지, Source Warning을 확인한다.
- `conceptBuilderAllowed=true`이고 Concept Builder 입력의 status가 `READY`인지 확인한다.

### NEEDS_INPUT

- 실제 수거/운반 수행자, 운영 지역 또는 개인정보 처리 여부를 `OPEN`으로 남긴다.
- UI에 2~4개의 중요한 질문, 질문 이유, 관련 Brief Field가 표시되는지 확인한다.
- 질문 답변이 Boundary에서 Brief로 자동 반영되지 않고 Idea 대화/Brief 수정 경로로 돌아가는지 확인한다.

### BLOCKED

- 예: `플랫폼이 필요한 자격 없이 직접 수거·운반한다`를 `LOCKED`로 확인한다.
- 공식 Evidence 기반 금지 Rule과 직접 충돌할 때 충돌 Field, Rule, 이유, 사용자 수정 선택지가 표시되는지 확인한다.
- Brief가 자동 변경되지 않고 `PREFERRED/OPEN으로 변경`, `자격 확보`, `허가 파트너 수행`, `범위 제외` 같은 선택지만 제시되는지 확인한다.

FAILED에서는 안전한 오류와 재시도 가능 여부만 보여야 하며 technicalCode, Prompt, provider body가 보여서는 안 된다.

## 4. 새로고침과 Worker Recovery

Boundary가 QUEUED 또는 RUNNING일 때 페이지를 새로고침하고 같은 run/job과 durable event replay가 복원되는지 확인한다. 작업 도중 Backend를 재시작한다.

```powershell
docker compose stop backend
docker compose start backend
docker compose logs --since=10m backend
```

QUEUED 또는 lease가 만료된 `REGULATORY_BOUNDARY_GENERATION` TaskRun이 다시 claim되고 `job.boundary.recovered`가 기록되어야 한다. 동일 Brief/hash에 Run·Version·Rule·Evidence가 중복 생성되어서는 안 된다.

## 5. PostgreSQL 확인

```powershell
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version, success from flyway_schema_history order by installed_rank;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select id, brief_version_id, task_run_id, input_snapshot_hash, state, error_code from regulatory_boundary_runs order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select id, run_id, brief_version_id, brief_snapshot_hash, version_number, status, snapshot_hash, stale_at from regulatory_boundary_versions order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select evidence_key, law_name, article, effective_date, source_status, content_hash from boundary_evidence order by id desc limit 30;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select rule_key, rule_type, structure_key, severity, source_status from boundary_rules order by id desc limit 30;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select question_key, target_brief_field, answer_type, required, state from boundary_questions order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select task_type, state, attempt_count, max_attempts, next_attempt_at from task_runs where task_type = '"'"'REGULATORY_BOUNDARY_GENERATION'"'"' order by created_at desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select job_id, sequence, status, message_key from job_events where message_key like '"'"'job.boundary.%'"'"' order by id desc limit 50;"'
```

## 6. 로그와 성공 기준

```powershell
docker compose logs --since=20m backend
docker compose logs --since=20m ai-server
docker compose logs --since=20m postgres
```

성공 기준:

- Flyway current version이 `4`이고 Migration 오류가 없다.
- Draft/과거 Confirmed Brief로는 새 Boundary가 시작되지 않는다.
- 동일 Brief Version/hash 요청은 동일 Run/job을 반환한다.
- 공식 URL과 content hash가 있는 Evidence만 정상 Rule의 근거가 된다.
- canonical dedupe가 Category 중복 Evidence/Rule을 하나로 합친다.
- READY/NEEDS_INPUT/BLOCKED 구분과 사용자 선택지가 계약대로 표시된다.
- Brief 변경 후 이전 Boundary는 STALE이고 current 결과로 사용되지 않는다.
- terminal Job Event는 Domain Version과 SUCCEEDED TaskRun commit 이후 존재한다.
- 로그/Event/UI에 전체 Brief, Prompt, provider raw body, 법령 원문 전체, Authorization 또는 technicalCode가 노출되지 않는다.

## 7. 실패 시 수집 자료

secret과 사용자 원문을 제거한 뒤 다음을 수집한다.

```powershell
docker compose ps
docker compose logs --since=30m backend
docker compose logs --since=30m ai-server
docker compose logs --since=30m postgres
docker compose config
docker version
```

DB에서는 관련 Run/TaskRun ID, state, attempt, lease, Boundary version/status/hash, Evidence/Rule key, Job Event sequence/messageKey만 수집한다. `input_snapshot`, `result_json`, 전체 Brief/Prompt/법령 본문, provider body, Authorization은 수집하지 않는다.
