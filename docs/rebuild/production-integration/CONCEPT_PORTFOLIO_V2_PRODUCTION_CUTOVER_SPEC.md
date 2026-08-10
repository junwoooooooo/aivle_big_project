# Concept Portfolio V2 Production Cutover Spec

## 1. P0 상태

- 기준 브랜치: `rebuild/new-pipeline-v1`
- P0 audit base HEAD: `9c423aa 구축 Concept Portfolio Engine V2`
- 적용 정본: `docs/rebuild/CONCEPT_PORTFOLIO_V2_PRODUCTION_CUTOVER_AMENDMENT_v1.0.md`
- 현재 단계: P0 감사 완료
- Product 구현: 시작하지 않음
- Core 알고리즘 변경: 없음
- LIVE Provider/MOLEG 실행: 없음

기존 노트북과 recording의 사용자 변경은 감사 대상과 겹치지 않아 그대로 보존했다.

## 2. Cutover 정본

1. Confirmed Idea Brief Snapshot을 V2 canonical seed로 변환한다.
2. `ConceptPortfolioEngine.run_full(seed, max_concepts=5, auto_confirm_hypotheses=False)`를 durable TaskRun에서 한 번만 실행한다.
3. `maxConcepts=5`는 최대치이며 Legal-accepted Concept 1~5개는 정상 결과다.
4. Product 상태는 Engine raw 상태와 분리한다. 유효 결과가 있으면 `RESULTS_AVAILABLE` 또는 `RESULTS_WITH_OPEN_INPUT`, 결과 없이 실제 사용자 사실이 필요하면 `NEEDS_INPUT`, 기술 실패만 `FAILED`다.
5. Engine의 `selectedConceptId`는 저장하되 사용자 선택으로 해석하지 않는다. 사용자 선택은 명시적 Selection API와 DB row만 정본이다.
6. Candidate 단위 입력 대기는 다른 적격 Concept의 공개·비교·선택을 차단하지 않는다.
7. 사용자 답변은 새 Input Response와 새 continuation TaskRun/Job을 만들고 동일 Candidate lineage를 이어간다.
8. 최종 Market 입력은 Confirmed Idea Brief, 명시적 선택, 확정 7개 가설, Delta Legal, 최종 Legal 결과와 Official Evidence를 포함한 immutable `MarketAnalysisSeedSnapshot`이다.

## 3. 현재 Production 흐름 감사 결과

현재 공식 경로는 V2 전체 엔진이 아니라 Backend의 5 Slot Concept Factory다.

`IdeaBrief CONFIRMED → ConceptFactoryService → CONCEPT_FACTORY_RUN TaskRun → ConceptFactoryWorker → Slot별 Candidate/Distinctness/Legal AI 호출 → 정확히 5개 publish → ConceptSelection → Hypothesis/Delta Legal → MarketAnalysisSeedSnapshot`

Python에는 Notebook과 같은 V2 엔진을 호출하는 `ai/app/tasks/concept_portfolio_v2/service.py`가 이미 있으나, `ai/app/api/executions.py`의 `TASK_TYPES`와 dispatcher에 등록되지 않았다. 따라서 현재 Backend TaskRun은 이 entrypoint를 호출할 수 없다.

## 4. P0 필수 질문 답변

### 4.1 V2 Production 경로는 어디에 들어가는가

- AI: 기존 `ai/app/tasks/concept_portfolio_v2/`를 Production Integration 경계로 사용한다. 이 패키지에 thin facade, continuation DTO, read-only trace observer를 둔다.
- AI dispatcher: `ai/app/api/executions.py`에 전용 task type `CONCEPT_PORTFOLIO_V2_RUN`과 dispatch를 추가한다.
- Backend: 기존 `pipeline/concept` Slot 실행을 확장하지 않고 새 `pipeline/conceptportfolio` 패키지에서 Run/API/worker/persistence를 시작한다.
- Core: `ai/app/concept_portfolio_v2/**`는 호출 대상일 뿐 Product orchestration을 넣지 않는다.

### 4.2 완전히 대체할 기존 Concept Factory execution

다음 실행 authority는 V2 Run cutover 후 비활성화한다.

- `ConceptFactoryController`의 `/concept-factory-runs` 공식 실행 API
- `ConceptFactoryService`의 5 Slot 생성과 retry
- `ConceptFactoryExecutionService`
- `ConceptFactoryWorker`의 Slot별 Candidate/Distinctness/Legal orchestration
- `ConceptFactoryAiGateway`
- `ConceptFactoryLimits.SLOT_COUNT=5`와 `ConceptFactoryCompletionPolicy`

