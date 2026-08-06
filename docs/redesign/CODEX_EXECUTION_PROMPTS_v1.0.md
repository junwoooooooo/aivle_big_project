# Codex 5.6sol 단계별 작업지시문 패키지 v1.0

아래 Prompt는 **한 번에 하나만** 로컬 Codex에 전달한다. 각 단계가 완료되고 결과를 검토한 뒤 다음 Prompt를 실행한다.

## 공통 머리말 — 모든 Prompt 앞에 붙이기

```text
저장소:
C:\Users\seewo\Desktop\big_proj_01\new_2

기준:
- main 기준 최신 저장 상태를 먼저 확인한다.
- 고정 기획서: 대화형 AI 사업검증 워크스페이스 DESIGN FREEZE v1.0
- 기준 커밋: 967c19a8eca17ff24f159175fb3e7ecc9fb6cf9d

작업 원칙:
1. 먼저 관련 코드·계약·Migration·테스트를 실제 파일로 확인한다.
2. 이번 Prompt 범위만 구현한다. 다음 단계로 확장하지 않는다.
3. 기존 공식 Journey와 보존 MVP를 삭제하거나 축소하지 않는다.
4. Contract를 완화하거나 누락값을 임의 Default로 채우지 않는다.
5. 사용자 확정값, 문서 추출값, AI 제안값, 기본 가정, 결측값을 구분한다.
6. API Key, Authorization, 전체 Prompt, Raw Provider Body, 전체 사용자 원문을 로그에 남기지 않는다.
7. 기능 변경에는 테스트와 문서 갱신을 포함한다.
8. commit/push하지 않는다. 완료 후 변경 범위와 검증 결과를 보고하고 멈춘다.
9. git diff --check를 실행한다.
10. 불명확한 지점은 추측 구현하지 말고 현재 계약과 충돌을 보고한다.
```

---

# Prompt G0 — 기준선 감사와 설계 동결 문서

```text
[대화형 사업검증 워크스페이스 G0 — Baseline Audit & Design Freeze]

목적:
현재 코드와 고정 기획서의 차이를 근거 기반으로 정리하고 구현 기준 문서를 저장소에 추가한다. 기능 코드는 변경하지 않는다.

확인 대상:
- frontEnd Idea/Legal/Concept route와 page/provider/api
- backend JourneyController, JourneyAiService, LegalPrecheck, ConceptJourneyService
- ai journey models/provider/prompts
- task_runs/task_attempts/task_results
- 현재 Migration과 docs/redesign
- 레거시 Quick/Detailed/Selection/Persona/Report 연결

생성 문서:
1. docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_SPEC_v1.0.md
2. docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_IMPLEMENTATION_PLAN_v1.0.md
3. docs/redesign/CONVERSATIONAL_VALIDATION_WORKSPACE_CURRENT_TO_TARGET_MAP.md
4. docs/redesign/DECISION_LOG.md가 있으면 ADR 추가, 없으면 생성

문서 필수 내용:
- 12개 고정 불변식
- 사용자 5단계 / 내부 전체 단계
- 현재 파일·테이블·API의 재사용/대체/신규/레거시 분류
- 예상 additive migration 목록
- Feature Flag와 Cutover 전략
- 단계 G1~G11 의존 관계
- 변경 통제 절차
- UI 타이포그래피: 주요 제목 유지, 본문/카드/메타 1~2단계 축소

변경 금지:
- Java/Python/JS/SQL 기능 파일
- package dependencies
- compose/env

검증:
- git diff --check
- 문서 링크와 경로 확인

완료 보고:
- 기준 SHA
- 생성 문서
- 재사용 가능 영역
- 가장 큰 구조적 충돌 5개
- 다음 단계 G1의 정확한 수정 후보

완료 후 멈춘다.
```

---

# Prompt G1 — Domain Foundation

