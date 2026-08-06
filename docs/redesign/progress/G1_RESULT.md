# G1 결과 — Domain Foundation

- 작업일: 2026-08-05
- 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`
- 작업 브랜치: `main`
- 범위: Conversation, Brief, Boundary, Job Event의 영속 구조와 상태/버전/hash/stale 계약
- commit/push: 수행하지 않음

## 1. 변경 파일

### Migration

- `backend/src/main/resources/db/migration/V2__conversational_validation_domain.sql`

### Domain 및 repository/service

- `backend/src/main/java/com/aivle/backend/journey/conversation/**`
- `backend/src/main/java/com/aivle/backend/journey/brief/**`
- `backend/src/main/java/com/aivle/backend/journey/boundary/**`
- `backend/src/main/java/com/aivle/backend/journey/foundation/**`
- `backend/src/main/java/com/aivle/backend/jobevent/**`
- `backend/src/main/java/com/aivle/backend/journey/IdeaSourceRepository.java`

### 테스트

- `backend/src/test/java/com/aivle/backend/journey/conversation/ConversationFoundationIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/journey/brief/OpportunityBriefFoundationTests.java`
- `backend/src/test/java/com/aivle/backend/journey/boundary/RegulatoryBoundaryFoundationTests.java`
- `backend/src/test/java/com/aivle/backend/jobevent/JobEventFoundationTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlContainerSmokeTests.java`

### 문서

- `docs/redesign/current-to-target/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md`
- `docs/redesign/progress/G1_RESULT.md`

## 2. Migration 번호와 신규 테이블

Migration은 additive `V2__conversational_validation_domain.sql`이다. 기존 테이블의
삭제, rename, 강제 변환은 없다.

1. `idea_conversations`
2. `idea_messages`
3. `idea_attachments`
4. `opportunity_brief_versions`
5. `opportunity_field_values`
6. `regulatory_boundary_runs`
7. `regulatory_boundary_versions`
8. `boundary_rules`
9. `boundary_evidence`
10. `boundary_questions`
11. `job_events`

프로젝트별 Brief/Boundary 버전, 대화별 message 순서, 버전별 field/rule/evidence/question
key, job별 event 순서는 DB unique constraint로 보호한다. 기존 `projects`,
`idea_sources`, `stored_files`, `task_runs`와는 FK로 연결했다.

## 3. 구현 계약

### 상태 전이

- Conversation: `ACTIVE -> CLOSED`
- Attachment: `UPLOADED -> PROCESSING -> EXTRACTED`,
  `UPLOADED|PROCESSING -> FAILED`
- Opportunity Brief: `DRAFT -> CONFIRMED`
- Regulatory Boundary Run: `QUEUED -> RUNNING -> SUCCEEDED|FAILED`
- Boundary Version: 생성 시점의 불변 상태 `READY|NEEDS_INPUT|BLOCKED|FAILED`
- Boundary Question: `OPEN -> ANSWERED`
- Job Event 상태 값: `QUEUED|RUNNING|COMPLETED|FAILED|NEEDS_INPUT`

Brief field 계약은 decision status
`LOCKED|PREFERRED|OPEN|ASSUMPTION`, source type
`USER_CONFIRMED|SOURCE_EXTRACTED|AI_PROPOSED|DEFAULT_ASSUMPTION|MISSING`을
그대로 사용한다. `MISSING`은 값이 없어야 하고 다른 source type은 값이 있어야 하므로
누락값을 기본값으로 보완하지 않는다.

### 버전, hash, current, stale

- Opportunity Brief와 Boundary Version은 프로젝트별 증가 버전과 current 조회를 제공한다.
- snapshot hash는 canonical JSON의 SHA-256이며 `sha256:` 접두사를 저장한다.
- 객체 키 순서, Unicode NFC, 동등한 십진수 표기는 같은 hash를 만든다.
- 현재 confirmed Brief의 id/hash와 Boundary 입력 snapshot이 다르면 Boundary는 stale이다.
- Brief 변경 후 stale: Boundary, Concept, Quick Assessment, Selection.
- Boundary 변경 후 stale: Concept, Quick Assessment, Selection.

### 프로젝트 격리

신규 create/read 서비스는 기존 `ProjectRepository`의 owner-scoped 조회를 사용한다.
Conversation message/attachment, Brief/field, Boundary/run/version과 job event replay는
프로젝트 범위가 일치해야 한다. 다른 프로젝트 또는 다른 소유자의 id로 조회하거나
연결할 수 없다.

## 4. 기존 테이블 재사용 결정

- `projects`: 신규 모든 aggregate의 소유권 기준으로 재사용.
- `idea_sources`: Conversation의 선택적 원천 연결로 재사용.
- `stored_files`: Attachment의 원본 파일 연결로 재사용.
- `task_runs`: `job_events.task_run_id`의 선택적 실행 연결로 재사용.
- `idea_origin_versions`: 신규 Brief snapshot/provenance 계약과 달라 대체하지 않고 레거시 유지.
- `legal_precheck*`: 실행 가능한 Boundary Rule/Evidence 계약과 달라 대체하지 않고 레거시 유지.
- `concept_*`: G5 이후 신규 slot/version 계약 전까지 변경 없이 유지.
- `task_attempts`, `task_results`: 실행 이력/결과 용도를 유지하고 durable event 저장소로 전용하지 않음.

## 5. 테스트 결과

- 지정 G1 테스트: 14건 통과, 실패 0, 오류 0.
  - `gradlew.bat test --tests "*Conversation*" --tests "*Opportunity*" --tests "*Boundary*" --tests "*JobEvent*" --no-daemon --console=plain`
- PostgreSQL migration/integration: 18건 통과, 실패 0, 오류 0.
  - `gradlew.bat postgresTest --no-daemon --console=plain`
  - 로컬 Docker daemon 최소 API가 1.40이어서 해당 프로세스에서만
    `DOCKER_API_VERSION=1.40`을 사용했다. 저장소 환경 설정은 변경하지 않았다.
- 전체 backend 회귀: 269건 통과, 실패 0, 오류 0, skipped 0.
  - `gradlew.bat test --no-daemon --console=plain`
  - 최초 실행에서 기존 `TaskRunServiceIntegrationTests`의 lease 만료 timing 테스트
    1건이 일시 실패했으나 동일 테스트 단독 재실행과 전체 재실행은 모두 통과했다.
- `git diff --check`: 통과.

검증 범위는 clean migration, 명시적 V1→V2 upgrade, repository 영속/조회,
상태 전이, 버전 uniqueness, deterministic hash, stale cascade, project isolation을 포함한다.

## 6. G2 Event 계약

`job_events`의 최소 영속 계약은 다음과 같다.

- identity: `id`, `job_id`, 프로젝트 안에서 사용할 증가 `sequence`
- scope/link: 필수 `project_id`, 선택적 `task_run_id`
- progress: `stage`, `event_type`,
  `status(QUEUED|RUNNING|COMPLETED|FAILED|NEEDS_INPUT)`
- message: 사용자용 `message_key`, 구조화된 `message_params_json`, 선택적
  비민감 `technical_code`
- time: `occurred_at`
- replay: `(job_id, sequence)` unique 및 `sequence > cursor` 오름차순 조회,
  반드시 `project_id` 범위 적용

G2는 이 계약 위에 publisher, SSE, reconnect cursor replay, polling fallback을 추가한다.
G1에는 event 전송 controller/API를 넣지 않았다.

## 7. Migration 및 미해결 위험

- Migration 영향: V1 스키마에 신규 11개 테이블과 FK/index/check/unique constraint만 추가한다.
  기존 행의 backfill이나 변환은 없다.
- PostgreSQL migration은 clean DB와 V1-only DB upgrade를 모두 검증했다.
- 동시 Brief/Boundary 버전 생성과 message/event sequence 할당은 DB unique constraint가
  최종 경합을 차단한다. G2/G3에서 retry 정책을 연결해야 한다.
- stale cascade는 G1의 판정 계약이다. 후속 단계 aggregate가 추가될 때 실제 조회/표시와
  재실행 제어에 연결해야 한다.
- `job_events.message_params_json`은 구조화 payload 저장소다. G2 publisher에서 API key,
  Authorization, 전체 Prompt, raw provider body, 전체 사용자 원문을 넣지 않는 검증이 필요하다.
- AI 호출, Prompt, frontend, 신규 외부 API, SSE는 이번 단계에서 변경하지 않았다.