과거 데이터 조회가 필요하면 read-only adapter만 허용한다. 새 사용자 action이 old engine과 V2를 동시에 실행해서는 안 된다.

### 4.3 그대로 살릴 TaskRun/SSE 기반

다음은 KEEP이다.

- `TaskRun`, `TaskAttempt`, `TaskResult`의 create/claim/adopt/fail/needs-input/immutable terminal history
- idempotency와 동일 입력 active-run 중복 방지
- heartbeat, lease-expiry recovery, late/duplicate result rejection
- `JobEvent` persistence, sequence, payload policy, ownership 검사
- job 단위 SSE의 `Last-Event-ID` replay와 polling fallback
- Frontend `useJobEvents`, authenticated SSE client, bounded reconnect/polling backoff

단 Project-level event stream은 현재 없으며 P7에서 추가해야 한다.

### 4.4 5개/Slot gate의 실제 위치

- Backend: `ConceptFactoryLimits.SLOT_COUNT`, `ConceptFactoryCompletionPolicy`, `ConceptFactoryWorker`의 Slot loop와 완료 event
- Backend query: `ConceptFactoryService.publicConcepts()`는 Run이 `COMPLETED`가 아니면 빈 결과를 반환한다.
- DB: `concept_slots.slot_number BETWEEN 1 AND 5`, Run/Slot/Concept의 1:1 Slot 제약
- Frontend factory: `evaluateRevealGate()`의 `slots.length !== 5`, `concepts.length !== 5`
- Frontend selection: `ConceptComparisonPage`의 `models.length !== 5`
- Frontend comparison: `MAX_COMPARE_COUNT=5`, 2~5개 비교 문구
- 사용자 copy/event: “5개 컨셉 만들기”, “5개 모두 준비”, `job.concept.run.completed`

### 4.5 Candidate continuation에 현재 부족한 것

V2 결과에는 `requiredInputs`와 제한된 `unresolvedCandidates`가 있지만 동일 Candidate를 복원하기 위한 안정된 공개 DTO가 없다. 특히 CandidateEnvelope, Plan/descriptor, canonical seed reference, 최신 LegalReview, affected fields, accepted Concept IDs, parent/recovery 정보와 canonical hash를 묶은 continuation snapshot이 부족하다.

Product 측에는 다음이 새로 필요하다.

- `ConceptInputRequest`, `ConceptInputResponse`, `ConceptPortfolioContinuation` persistence
- raw question과 한국어 사용자 질문을 분리하는 presentation contract
- Candidate/lineage/targetFields에 제한된 USER_CONFIRMED patch
- 새 continuation TaskRun과 worker
- continuation 결과의 idempotent merge와 InputRequest resolve
- Candidate-local 입력과 global/system failure를 구분하는 Product status mapper

과거 `NEEDS_INPUT` TaskRun/Job은 다시 실행하지 않는다.

### 4.6 선택 → Hypothesis → Delta Legal → Market Seed 재사용 범위

- 명시적 `POST /api/v3/projects/{projectId}/concept-selections`와 current selection row는 재사용 가능하다.
- 7개 `HypothesisType`, LOCK 보호, accept/edit/alternative, async Delta Legal과 stale-result guard는 재사용 가능하다.
- `MarketAnalysisSeedSnapshotService/Factory`는 Confirmed Idea Brief, 선택 Concept, 확정 가설, Legal 결과, Delta 결과, Official Evidence를 이미 결합하므로 핵심 materialization 로직을 재사용한다.
- 현재 Selection·Hypothesis·Market Seed가 legacy `Concept` FK와 published-completed Run에 결합되어 있어 새 Portfolio Concept에 맞춘 repository/entity adapter 또는 FK migration이 필요하다.
- 독립적인 최종 Legal Regulatory Report resource, CURRENT/STALE 상태와 print view는 현재 없다.
- selection/hypothesis 변경에 따른 Legal Report·Market Seed stale 전파도 보강해야 한다.

### 4.7 장시간 `run_full` 위험

- AI Server RestClient 기본 read timeout은 30초다.
- 기존 Concept Factory parent lease/timeout은 각각 10분/30분이지만 heartbeat는 Slot 사이에서만 호출된다.
- V2 `run_full()`을 하나의 blocking AI 호출로 연결하면 실행 중 heartbeat가 없어 lease expiry/recovery 중복 실행 위험이 있다.
- `TaskAttempt.deadlineAt`은 completion 시 강제되므로 5분 이상 실측과 맞는 전용 configurable runtime이 필요하다.