```text
[대화형 사업검증 워크스페이스 G1 — Conversation, Brief, Boundary, Event Domain Foundation]

목적:
AI 호출과 UI 없이 신규 도메인의 영속 구조, 상태, Version, Hash, Stale 계약만 구현한다.

먼저:
- G0 문서와 현재 DB migration 번호를 확인
- 기존 idea_sources, idea_origin_versions, legal_precheck*, task_runs, concept_* 재사용 가능성을 대조

구현:
- additive migration
- Conversation: idea_conversations, idea_messages, idea_attachments
- Brief: opportunity_brief_versions, opportunity_field_values
- Boundary: regulatory_boundary_runs, regulatory_boundary_versions, boundary_rules, boundary_evidence, boundary_questions
- Async: job_events 또는 기존 TaskRun과 연결되는 동등 구조
- Enum:
  LOCKED/PREFERRED/OPEN/ASSUMPTION
  USER_CONFIRMED/SOURCE_EXTRACTED/AI_PROPOSED/DEFAULT_ASSUMPTION/MISSING
- Version 번호, current 조회, canonical JSON hash
- 상위 Version 변경 시 Stale 판정 service
- 프로젝트 소유권 검증

유의:
- 기존 테이블 삭제·rename 금지
- 신규 API는 repository/service test에 필요한 최소 read/create만 허용
- AI Prompt와 Frontend 변경 금지

테스트:
- clean migration
- 기존 migration upgrade
- repository
- state transition
- version uniqueness
- deterministic hash
- stale cascade unit test
- project isolation

검증:
.\gradlew.bat test --tests "*Conversation*" --tests "*Opportunity*" --tests "*Boundary*" --tests "*JobEvent*" --no-daemon --console=plain
.\gradlew.bat postgresTest --no-daemon --console=plain
git diff --check

완료 보고:
- Migration 번호와 테이블
- 상태 전이
- 기존 테이블 재사용 결정
- 테스트 결과
- 다음 G2에서 사용할 Event 계약

완료 후 멈춘다.
```

---

# Prompt G2 — Durable Job Events & SSE

```text
[대화형 사업검증 워크스페이스 G2 — Durable Job Events, SSE, Polling Fallback]

목적:
장기 작업 이벤트를 영속 저장하고 브라우저가 새로고침 후 복원할 수 있는 공통 이벤트 인프라를 구현한다.

Backend:
- JobEventPublisher/Repository/QueryService
- sequence 원자적 증가
- projectId/jobId/taskRunId 연결
- messageKey + safe params, technicalCode 분리
- GET /api/v2/jobs/{jobId}/events (SSE)
- Last-Event-ID 지원
- GET /api/v2/jobs/{jobId}/events?after={sequence} polling fallback
- heartbeat와 emitter cleanup
- ownership/security

Frontend:
- shared async-events API
- useJobEvents(jobId)
- EventSource reconnect
- last sequence dedupe
- SSE failure polling fallback
- generic JobTimeline component
- user message mapper

금지:
- 가짜 percent
- raw prompt/body 노출
- Idea/Concept 화면 교체

테스트:
- ordering/replay/dedup
- reconnect
- unauthorized
- cleanup
- frontend reducer/hook fake timers

검증:
Backend targeted tests
Frontend lint + targeted tests + baseline
Build
git diff --check

완료 후 이벤트 예제와 G3 연결 포인트를 보고하고 멈춘다.
```

---

# Prompt G3 — Conversational Idea Intake

```text
[대화형 사업검증 워크스페이스 G3 — Conversational Idea Intake & Opportunity Brief]

목적:
기존 상단 프로젝트·Journey UI는 유지하고 본문 입력 영역을 채팅 + Brief 정보판으로 교체한다. Feature Flag 아래 신규 흐름을 구현한다.

AI Task:
- IDEA_CONVERSATION_TURN
- IDEA_ATTACHMENT_EXTRACTION
- OPPORTUNITY_BRIEF_SYNTHESIS 또는 동등한 계약

응답:
- assistantMessage
- 2~4 followUpQuestions
- fieldPatches
- sourceType
- suggestedDecisionStatus
- confidence
- missingFields
- contradictions
- readiness

Backend:
- conversation/message/attachment API
- message 저장 후 async job
- file parsing은 기존 parser 재사용 우선
- AI patch strict validation
- Draft Brief 갱신
- 직접 field edit
- confirm gate
- Confirmed Brief hash/version

Frontend:
- chat 65 / brief 35
- mobile drawer
- messages/questions/options/file cards/job timeline
- source/status badges
- direct edit
- confirm
- body/card/meta typography 축소

필수 질문 종료 조건과 조건부 질문군을 고정 기획서대로 구현한다.

테스트:
- text conversation
- attachment
- contradictions
- all locked warning
- confirm gate
- refresh restore
- security/file limits

기존 Idea page는 flag off에서 유지한다.
완료 후 사용자 흐름 스크린샷 또는 DOM 구조와 테스트 결과를 보고하고 멈춘다.
```

---

# Prompt G4 — Regulatory Boundary

