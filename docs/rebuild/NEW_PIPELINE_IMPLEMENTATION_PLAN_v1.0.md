# NEW PIPELINE IMPLEMENTATION PLAN — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다. Unit은 순서대로 수행하되 한 요청에서 지정된 Unit만 실행한다.

## V2-0 — Authoritative Contract Reset

- Master, Product, Data/API, External Handoff, UI/UX, Implementation, Async 계약을 V2로 정렬
- 초기 required seed 3개와 optional LOCKED seed 고정
- AI_DERIVED와 AI_HYPOTHESIS 분리
- EXPLORE/REFINE/AS_IS, distinct eligible 5개 고정
- Concept-generated Legal Fact Pattern과 distinctness-before-legal 고정
- hypothesis-before-legal, selection-after-decision, Delta Legal 고정
- MarketAnalysisSeedSnapshot과 planning-change 제거 고정
- TechOps, Finance preparation, Marketing Source 고정

완료 Gate: 계약 문서 검색으로 구 계약이 active requirement로 남지 않았음을 확인하고 `git diff --check`를 통과한다. 제품 코드는 변경하지 않는다.

## V2-1 — Market Seed Intake and Interpretation

- `MarketAnalysisSeedDraft`와 required 3 fields
- optional LOCKED fields와 structured constraints
- Safety Review, AI Interpretation, interpretation confirmation
- Concept 탐색에 필요한 경우만 최소 follow-up
- source/authority 보존과 snapshot readiness
- 복잡한 초기 legal-required 질문 active path 제거

Targeted Gate: 3개 필드만으로 진행, optional 누락 허용, LOCKED 보존, safety block, editable interpretation, legal detail 누락으로 초기 needs-input 금지.

## V2-2 — ConceptCandidateV2 and Distinctness

- V2 schema와 source/authority
- EXPLORE/REFINE/AS_IS 및 AS_IS original Candidate 1
- AI hypothesis와 structured pre-market SOM
- operating/legal structure
- Prompt와 Backend의 LOCKED validation
- fingerprint, semantic distinctness, bounded replacement

Targeted Gate: minimal seed generation, LOCKED revenue/price preservation, missing value hypothesis, renamed-only/semantic duplicate rejection, target count replacement, SOM hypothesis labeling.

## V2-3 — Concept Legal Fact Pattern Integration

- complete Candidate → distinctness → Legal Fact Pattern → official-evidence Legal Review
- Concept-specific payment/platform/data/partner/qualification/claim facts
- legal-sensitive hypothesis 포함, SOM 제외
- exceptional external fact만 `NEEDS_FACTS`
- `REDESIGNABLE` 1회 후 schema/origin/distinctness/legal 재검사

Targeted Gate: hypothesis-before-legal, duplicate legal call 금지, redesign duplicate 실패 가능, Concept design gap을 사용자 질문으로 전가하지 않음.

## V2-4 — Concept Selection and Hypothesis Decision

- 적격 distinct 5개 비교 후 하나 선택
- `ConceptHypothesisDecision` persistence
- accept, edit-and-accept, reject/request-alternative
- LOCKED read-only
- legal-sensitive 변경만 Delta Legal Review

Targeted Gate: reject alternative, locked mutation 차단, revenue/claim 변경 Delta Review, SOM 변경 즉시 결정, 실패한 Delta Review acceptance 차단.

## V2-5 — Market Seed Snapshot and Handoff

- selection, required decisions, Delta Review, eligibility finalize gate
- immutable `MarketAnalysisSeedSnapshot`
- Market handoff 입력 전환
- Market Result contract와 external shell
- planning-change proposal/decision/FinalizedPlanning active workflow 제거

Targeted Gate: hash/schemaVersion/createdAt, snapshot-only handoff, Market Result가 Concept를 수정하지 않음.

## V2-6 — Marketing Source Cutover

- Selected Concept + final accepted hypotheses + Legal Result
- immutable `MarketingSourceSnapshot`
- allowed/prohibited claims, disclosures, communication controls
- Market Result/FinalizedPlanning mandatory dependency 제거
- 기존 TaskRun/SSE 재사용

## V2-7 — TechOps Preparation and Snapshot

- 독립 route/module status/frontend shell
- 상위 확정값 reuse
- required user facts와 AI-proposed required decisions
- optional Evidence 분리
- immutable `TechOpsInputSnapshot`
- external handoff boundary

분석 알고리즘은 구현하지 않는다.

## V2-8 — Financial Preparation and Snapshot

- TechOps 값 자동 승계
- missing fixed-cost, initial-investment, three-year target input
- 사업 유형별 목표 metric
- CAC components와 system calculation
- conditional unit/variable cost
- AI explanation/proposal decision
- immutable `FinancialInputSnapshot`과 external handoff

외부 소유 재무 계산 알고리즘은 구현하지 않는다.

## V2-9 — Active Surface Cleanup and Acceptance

- old required Idea fields unreachable
- old planning proposal workflow unreachable
- FinalizedPlanning Marketing dependency 제거
- legacy duplicate route/controller/registration 제거
- module status와 Job Center current truth 확인
- stale enum/microcopy 제거
- Browser verification 문서 작성

## 공통 실행·검증 규칙

각 Unit은 관련 파일 조사, 구현, compile/syntax, 직접 관련 targeted test, targeted lint, `git diff --check`, RESULT, USER_VERIFICATION까지만 수행한다.

R0~R6 fast profile에 해당하는 동안 전체 backend suite, 전체 postgresTest/Testcontainers, frontend production build, Docker full rebuild/E2E, live provider smoke, 전체 CI는 사용자 요청 없이 실행하지 않는다. 결과 문서는 실행한 검사와 생략한 검사를 정확히 구분하고 runtime acceptance가 남으면 명시한다.

## DB와 Cutover

현재 migration baseline을 조사한 뒤 V2 domain별 명확한 table/entity를 추가한다. migration history를 임의로 훼손하지 않는다. 신규 코드는 legacy journey/persona/interview/market-response/feasibility/marketing-workspace를 import하지 않는다. 기존 Persona 외부 계약은 승인 adapter 경계로만 유지한다.