따라서 V2 worker에는 전용 장기 timeout, 실행 중 주기적 heartbeat/lease renewal, 동일 Run 중복 claim 방지와 idempotent completion이 필요하다. 공통 30초 AI client 설정을 무작정 전역 확대하지 않는다.

### 4.8 수동 refresh 의존 위치

- Project Module Status hook은 mount 또는 사용자의 `retry()` 때만 조회한다.
- Job Center는 선택된 job 하나에만 SSE를 연결한다.
- 선택된 job의 terminal event에서만 Job Center와 module status가 갱신된다.
- 다른 background job, Portfolio/InputRequest/Selection/Legal Report/Market Seed 변경을 프로젝트 전체에 알리는 SSE endpoint가 없다.
- Concept Factory와 Job Center에 정상 흐름용 수동 새로고침 버튼이 남아 있다.
- Market Integration은 mount와 수동 retry 외 실시간 invalidation이 없다.

P7에서는 project-level event stream을 신호로 사용하고, 이벤트 수신 후 관련 Query API를 재조회해야 한다.

### 4.9 새 UI에서 삭제 가능한 Legacy

- Slot 카드와 Slot 번호 중심 진행판
- 정확히 5개 reveal gate와 “5개 미만 실패” copy
- Slot별 재생성·대체를 사용자 개념으로 노출하는 화면
- Slot filter 기반 `ConceptTimeline`
- 5개 전용 비교 진입 gate와 최대 5개 비교 선택
- Concept 생성과 비교를 별도 큰 Journey 단계로 보여주는 기존 module 표현
- 최종 법률 결과를 `LegalDetailDialog` 하나로 끝내는 흐름

라우트 `/concepts`와 `/concepts/compare`는 전환 중 URL 호환을 위해 유지할 수 있으나 새 Business Proposal Workspace를 렌더링해야 한다.

### 4.10 P1 최소 파일 집합

P1은 AI Integration seam만 다룬다.

1. `ai/app/tasks/concept_portfolio_v2/service.py` — thin Production facade와 canonical `run_full()` 호출 정리
2. `ai/app/tasks/concept_portfolio_v2/models.py` — Production result와 continuation snapshot DTO 신규
3. `ai/app/tasks/concept_portfolio_v2/observer.py` — read-only TraceEvent sink 신규
4. `ai/app/api/executions.py` — `CONCEPT_PORTFOLIO_V2_RUN` registry/dispatch 추가
5. `ai/tests/tasks/test_concept_portfolio_v2_production.py` — facade, selection 비권위, continuation export, trace fan-out targeted test 신규

`ai/app/concept_portfolio_v2/**`의 알고리즘 파일은 P1 수정 대상이 아니다. Backend migration/API/worker는 각각 P2/P3에서 진행한다.

## 5. P0 이후 구현 순서

P1 AI facade/continuation/trace → P2 additive persistence → P3 durable orchestration/API → P4 candidate continuation → P5 selection/hypothesis/Delta adapter → P6 Legal Report/Market Seed → P7 project live sync → P8/P9 UI cutover → P10 legacy retirement → P11 cutover gate 순서를 유지한다.

## 6. P0 검증 범위

- branch와 HEAD 확인
- Amendment/AGENTS path 확인
- AI/Backend/Frontend/DB 영향 경로 static grep
- `git diff --check`

## Cutover Bundle 4 implementation status — PASS