```text
[대화형 사업검증 워크스페이스 G4 — Regulatory Boundary]

목적:
Confirmed Opportunity Brief에서 공식 Evidence를 조회하고 Concept 생성에 사용할 실행 가능한 Boundary Rule을 만든다.

Rule types:
PROHIBITED_ROLE
PROHIBITED_ACTIVITY
ALLOWED_PATTERN
REQUIRED_CONTROL
REQUIRED_PARTNER
REQUIRED_DISCLOSURE
UNRESOLVED_FACT

Pipeline:
brief classification → route → official source → screening → evidence persist → rule normalization → locked conflict

중요:
- 법률 조문 제목/plainSummary를 그대로 hard constraint로 사용 금지
- Rule은 evidenceIds를 가져야 함
- source partial/warning 유지
- READY/NEEDS_INPUT/BLOCKED/FAILED
- BLOCKED 시 userActionOptions

Frontend:
- chat 영역 Job Event
- 허용 방향/금지 방향/필수 조건/추가 질문
- blocked 수정 선택지

테스트:
- duplicate evidence dedupe
- category duplicate가 rule duplicate를 만들지 않음
- blocked vs needs input
- evidence 없는 rule 처리
- brief change stale

Legal Report는 구현하지 않는다.
완료 후 Concept 입력 계약을 보고하고 멈춘다.
```

---

# Prompt G5 — Concept Core

```text
[대화형 사업검증 워크스페이스 G5 — Concept Core, Slot Isolation, Deterministic Trace]

목적:
현재 single-candidate fan-out을 폐기하지 않고 독립 Slot 오케스트레이션과 구현 가능성 검증을 완성한다.

먼저 실제 현재 journey_provider.py와 ConceptJourneyService를 대조한다.

AI Concept 출력:
- concept skeleton
- actor roles
- transaction flow
- data flow
- physical activities
- partner requirements
- legal implementation hypothesis

AI에서 제거:
- sourceValue 원본 복사
- 정식 legalTrace
- 법률 원문 재출력

시스템 생성:
- origin source trace
- boundary rule trace
- evidence linkage

Slot:
- gather return_exceptions 또는 동등한 격리
- VALID / SCHEMA_INVALID / TRANSIENT_PROVIDER_FAILURE / PERMANENT_PROVIDER_FAILURE
- 정상 Slot 보존
- initial + repair 또는 retry 중 하나
- concurrency 1 기준선 테스트

Legal states:
IMPLEMENTABLE
IMPLEMENTABLE_WITH_CONTROLS
REDESIGN_REQUIRED
INSUFFICIENT_INFORMATION
HARD_BLOCK

Orchestrator:
- redesign 1회
- hard block 폐기
- 대체 생성
- 적격 3개
- needs input 구분

테스트에 실환경 혼합 실패 패턴을 포함한다.
Backend/Frontend 정책은 이번 단계에서 최소 변경.
완료 후 Docker/OpenAI 재현 순서까지 보고하고 멈춘다.
```

---

# Prompt G6 — Concept Workboard

```text
[대화형 사업검증 워크스페이스 G6 — Async Concept Workboard]

목적:
Confirmed Brief/Boundary 요약과 3개 Concept Slot의 실제 비동기 진행을 표시한다.

UI:
- left 30 summary / right 70 board
- slot focus, state, safe message, expandable timeline
- READY 전 상세 숨김
- REDESIGNING, NEEDS_INPUT, FAILED actions
- 3 READY 후 cards reveal
- legal implementability, controls, partners, prohibited variants
- stale banner

데이터:
- backend slot/query/event API가 부족하면 additive 구현
- polling-only 반복 요청 최소화

타이포그래피:
- project/journey title 유지
- section 15~16
- card 14~15
- body 13~14
- meta 11.5~12.5

테스트:
- async ordering
- refresh restore
- failed draft hidden
- 3 ready gate
- accessibility

Quick Assessment는 연결하지 않는다.
완료 후 멈춘다.
```

---

# Prompt G7 — Quick Assessment & Selection

```text
[대화형 사업검증 워크스페이스 G7 — Quick Assessment v2 & Selection]

목적:
신규 Concept 모델을 Legacy 문자열 변환 없이 직접 평가하고 사용자가 1~2개를 선택한다.

평가:
problemFit, customerValue, differentiation, executionFeasibility, revenuePotential,
testability, legalFeasibility, complianceComplexity, partnerDependency, assumptionUncertainty

작업:
- v2 AI/API contract
- current eligible concept versions only
- strengths/risks/evidence/validation tasks
- no auto selection
- selection reason
- selection version and hash
- stale guard

제거/금지:
- problem=originTrace JSON
- risks=[]
- legacy mapping으로 신규 평가 실행

테스트와 fixture를 갱신한다.
Enrichment는 구현하지 않는다.
완료 후 멈춘다.
```

