# NEW PIPELINE PRODUCT SPEC — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다. 본문은 2026-08-08 승인된 V2 제품 계약이다.

## 1. 제품 정의

사용자의 최소 사업 Seed를 안전하게 해석하고, 실질적으로 다른 사업 Concept를 설계해 공식 근거 기반 법률 구현 가능성을 검토한 뒤, 사용자가 하나를 선택하고 가설을 확정해 외부 분석과 Marketing에 전달하는 제품이다.

## 2. 사용자 핵심 작업

1. `ideaOverview`, `problem`, `targetUsers`를 입력한다.
2. 이미 정한 지역·경쟁자·수익·가격·채널·차별점·제약은 선택적으로 고정한다.
3. Safety 결과와 AI Interpretation을 확인·수정한다.
4. 법률 적격이면서 서로 다른 Concept 최대 5개를 비교한다.
5. Concept 하나를 선택하고 선택 Concept의 AI 가설만 결정한다.
6. 필요한 Delta Legal Review 후 Market Seed를 확정한다.
7. Market, BM, TechOps, Finance의 준비·외부 실행 상태를 확인한다.
8. 선택 Concept와 Legal Guard를 기반으로 Marketing 콘텐츠를 만든다.

## 3. 범위

### 포함

- `MarketAnalysisSeedDraft` 입력과 source/authority 보존
- Safety Gate와 AI Interpretation
- `EXPLORE`, `REFINE`, `AS_IS` 전략
- `ConceptCandidateV2`, LOCKED validation, distinctness
- Concept-generated Legal Fact Pattern과 official-evidence Legal Review
- 적격 Concept 동시 공개·비교·선택
- 선택 Concept hypothesis decision과 Delta Legal Review
- `MarketAnalysisSeedSnapshot` 및 외부 Market Handoff
- BM 실행 직전 Preparation boundary
- `TechOpsInputPreparation`과 `TechOpsInputSnapshot`
- `FinancialInputPreparation`과 `FinancialInputSnapshot`
- `MarketingSourceSnapshot`과 Legal Guard
- 공통 TaskRun, JobEvent, SSE, polling fallback, retry/recovery

### 제외

- 외부 Market, BM, TechOps, Finance 분석 알고리즘
- Persona 알고리즘 변경 또는 기존 외부 Persona 계약의 임의 삭제
- 시장분석이 Concept/Planning을 자동 수정하는 기능
- `planningChangeProposals`와 planning decision UX
- Marketing A/B Workspace와 launch strategy validator
- Provider raw reasoning, Prompt, policy, stack trace 노출

## 4. 제품 불변식

- 초기 필수 Seed는 정확히 세 필드다.
- 선택 Seed 누락은 Concept 탐색을 막지 않는다.
- 사용자 선택값은 `USER_INPUT + LOCKED`; AI는 Prompt와 Backend 어느 쪽에서도 변경할 수 없다.
- `AI_DERIVED + REVIEWABLE`은 입력 해석이고 `AI_HYPOTHESIS + PROPOSED`는 열린 값의 제안이다.
- Evidence는 source/가설이 아니라 독립 근거 resource다.
- Safety 판단과 Legal Review는 별도 의미와 상태를 갖는다.
- 플랫폼 역할, 결제·데이터 흐름, 파트너·자격, 광고 주장은 Concept가 설계한다.
- 후보 검증 순서는 schema → LOCKED/origin → distinctness → legal이다.
- 법률검토 전에 수익·가격·채널·차별점과 pre-market SOM 가설이 존재해야 한다.
- 의미상 중복 후보는 적격 수에 포함하지 않고 Legal API를 호출하지 않는다.
- 목표는 적격 distinct 5개지만 bounded limit 안에서 불가능하면 `INSUFFICIENT_DISTINCT_CONCEPTS`다.
- 적격 5개 상세는 준비 완료 뒤 동시에 공개한다.
- 선택 전 모든 후보 가설 확정을 요구하지 않는다.
- LOCKED 값은 hypothesis decision UI와 mutation 대상이 아니다.
- `REJECTED` 가설에는 alternative proposal 경로가 있다.
- legal-sensitive 가설 수정은 Delta Legal Review 통과 전 확정할 수 없다.
- pre-market SOM 수정은 기본적으로 non-legal이다.
- Market의 유일한 정식 입력은 immutable `MarketAnalysisSeedSnapshot`이다.
- Market Result는 Concept를 자동 변경하지 않는다.
- 후속 분석의 구체 입력은 각 분석 직전 Gate에서 받고 상위 확정값을 승계한다.
- Marketing 필수 Source는 Selected Concept, final accepted hypotheses, Legal Result다.