- P5+P6 hotfix: Delta Legal 결과의 canonical `deltaLegalResult` 경로를 BUILD_HANDOFF까지 유지한다. V13은 Delta review에 `hypothesis_revision`을 추가하며, Delta 승인 자체는 가설 값 revision을 올리지 않는다. Market Seed에는 현재 revision의 최신 approved Delta 한 건만 적용하고 전체 Delta history는 Final Legal Report의 감사 이력으로 보존한다.
- Active action: Selection에 `activeTaskRunId`가 있으면 상태와 무관하게 `nextAction=WAIT`이다. 같은 Selection의 동시 요청은 기존 `ANALYSIS_ALREADY_RUNNING` Business error로 정규화한다.
- Project live sync: `GET /api/v2/projects/{projectId}/events`는 SSE, `?after={cursor}`는 replay/polling이다. Project cursor는 `job_events.id`이며 terminal job event는 project stream을 닫지 않는다. 이벤트는 invalidation 신호이고 Product state 정본은 REST query다.
- Frontend invalidation: `ProjectLayout`에서 project stream을 한 번 구독한다. event revision이 바뀌면 module status, Work Center, Portfolio/Selection/Report/Market Seed query가 canonical REST를 다시 읽는다.
- Business Proposal UI: 공식 `/concepts`와 `/concepts/compare`는 동일 Workspace의 목록/비교 모드다. 1~5개를 정상 표시하고 빈 Slot을 만들지 않으며, 직접 선택과 2~3개 비교를 지원한다. OPEN Candidate input은 다른 ACCEPT 사업안의 선택을 막지 않는다.
- Selection/Legal/Market: V2 Selection API만 사용자 선택 authority로 사용한다. 정확히 7개 검증 가정, 실제 async Delta 대기, server Final Legal Report, READY_FOR_MARKET 및 V2 Market Seed를 렌더링한다. V2 Seed는 legacy Concept/Selection으로 변환하지 않는다.
- Journey/Work Center: 아이디어 → 사업안 → 시장 분석 → 사업 모델 → 기술·운영 → 재무 → 마케팅으로 정리했다. 우측 compact Work Center와 실제 JobEvent 기반 drawer를 사용하며 가짜 진행률은 만들지 않는다.
- Legacy: 기존 legacy 소스는 삭제하지 않았지만 새 공식 route는 legacy execution/UI를 호출하지 않는다. 물리 삭제는 Final Cutover Gate에서 caller 0을 재확인한 뒤 수행한다.
- 검증: AI/Backend/Frontend targeted test, Backend `compileJava`, Frontend production build, `git diff --check`, Frozen Core diff를 실행한다. LIVE/전체 regression/browser E2E는 제외한다.

## Final Cutover Hardening implementation status — READY

- Candidate input: P4 정본의 8개 field만 허용한다. 단일 `affectedFields`는 고정하고, 다중 또는 미지정 target은 사용자가 선택한다. role field는 string, flow/requirements/usage/activities field는 빈 항목을 제거한 `list[string]`으로 전송한다.
- Legal Report: Frontend section은 Backend의 실제 `finalLegalConclusion`, transaction/payment, partner/qualification, privacy, control/disclosure, prohibited/advertising, evidence, Delta history, source hash key만 렌더링한다.
- Hypothesis provenance: 서버 original 값과 실제 편집값을 비교하여 변경된 unlocked hypothesis만 `changes`에 포함하고 `confirmAll=true`를 유지한다. `ACCEPTED`와 `USER_EDITED_ACCEPTED`는 확인됨으로 표시한다.
- Recovery: terminal Run은 새 idempotency key의 새 V2 Run으로 다시 시작한다. ANSWERED continuation failure와 Delta Legal failure는 기존 retry resource를 사용하며 terminal TaskRun을 되살리지 않는다.
- Live UX: Market page도 project event revision에 따라 Selection, V2 Seed, module runs와 result를 REST에서 다시 읽는다. 중복 project event ID는 revision을 증가시키지 않는다.
- Work Center: V2 TaskType과 실제 Backend message key를 사용자 용어로 변환한다. 상세 진행은 실제 QUEUED/RUNNING/AI_EXECUTING/MATERIALIZING/terminal event만 사용한다. Cross-process Core trace는 post-cutover enhancement backlog다.
- Helper/notice: floating helper는 현재 canonical module 상태와 next action 안내를 연다. recovered notice는 client selection baseline 이후 실제 concept ID가 추가된 경우에만 표시한다.
- Legacy audit: 공식 `/concepts`와 `/concepts/compare` route에서 legacy Factory/Comparison hook/API caller는 0이다. legacy 소스와 schema는 사용자 실검증 전까지 보존한다.

Provider, MOLEG, Docker, browser, 전체 test, 전체 build는 실행하지 않는다.

## 7. P1 implementation status — FINAL PASS

