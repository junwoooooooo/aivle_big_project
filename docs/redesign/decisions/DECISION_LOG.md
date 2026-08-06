# 대화형 사업검증 워크스페이스 Decision Log

- 상위 기준: [SPEC v1.0](../CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md)
- 현행 매핑: [CURRENT_TO_TARGET_MAP](../current-to-target/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md)

## ADR-CVW-0001 — Design Freeze v1.0을 구현 기준으로 채택

- 상태: 승인됨
- 결정일: 2026-08-05
- 기준 SHA: `967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d`

### 배경

현재 저장소는 Idea Source/Origin, Legal Precheck, Concept Eligibility,
TaskRun과 레거시 Quick/Detailed/Selection/Persona/Report를 제공하지만,
대화형 Opportunity Brief, Regulatory Boundary Rule, durable Job Event/SSE,
versioned legal publication, Analysis Readiness 목표 계약은 구현하지 않았다.

### 결정

1. SPEC v1.0의 I-01~I-12를 구현 편의로 변경하지 않는다.
2. 사용자 5단계와 내부 8단계를 목표 여정으로 고정한다.
3. 기존 테이블과 route를 삭제·rename하지 않고 additive migration과 feature
   flag로 병행한다.
4. `task_runs`, `task_attempts`, `task_results`, Idea Source/Origin, 공식
   Legal Evidence pipeline, Concept Eligibility의 이력·stale·검증 자산을
   재사용한다.
5. `conversationalValidationWorkspaceEnabled`를 설계상 flag 명칭으로
   사용하며, G0에서는 코드나 환경 설정에 추가하지 않는다.
6. 신규 Journey E2E가 Green이 되기 전 현재 route와 레거시 MVP를
   공식 경로에서 제거하지 않는다.
7. UI 프로젝트 제목과 Journey 위계는 유지하고 본문·카드·메타만 1~2단계
   축소한다.

### 결과

- G1은 영속 도메인, 상태, Version, Hash, Stale만 구현한다.
- AI prompt, SSE, frontend 전환은 G1 범위가 아니다.
- 현재 데이터는 강제 변환하지 않고 신규 snapshot부터 새 계약을 사용한다.

### 변경 통제

이 결정을 변경하려면 작업을 중단하고 다음을 포함한 후속 ADR을 작성한다.

- 충돌 내용과 문제
- 영향받는 불변식
- 가능한 대안과 선택 근거
- API/DB/사용자 여정 영향
- additive migration 또는 rollback 영향
- 기존 E2E와 신규 수용 테스트 영향

사용자 승인 전에는 상태를 `승인됨`으로 바꾸거나 구현으로 우회하지 않는다.

## ADR-CVW-0002 — Authenticated fetch 기반 SSE 전송

- 상태: 승인됨
- 결정일: 2026-08-05

### 배경

Frontend access token은 메모리에 보관되고 보호 API는 `Authorization: Bearer`
헤더를 요구한다. 브라우저 native `EventSource`는 임의 Authorization 헤더를
설정할 수 없으므로 현재 인증 계약과 함께 사용할 수 없다.

### 결정

비동기 Job Event 스트림은 native `EventSource` 대신 fetch와
`ReadableStream` 기반의 인증 SSE client로 구현한다.

다음 계약은 유지한다.

- `Content-Type: text/event-stream`
- durable Event 영속과 `Last-Event-ID` replay
- cursor deduplication, reconnect, polling fallback
- project ownership 검증

### 보안 제약

- access token을 URL query parameter에 넣거나 localStorage에 저장하지 않는다.
- 인증 cookie 구조로 전환하지 않는다.
- Prompt, raw provider body, 전체 사용자 입력을 Event에 저장하지 않는다.

### 결과

- Frontend가 SSE frame parsing, reconnect, abort, fallback을 직접 관리한다.
- native `EventSource`, query token, subscription ticket, WebSocket은 사용하지 않는다.
- G1 `job_events`를 재사용하며 추가 DB migration은 만들지 않는다.

## ADR-CVW-0003 — G3 Message/Provenance 구조화와 Durable Worker V3

- 상태: 승인됨
- 결정일: 2026-08-05

### 배경

G3 최초 구현은 Assistant Message envelope를 `idea_messages.content TEXT` 안에
`idea-message-v1` JSON으로 저장하고, parse 실패를 legacy TEXT로 낮췄다.
Opportunity Field provenance는 `source_reference VARCHAR(500)`에 JSON 문자열로
저장했다. 이 구조는 schema version/type strict 검증, FK/project isolation,
길이, 검색, 손상 탐지를 장기 계약으로 보장하지 못한다. Attachment와
Conversation AI는 commit 후 process-local executor 제출에 의존해 서버 재시작 시
QUEUED/만료 RUNNING TaskRun을 자동 재개하지 못했다.

### 결정

선택 B를 채택하고 additive
`V3__idea_workspace_durability_hardening.sql`을 적용한다.

1. Assistant Message는 `schema_version`, `message_type`, `payload_json`과
   선택적 unique `task_run_id`를 사용한다. USER TEXT는 `content`에 저장하고
   Assistant structured envelope와 명시적으로 분리한다.
2. schema `1.0`, 허용 message type, type별 exact payload를 Backend와 Frontend가
   모두 검증한다. 손상 또는 미지원 envelope를 TEXT로 강등하지 않는다.
3. provenance는 `source_message_id`, `source_attachment_id`, `confidence`,
   `user_confirmed`, `confirmed_at` 열과 FK/check로 보존한다.
   `source_reference`는 기존 호환 열로 삭제하지 않지만 신규 정본으로 사용하지 않는다.
