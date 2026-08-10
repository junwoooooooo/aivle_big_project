# Concept Portfolio V2 Production Cutover Spec

## 1. P0 상태

- 기준 브랜치: `rebuild/new-pipeline-v1`
- 기준 HEAD: `9c423aa 구축 Concept Portfolio Engine V2`
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

Provider, MOLEG, Docker, browser, 전체 test, 전체 build는 실행하지 않는다.