- Continuation 계약: Canonical Seed와 `DesignSpaceAnalysis`는 shared `ConceptPortfolioContinuationContext`에 한 번만 저장하고, unresolved Candidate가 참조하는 실제 `PortfolioPlan` snapshot을 `planId`별로 deduplicate한다. Candidate별 Artifact는 CandidateEnvelope, 최신 LegalReview, strict required input, affected fields, lineage/parent/recovery 정보와 accepted Portfolio ID를 보존한다.
- Context 관측: read-only observer가 `analyze_seed()`, `expand_plan()`, `review_legal_candidate()`의 public method boundary에서 Design, 실제 Plan, Candidate를 deep copy한다. Core private `_last_*`를 export하지 않으며 새 run에서 캡처 상태를 모두 reset한다.
- Product 선택 authority: Core의 `selectedConceptId`는 `engineDefaultConceptId`로만 반환하고 `userSelectedConceptId`는 항상 `null`이다. Core `RunSummary` 대신 selection 필드가 없는 bounded `ProductionRunSummary`를 사용한다.
- 응답·오류 경계: 전체 raw trace와 diagnostics/provider usage는 반환하지 않는다. 5 Candidate NEEDS_INPUT와 5 Artifact에 가까운 최악 fixture도 1.5 MiB safety target 이내이며, materialization 계약 오류는 기존 Internal Execution envelope의 `RESULT_SCHEMA_INVALID / AI_RESULT_INVALID`, non-retryable로 정규화한다.
- Trace 경계: Observer seam은 준비되었으나 Python process 내부 callback이다. Cross-process AI progress transport는 P2/P3 이후 Backend integration 과제이며, 연결 전에는 임의의 시간 기반 상세 진행 이벤트를 만들지 않는다.
- 확인: P1 표적 테스트 23개, 대상 package compile, Core diff 확인, `git diff --check`를 수행한다. Provider/MOLEG LIVE, 전체 AI test/build, Backend·DB·Frontend 변경은 수행하지 않았다.
- 다음 시작점: P2 additive persistence와 P3 Backend TaskType/worker, 장시간 timeout·lease·heartbeat 및 실제 trace event transport다.

## 8. P2+P3 implementation status — PASS

- Additive persistence: 기존 migration을 수정하지 않고 V10에 Portfolio Run, Concept, shared Continuation Context, InputRequest, InputResponse 다섯 resource를 추가했다. 1~5개 결과와 Candidate lineage를 보존하며 Slot/정확히 5개 gate는 새 schema에 넣지 않았다.
- 공식 실행 경로: Confirmed Idea Brief의 정본 field·provenance·interpretation을 해시 가능한 seed로 구성하고, `POST /api/v3/projects/{projectId}/concept-portfolio-runs` 및 current/run/concepts 조회 API를 독립 `pipeline/conceptportfolio` package에 추가했다. 미확정·stale source, ownership, active run, idempotency 충돌을 시작 전에 차단한다.
- Durable execution: Backend `TaskType.CONCEPT_PORTFOLIO_V2_RUN`이 P1 Internal AI dispatcher를 호출한다. 일반 AI 호출의 30초 timeout은 유지하고 V2에만 기본 15분 read timeout을 적용한다. bounded executor 위 blocking future를 20초 기본 주기로 heartbeat하며, 90초 lease·20분 task deadline과 만료 attempt 재큐잉을 사용한다.
- 원자 materialization: active claim authority와 input hash를 다시 확인한 동일 transaction에서 TaskRun terminal 결과와 Portfolio Run/Concept/Continuation/InputRequest를 저장한다. late/stale worker는 Product row와 terminal event를 만들 수 없다.
- Product 상태: 1~5 ACCEPT는 정상 결과이며 Candidate-local open input은 다른 ACCEPT 결과를 막지 않는다. 0 ACCEPT와 actionable input은 `NEEDS_INPUT`, 실제 system/provider/contract failure는 `FAILED`로 분리한다. Engine default candidate는 비권위 정보일 뿐이며 selection row는 생성하지 않는다.
- Event 경계: 실제 Backend lifecycle에 근거한 queued/running/heartbeat/terminal JobEvent만 사용한다. P1 observer의 상세 trace는 아직 Python process 내부 seam이므로 cross-process progress transport는 후속 integration 과제이며 시간 기반 가짜 단계 event는 만들지 않는다.
- 확인: CPV2 migration/seed/API/materialization/status/worker와 AI client routing 표적 테스트, 기존 Internal AI client 직접 영향 테스트 및 Java compile을 통과했다. Provider/MOLEG LIVE, Docker/Testcontainers, Backend 전체 테스트, Frontend 작업은 수행하지 않았다.
- 다음 시작점: P4 Candidate 추가정보 응답·새 continuation TaskRun·동일 lineage merge다. P2+P3에서는 InputResponse schema만 준비했으며 continuation API/worker는 시작하지 않았다.

## 9. P4 implementation status — PASS