4. `IDEA_ATTACHMENT_PARSE`, `IDEA_CONVERSATION_TURN`을 TaskRun type으로 추가하고
   `next_attempt_at`, 기존 attempt lease/claim/result를 사용한 정기 worker/recovery로 실행한다.
5. Attachment/Assistant/Brief의 TaskRun unique link와 deterministic idempotency key로
   at-least-once 실행의 중복 채택을 막는다.
6. 성공 도메인 변경과 Conversation TaskRun result 채택은 한 transaction에서 처리하며,
   completed Job Event는 해당 commit 이후에만 발행한다.

### Migration과 호환성

- 기존 테이블/열은 삭제하거나 rename하지 않는다.
- V2의 기존 Assistant 행은 schema `1.0`/TEXT로 표시하고 기존 content를 payload
  migration source로 보존한다. 신규 조회는 strict parser를 통과해야 한다.
- V2에는 G3 운영 provenance 행이 존재하지 않는 전제가 있으므로 새 scalar provenance는
  신규 write부터 정본이다. 기존 `source_reference` 문자열을 추측해 FK로 승격하지 않는다.
- clean V3, V1→V3, V2→V3를 PostgreSQL에서 검증한다.

### 결과와 범위

- G4는 confirmed Brief의 구조화 provenance와 동일 TaskRun/JobEvent worker 계약을 사용한다.
- G4 Regulatory Boundary, Concept Generator, 새 화면, 인증 방식은 변경하지 않는다.

## ADR-CVW-0004 — G4 Regulatory Boundary 구조화 V4

- 상태: 승인됨
- 결정일: 2026-08-05

### 배경

G1 V2의 Boundary 표는 Version과 기본 Rule/Evidence/Question 행을 제공하지만 다음 G4
불변식을 직접 보존하지 못한다.

- Run의 실제 분류·라우팅·조회·선별·정규화·충돌 단계와 `STALE`
- Boundary Version이 기준으로 삼은 Brief hash
- Evidence의 source type, plain summary, 관련성, 조회 시각, content hash
- Rule의 structure key, title/description, normalized requirement, appliesWhen, source status,
  파트너·자격·고지·대안
- Question의 answer type/options/required 및 Rule/Evidence 참조

기존 `statement`, `rationale`, `source_reference`류 문자열에 새 의미를 겹쳐 넣으면 검색,
검증, 중복 제거 및 G5 입력 계약이 불명확해진다.

### 결정

additive `V4__regulatory_boundary_contract.sql`을 적용한다.

1. 기존 Boundary 표와 열은 삭제하거나 rename하지 않는다.
2. Run 상태 check를 G4 pipeline/terminal/stale 상태로 확장하고 동일
   `(project_id, brief_version_id, input_snapshot_hash)`와 TaskRun 연결을 유일하게 유지한다.
3. Version에 `brief_snapshot_hash`, `stale_at`을 추가한다.
4. Evidence·Rule·Question에 G4 구조 열과 검증 constraint/index를 추가한다.
5. 기존 G1 행은 삭제하지 않으며 새 필수 열에는 명시적인 legacy warning 값만 적용한다.
   기존 Legal Precheck 데이터를 Boundary Rule로 추측 변환하지 않는다.
6. 동일 Evidence key는 Boundary Version + 법령명 + 조문 + 시행일 + content hash이고,
   Rule dedupe key는 `ruleType + structureKey + canonical normalizedRequirement + canonical appliesWhen`이다.
7. 공식 Evidence 조회는 기존 Legal Source pipeline을 재사용하지만 PASS/FAIL gate와
   plainSummary→guardrail 복사 방식은 재사용하지 않는다.

### 영향

- clean 및 V1/V2/V3→V4 Migration과 PostgreSQL repository/lease/idempotency를 검증한다.
- G5는 `READY` Boundary의 explicit Concept Builder input만 소비한다.
- 기존 Legal Precheck, Concept Generator, Legal Report와 인증 구조는 변경하지 않는다.

## ADR-CVW-0005 — G5 Concept Core additive V5와 legacy 분리

- 상태: 승인됨
- 결정일: 2026-08-05

### 배경

기존 Concept batch/draft/version은 single-candidate aggregate와 PASS/FAIL legal gate를 저장한다. 독립 Slot/Attempt, phase별 실패, 시스템 Origin/Boundary Trace, 5단계 법률 구현 가능성, validated snapshot hash, 공개/내부 draft 분리를 안전하게 표현할 수 없다. 기존 `ConceptVersion` 매핑에는 필드 축약과 의미 중복도 있어 신규 정본으로 재사용하면 기존 데이터 의미를 추측해야 한다.

### 결정

additive `V5__concept_core.sql`을 적용하고 G5 전용 Batch/Slot/Attempt/Trace/Assessment/Public Concept 구조를 사용한다. 기존 Concept 테이블과 `/concepts` legacy 계약은 삭제·rename·자동 변환하지 않는다. 신규 공개 조회는 `contract=concept-core-v1`으로 명시한다.

AI는 Strict Skeleton만 생성한다. Origin/Boundary Trace, Evidence 연결과 authoritative legal state는 저장된 Brief/Boundary에서 서버가 결정론적으로 생성한다. 기본 concurrency는 1이고 1~3만 허용한다. Slot failure는 격리하며 schema repair, transient retry, redesign, replacement에 각각 제한을 둔다.

### 영향

- G6/G7은 신규 공개 Concept 계약을 명시적으로 소비해야 한다.
- legacy Quick Assessment에는 자동 연결하지 않는다.
- clean 및 V1~V4 upgrade, idempotency, uniqueness, recovery, stale을 PostgreSQL targeted test로 검증한다.
- 개발 failure injection은 명시적 flag가 true일 때만 활성화한다.