---

# Prompt G8 — Selected Concept Enrichment

```text
[대화형 사업검증 워크스페이스 G8 — Selected Concept Enrichment]

목적:
선택 Concept만 상세화하고 각 값의 provenance/confirmation을 관리한다.

필드:
featureArchitecture, userJourney, actorResponsibilities, transactionFlow,
paymentRefundFlow, dataLifecycle, physicalOperations, channels,
pricingHypotheses, revenueFlow, costDrivers, metrics, experimentPlan

상태:
USER_CONFIRMED, SOURCE_EXTRACTED, AI_PROPOSED, DEFAULT_ASSUMPTION, MISSING

법률 민감 변경 diff:
role, payment actor, delivery/collection actor, data purpose/retention,
region, regulated type, advertising claims

Frontend:
- grouped sections
- compact typography
- proposed/confirmed/missing badges
- user confirmation

테스트:
- no auto confirm
- sensitive diff
- non-sensitive edit
- version/stale

Legal Report는 구현하지 않는다.
완료 후 멈춘다.
```

---

# Prompt G9 — Legal Report

```text
[대화형 사업검증 워크스페이스 G9 — Versioned Legal & Regulatory Report]

목적:
Concept 생성 단계의 Legal Feasibility Profile과 Evidence를 사용해 설명·이행 중심 보고서를 발행한다.

참고:
junwoooooooo/aivle_big_project junwoo branch의 legal-review UX를 분석한 기존 설계 문서

구현:
- report version + publication snapshot
- purpose/scope
- concept facts
- overall status
- categories/evidence
- 5-step reasoning chain
- required controls
- pre-launch checklist
- conditional obligations
- questions/confirmed facts
- professional review
- disclaimer
- revision requests/suggestions
- incremental affected-category review
- converged/publish

중요:
- 새 PASS/FAIL Gate 금지
- sensitive change가 없으면 기존 profile로 보고서 구성
- sensitive change가 있으면 영향 범주만 재검사
- publication immutable

Frontend:
- overview + formal report tabs
- easy explanation first, excerpt collapsed
- font density guideline

테스트:
- source hash
- evidence chain
- suggestion version
- incremental diff
- publication preservation

완료 후 멈춘다.
```

---

# Prompt G10 — Analysis Readiness

```text
[대화형 사업검증 워크스페이스 G10 — Analysis Input Readiness]

목적:
후속 분석별 필수 입력과 provenance를 평가해 READY/NEEDS_CONFIRMATION/MISSING/BLOCKED를 표시한다.

분석:
- Quick Assessment
- Market
- Business Model
- Technology & Operations
- Financial
- Persona
- User Validation
- Legal Report

작업:
- requirement catalog
- readiness policy
- snapshot
- API/UI
- user confirmation forms
- legacy MVP route adapters

금지:
- missing number = 0
- AI proposed = confirmed
- all ready 문구 과장

테스트:
- targeted readiness change
- upstream stale
- financial missing
- source/confirmation badges

완료 후 멈춘다.
```

---

# Prompt G11 — Full E2E, Cutover, Documentation

```text
[대화형 사업검증 워크스페이스 G11 — End-to-End Cutover]

목적:
신규 Journey를 Docker 실제 스택에서 끝까지 검증하고 공식 경로를 안전하게 전환한다.

E2E 필수:
1. short text path
2. attachment path
3. boundary blocked and revise
4. mixed slot failure isolation
5. redesign and replacement
6. SSE reconnect
7. stale cascade
8. sensitive legal incremental review
9. legal report publication
10. analysis readiness financial missing
11. auth/project isolation

Cutover:
- feature flag default
- route compatibility
- old journey retained or redirected
- docs/readme/local run/current baseline/architecture
- CI jobs
- rollback

전체 검증:
frontend ci/lint/baseline/build
AI full tests + fixtures
backend clean test postgresTest
optional minioTest
Docker smoke
manual UI walkthrough
security log scan
git diff --check

완료 보고:
- exact SHA before/after
- migrations
- APIs
- screenshots/flow evidence
- test counts
- remaining limitations
- rollback procedure

commit/push하지 않고 멈춘다.
```