- P2/P3 hardening: Worker deadline은 UTC `Clock` 기준으로 계산하며 기본 AI deadline 14분, V2 read timeout 15분, Task timeout 20분 순서를 강제한다. Engine `FAILED`는 0 Concept와 actionable Candidate input이 있고 failureCode가 없는 경우만 `NEEDS_INPUT`으로 해석하며, 그 외 FAILED 결과에서는 Concept/InputRequest를 신규 materialize하지 않는다.
- AI continuation: `CONCEPT_PORTFOLIO_V2_CONTINUE` strict task 계약을 추가했다. 저장된 Canonical Seed hash와 `DesignSpaceAnalysis`를 공개 `analyze_seed()`로 복원·대조하고 shared Context의 실제 `PortfolioPlan`을 사용한다. 사용자 `confirmedFacts`는 허용된 8개 business fact field에만 `USER_INPUT / LOCKED / ACCEPTED` semantics로 patch한 후 descriptor를 재생성한다.
- Candidate-only 실행: 전체 `run_full()`이나 replan을 호출하지 않고 `max_replans=0`인 fresh engine에서 해당 Candidate의 `validate_candidates()`와 `review_legal_candidate()`만 실행한다. 기존 accepted Candidate snapshot은 comparison context로 전달한다.
- 사용자 API: InputRequest 목록 조회, Candidate response 제출, technical failure retry API를 추가했다. Browser는 `confirmedFacts`, idempotency key, 선택적 note만 제출하며 Context/Artifact/Plan/Legal snapshot은 Backend DB에서 조립한다. GLOBAL input은 Candidate continuation으로 처리하지 않는다.
- Durable continuation: 답변은 `concept_input_responses`에 저장하고 기존 terminal TaskRun을 변경하지 않은 채 같은 Portfolio subject의 새 TaskRun/Job을 만든다. initial/active Task ID를 Run API에서 구분하며 continuation도 V2 long-read client, shared bounded executor, heartbeat와 lease recovery를 사용한다.
- 원자 merge: active claim 확인, TaskRun terminal, 이전 InputRequest resolve, ACCEPT Concept merge 또는 후속 OPEN InputRequest 생성, Run count/status 갱신을 한 transaction에서 처리한다. V11은 `(run_id, lineage_id)` uniqueness만 additive하게 추가했다.
- 결과 의미: `ACCEPTED`는 기존 Concept를 보존하며 다음 display order로 추가하고, `NEEDS_INPUT`은 과거 질문을 resolve한 뒤 새 질문을 만든다. `EXCLUDED`는 Candidate-local 종료이며 0 Concept이면 `NO_ACCEPTED_CONCEPTS`; `SYSTEM_FAILURE`는 기존 Portfolio와 ANSWERED response를 보존해 retry할 수 있다.
- Job actionability: 과거 immutable NEEDS_INPUT Job은 동일 subject의 latest TaskRun과 실제 OPEN InputRequest를 함께 확인해 actionable 여부를 결정한다. 상세 Core trace transport 없이 실제 queued/running/AI executing/materializing/terminal event만 발행한다.
- 확인: P4 AI task-layer와 P1 직접 영향 테스트, CPV2 Backend API/domain/materialization/worker, long-read routing, Job query 표적 테스트 및 compile/static gate를 수행했다. Provider/MOLEG LIVE, Docker/Testcontainers, 전체 regression, Frontend는 실행하지 않았다.
- 다음 시작점: 별도 지시의 P5+P6 명시적 Portfolio Concept selection, 7 Hypothesis, Delta Legal, 최종 Legal Report, Market Analysis Seed Snapshot이다.

## 10. P5+P6 implementation status — PASS

