# G3 Docker 검증 절차

이 문서는 사용자가 PostgreSQL 기반 G3 Idea Workspace와 G3-H durable worker를 직접 검증하는 절차다. Codex는 이 문서 작성 과정에서 수동 브라우저 검증을 수행하지 않았다.

## 1. 사전 준비와 환경변수

저장소 루트에서 다음을 실행한다.

```powershell
Copy-Item .env.example .env
```

`.env`에 최소한 다음 값을 설정한다. 실제 secret은 출력하거나 저장소에 commit하지 않는다.

```dotenv
AI_PROVIDER=openai
AI_API_KEY=<provider key>
AI_MODEL=<model id>
AI_INTERNAL_SERVICE_TOKEN=<long random token>
AI_FIXTURE_MODE=false
JWT_SECRET=<32 bytes or longer>
POSTGRES_PASSWORD=<local password>
MINIO_ROOT_PASSWORD=<local password>
VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=true
```

실제 G3 대화에는 `MOLEG_API_KEY`가 필요하지 않다. G4 법률 조회는 이 검증 범위가 아니다.

## 2. 초기화와 실행

기존 로컬 데이터가 필요 없을 때만 다음 명령으로 named volume을 제거한다. 복구할 데이터가 있으면 실행하지 않는다.

```powershell
docker compose down -v
docker compose up --build -d
docker compose ps
```

모든 서비스가 healthy이고 `minio-init`이 성공 종료한 뒤 `http://localhost:3000`을 연다.

## 3. 프로젝트와 대화 검증

1. 사용자 등록 또는 로그인 후 새 프로젝트를 만든다.
2. 프로젝트의 Idea 단계로 이동한다.
3. 짧은 문제 설명을 전송한다.
4. 실제 Job Timeline에 queued/claimed/started 및 Brief/질문 단계가 나타나는지 확인한다.
5. 후속 질문에 답하고 AI 제안과 사용자 확인값이 서로 다른 상태로 표시되는지 확인한다.
6. 필드를 직접 편집하고 Decision Status를 변경한 뒤, 필수값이 충족되기 전에는 Brief 확인이 차단되는지 확인한다.
7. AI 제안이 자동으로 confirmed 또는 LOCKED가 되지 않는지 확인한다.

## 4. TXT/DOCX 첨부 검증

1. UTF-8 TXT와 암호화되지 않은 DOCX를 각각 첨부한다.
2. `received → claimed → started → parsing → extraction → completed` 실제 이벤트와 Attachment `EXTRACTED` 상태를 확인한다.
3. PDF 또는 CSV를 선택해 지원하지 않는 형식 오류가 표시되는지 확인한다.
4. 파일 본문, Prompt, provider body, Authorization이 Timeline이나 일반 로그에 나타나지 않는지 확인한다.

## 5. 새로고침과 Backend 재시작 복구

작업이 QUEUED 또는 RUNNING인 동안 페이지를 새로고침한다. 같은 Conversation, Message, Attachment, Draft Brief와 durable Job Event가 복원되어야 한다.

Worker recovery는 다음 순서로 확인한다.

```powershell
docker compose stop backend
docker compose start backend
docker compose logs --since=10m backend
```

재시작 전에 남은 QUEUED task 또는 lease가 만료된 RUNNING task가 새 worker에 claim되고, 동일 Assistant Message/Brief Version/완료 Event가 중복 생성되지 않아야 한다. RUNNING lease를 확실히 남기는 장애 주입은 별도 테스트 환경에서만 container를 강제 종료하여 수행한다.

## 6. PostgreSQL 상태 확인

비밀번호를 command line에 직접 넣지 않고 compose container 내부 환경을 사용한다.

```powershell
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select installed_rank, version, success from flyway_schema_history order by installed_rank;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select task_type, state, attempt_count, max_attempts, next_attempt_at from task_runs where task_type in ('"'"'IDEA_ATTACHMENT_PARSE'"'"','"'"'IDEA_CONVERSATION_TURN'"'"') order by created_at desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select task_run_id, attempt_number, state, claimed_by, lease_expires_at from task_attempts order by created_at desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select role, schema_version, message_type, task_run_id from idea_messages order by id desc limit 20;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select field_key, source_type, source_message_id, source_attachment_id, confidence, user_confirmed, confirmed_at from opportunity_field_values order by id desc limit 30;"'
docker compose exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select job_id, sequence, event_type, status, message_key from job_events order by id desc limit 50;"'
```

`.env`에서 기본 DB 이름/사용자를 변경하지 않았다면 `$env:POSTGRES_USER`와 `$env:POSTGRES_DB` 대신 각각 `aivle`을 사용할 수 있다.

## 7. 로그와 성공 기준

```powershell
docker compose logs --since=15m backend
docker compose logs --since=15m ai-server
docker compose logs --since=15m postgres
```

성공 기준:

- Flyway current version이 `3`이고 migration failure가 없다.
- 동일 TaskRun은 동시에 한 worker만 소유하며 attempt가 `max_attempts`를 넘지 않는다.
- 재시작 후 QUEUED/만료 RUNNING task가 복구된다.
- 동일 task_run_id의 Assistant Message와 Brief Version은 각각 최대 1개다.
- completed Job Event는 성공 도메인 행과 SUCCEEDED TaskRun 이후에 존재한다.
- Assistant는 schema `1.0`의 허용 message type만 사용하고 손상 envelope는 일반 TEXT로 표시되지 않는다.
- provenance FK가 같은 프로젝트의 Message/Attachment를 가리키고 AI 제안은 `user_confirmed=false`다.
- 로그/Event에 token, 전체 Prompt, provider raw body, 첨부/사용자 전체 원문이 없다.

## 8. 실패 시 수집 자료

secret과 사용자 원문을 제거한 뒤 다음 결과를 수집한다.

```powershell
docker compose ps
docker compose logs --since=30m backend
docker compose logs --since=30m ai-server
docker compose logs --since=30m postgres
docker compose config
docker version
```

DB에서는 해당 `task_runs.id`의 state/attempt와 `job_events`의 sequence/message_key만 수집한다. `input_snapshot_json`, `result_json`, Message content, 파일 본문, Authorization, Prompt, provider body는 진단 자료에 포함하지 않는다.