## 5. Seed acceptance criteria

- 세 필드만 입력해 Safety와 Interpretation을 시작할 수 있다.
- optional field가 없어도 진행할 수 있다.
- optional 입력값은 LOCKED로 저장된다.
- `BLOCK_OR_REFRAME`은 Concept Factory를 시작하지 않는다.
- Interpretation은 사용자 의미를 축약·변형하지 않고 사용자가 수정할 수 있다.
- 법률 상세 누락만으로 초기 `NEEDS_INPUT`을 만들지 않는다.
- 후속 질문은 핵심 문제·사용자·의도가 불명확해 Concept 탐색이 불가능할 때만 생성한다.

## 6. Concept acceptance criteria

- 최소 Seed로 후보를 생성할 수 있다.
- AS_IS Candidate 1은 사용자 원안을 의미 손실 없이 보존한다.
- 사용자 LOCKED 수익·가격·채널·차별점·지역·제약을 모든 후보가 지킨다.
- 열린 수익·가격·채널·차별점은 AI hypothesis로 표시된다.
- pre-market SOM은 구조화된 가설이며 시장 사실로 표시하지 않는다.
- payment/platform/data/partner/qualification/claim 구조는 Concept가 만든다.
- 이름만 변경한 후보와 semantic duplicate를 거부한다.
- distinctness를 통과하지 않은 후보는 Legal Review를 받지 않는다.
- Legal mapper는 legal-sensitive hypothesis를 포함하고 SOM을 제외한다.
- `REDESIGNABLE`은 한 번만 재설계하고 전체 검증을 반복한다.
- 설계 누락은 사용자 legal 질문이나 `NEEDS_FACTS`로 전가하지 않는다.

## 7. 선택·가설 acceptance criteria

- 사용자에게 legal eligible Concept만 공개한다.
- 선택 Concept의 `revenueModel`, `price`, `channels`, `differentiators`, `preMarketSomShareHypothesis`, `preMarketSomHypothesis`만 결정한다.
- 사용자는 채택, 수정 후 채택, 다른 제안을 선택할 수 있다.
- 거절은 `ALTERNATIVE_PROPOSED`를 생성해 dead end가 아니다.
- LOCKED 값은 hypothesis endpoint로 수정할 수 없다.
- 수익·결제·지역·법률 민감 claim 변경은 Delta Legal Review를 유발한다.
- SOM 변경은 Delta Review를 유발하지 않는다.
- 실패한 Delta Review는 해당 가설 acceptance를 막고 alternative를 허용한다.

## 8. 외부 분석과 Marketing acceptance criteria

- 모든 필수 decision과 Delta Review가 끝나야 Market Seed Snapshot을 finalize할 수 있다.
- Market handoff는 Snapshot 본문, ID, hash, schemaVersion을 전달한다.
- Market Result에는 planning change proposal이 없다.
- BM 추가 입력은 BM 시작 직전에만 받는다.
- TechOps와 Finance는 각각 준비 화면, 결정 상태, immutable Snapshot, 외부 handoff boundary를 갖는다.
- TechOps/Finance에서 이미 확정된 값을 다시 입력시키지 않는다.
- AI 추정값은 사용자 채택 전 user fact가 아니다.
- Marketing은 Market Result나 `FinalizedPlanningSnapshot`을 필수로 요구하지 않는다.
- Marketing 출력은 allowed/prohibited claims, disclosure, communication control을 지킨다.

## 9. 비동기와 오류 UX

- Event는 진행 신호이고 Query API가 화면 정본이다.
- Terminal TaskRun과 Job ID는 새 사용자 Action에 재사용하지 않는다.
- terminal Event 뒤 동일 jobId Event를 추가하지 않는다.
- raw TaskRun 결과와 현재 사용자 actionability를 분리한다.
- 외부 현실 사실이 필요한 `NEEDS_FACTS`와 Concept 설계 실패를 구분한다.
- Provider failure는 Attempt error이며 사용자-facing Slot enum이 아니다.
- transient retry, schema repair, redesign, replacement는 모두 bounded다.
- 도메인 전제조건은 안전한 error code, userMessage, nextAction으로 표현한다.

## 10. 제품 완료 표현

코드·계약 구현과 실제 provider/browser/external runtime acceptance를 구분한다. 무거운 검증이 생략된 Unit은 “implementation complete / runtime acceptance pending”으로 기록한다.