- 선택 authority: V12의 `concept_portfolio_selections`가 V2 사용자의 유일한 명시적 선택 정본이다. Engine `selectedConceptId`/`engineDefaultConceptId`, 첫 Candidate, display order와 legacy `concept_selections`는 선택 authority가 아니다. 1개 Concept도 직접 선택할 수 있고 `RESULTS_WITH_OPEN_INPUT`의 다른 Candidate 입력 대기는 선택을 막지 않는다.
- 7개 검증 가정: 새 `concept_portfolio_hypothesis_decisions`는 `TARGET_REGION`, `REVENUE_MODEL`, `PRICE`, `CHANNELS`, `DIFFERENTIATORS`, `PRE_MARKET_SOM_SHARE`, `PRE_MARKET_SOM`만 허용한다. AI task-layer는 Frozen Core의 build/semantic resolve/confirm API를 호출하며 `confirmAll=true`의 명시적 사용자 action 전에는 자동 확정하지 않는다. LOCKED 값은 유지하고 invalid/unresolved 편집은 미확정으로 남긴다.
- 단일 action seam: `CONCEPT_PORTFOLIO_V2_SELECTION_ACTION` 하나가 `PREPARE_HYPOTHESES`, `CONFIRM_HYPOTHESES`, `PROPOSE_ALTERNATIVE`, `DELTA_LEGAL`, `BUILD_HANDOFF`를 strict enum으로 처리한다. 기존 bounded executor, V2 long-read client, UTC deadline, heartbeat, lease recovery를 재사용한다. active task/claim/current selection/expected hypothesis revision을 materialization 직전에 재검증해 늦은 결과를 차단한다.
- Delta Legal: 사용자 편집 중 법률 민감 5개 type만 Core 규칙에 따라 `deltaLegalRequired`가 될 수 있다. Frozen `review_delta_legal()`과 `mark_delta_legal_reviewed()`를 사용하며 approved 결과는 별도 history로 저장한다. domain/system failure 모두 확정 가정을 보존한 채 `DELTA_LEGAL_FAILED`로 종료하고 retry는 새 TaskRun을 만든다.
- 최종 법률·규제 보고서: `concept_legal_regulatory_reports`는 current selected Portfolio Concept, latest ACCEPT base LegalReview, confirmed/VALID 7개 가정, approved Delta history와 Official Evidence만 immutable JSON으로 materialize한다. 새 법률 판단을 생성하지 않으며 CURRENT/STALE history를 유지한다.
- Market Seed bridge: 기존 `market_analysis_seed_snapshots`에 `source_type`, Portfolio Selection/Concept/Legal Report FK와 `stale_at`을 additive하게 추가했다. DB CHECK는 LEGACY와 `CONCEPT_PORTFOLIO_V2` authority가 한 row에 섞이는 것을 금지한다. V2 row는 legacy selection/concept가 NULL이며 fake legacy row를 만들지 않는다.
- canonical handoff: Frozen `CurrentDownstreamAdapter`의 `market-analysis-seed-snapshot-v1`, schema `2.0`을 유지한다. task-layer가 Lab placeholder를 실제 project/Portfolio selection/Product concept/snapshot ID로 바인딩하고 source/snapshot SHA-256을 다시 계산한다. CURRENT Legal Report와 PASS compatibility가 Market Seed finalize gate다.
- stale 전파: 사용자 selection 변경 또는 hypothesis/alternative 변경 시 기존 Report는 STALE, V2 Market Seed는 `stale_at`으로 보존한다. 새 recovered Concept가 추가되어도 current selection은 자동 변경하지 않는다. 실행 중 과거 selection action은 best-effort cancel하고 늦은 결과는 새 current selection을 수정할 수 없다.
- Trace 경계: P1 Observer는 여전히 Python process 내부 seam이다. Cross-process 상세 trace transport와 Project-level SSE는 후속 UI/live-sync 단계 과제이며, 연결 전 Backend는 시간 기반 가짜 중간 단계를 생성하지 않는다.
- 확인: P5+P6 AI task-layer targeted test, V12/domain/Market bridge Backend targeted test, P4 continuation 직접 영향 test, Python compile, Java compile과 static gate를 실행했다. Provider/MOLEG LIVE, Docker/Testcontainers, 전체 regression, Frontend는 실행하지 않았다.
- 다음 시작점: 별도 지시의 Production UI Cutover + Project Live Sync다. 이번 단계에서는 Market Analysis 실제 실행, Frontend, legacy retirement를 시작하지 않는다.

## 11. Browser Runtime Verification Findings

- Work Center의 compact 목록과 상세 drawer가 서로 다른 렌더 경계를 사용해 실제 선택 작업의 event log가 열리지 않았고, mount/replay/manual refresh를 모두 새 상태처럼 알리는 비정본 notice가 있었다.
- Idea Brief 확정 직후 module status query가 즉시 갱신되지 않았고, 다음 공식 단계인 사업안 검토로 이동하는 명시적 CTA가 부족했다.
- P1 observer는 Python process 내부 hook까지만 존재해 Core trace가 Backend `JobEvent`와 browser SSE까지 전달되지 않았다.
- Candidate 추가정보 화면은 질문의 대상 사업안·요약·이유를 충분히 보여주지 못했고, `affectedFields`가 비어 있으면 허용 8개 field 전체를 임의 선택지로 제시했다.
- 비교 화면, 구조화 SOM, 최종 Legal Report가 저장된 정본을 사용자용 구조로 충분히 투영하지 못했다.
- `BUILD_HANDOFF` 502의 직접 원인은 Python production canonical hash가 숫자의 trailing zero/exponent와 Unicode NFC를 정규화하는 반면 Java 검증 hash는 Jackson 표기 그대로 계산한 cross-language hash 불일치였다. 구조화 SOM의 `240000000.0` 같은 값이 이 경로를 재현했다.
- C4 provenance 감사에서 `physicalActivities`는 선택된 `ConceptPortfolioConcept.candidateSnapshotJson.candidate`에서 Final Report로 deep-copy된다. Backend adapter의 기본값, 다른 Candidate, stale selection의 합성 경로는 없었다. 따라서 관찰된 비정상 배송/방문/설치 의미는 Provider가 만든 선택 Candidate snapshot의 semantic quality 이슈이며 Frozen Core/provider prompt 변경이 필요한 별도 품질 backlog다.

## 12. Runtime Remediation Status — IMPLEMENTATION READY

- Work Center는 `ProjectLayout`이 소유하는 단일 bottom sheet로 통합했다. 목록·상세는 같은 `useProjectJobs` 정본을 사용하며 backdrop/ESC/닫기, 240ms opening/closing phase, body scroll lock과 trigger focus 복귀를 지원한다. replay나 단순 선택으로 가짜 갱신 notice를 만들지 않는다.
- Idea Brief 확정은 module status를 다시 읽고 `/concepts`로 이동하는 `사업안 검토로 이동` CTA를 표시한다. 자동 redirect는 하지 않으며 프로젝트 목록은 window focus 때 canonical REST를 조용히 재조회한다.
- Initial CPV2 run은 기존 read-only observer의 event를 bounded in-process queue에 넣는다. 별도 async sender가 고정 internal endpoint `POST /internal/v1/ai/task-progress`로 전달하며 2초 timeout·bounded flush를 적용한다. callback 실패나 queue 포화는 경고만 남기고 Core 결과 authority를 깨지 않는다.
- Backend callback은 internal bearer token, TaskRun/correlation/current TaskAttempt/RUNNING authority를 검증한다. stale 또는 terminal attempt trace는 무시하고 기존 `JobEventPublisher`에 RUNNING event만 추가한다. trace는 terminal Product state authority가 아니며 기존 job/project SSE와 replay cursor를 그대로 사용한다.
- Trace UI는 실제 stage/action/reason을 coarse 사용자 문구로 매핑한다. raw prompt/provider payload/secret/내부 Candidate·lineage 용어와 가짜 percent는 전달하지 않는다. terminal materialization 직전에는 reviewed/prepared/needs-input/excluded 실제 집계 summary를 남긴다.
- Candidate InputRequest projection은 대상 사업안 이름·한 줄 요약·실제 question/reason/safe summary를 제공한다. 비어 있는 `affectedFields`는 `INPUT_TARGET_UNRESOLVED`로 표현하고 Browser는 8개 전체 fallback이나 제출 UI를 만들지 않는다.
- 비교는 선택한 2~3개 사업안의 실제 Candidate/Legal field를 행 단위로 보여주며 4번째 추가를 차단한다. SOM 가정은 typed number/currency/period/basis/assumption control로 편집하고 raw JSON을 노출하지 않는다.
- Final Legal Report는 결론, 역할, 거래·결제 흐름, 개인정보·물리 활동, 파트너·자격, 통제·고지·금지, 광고 주의, 미확정 사실, 공식 근거 링크, Delta 이력을 사람용 section으로 표시한다. `SOURCE_PARTIAL`을 명시하고 source hash는 접힌 기술 정보에만 둔다.
- `BUILD_HANDOFF`의 Market Seed cross-language 검증에만 Python과 동일한 number/NFC canonical hash를 적용했다. 기존 저장 hash semantics는 변경하지 않았다. 실제 AI result → Backend materialization → `CONCEPT_PORTFOLIO_V2` Market Seed 저장 연결을 표적 테스트로 고정했다.
- 현재 Selection이 없는 정상 초기 조회는 `RESOURCE_NOT_FOUND`로 정규화해 validation 422로 오인되지 않게 했다. Project event burst는 180ms 창에서 한 번의 revision으로 합치고 duplicate/old event ID는 무시한다.
- DB migration V10~V13, Frozen Core 알고리즘, Market Analysis 알고리즘과 selection authority는 변경하지 않았다. Provider/MOLEG LIVE와 browser full E2E는 사용자 runtime 재검증 단계로 남긴다.
