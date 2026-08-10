# AI 사업검증 플랫폼 재구축 통합 기획서 v2 계약 기준

- 대상 브랜치: `rebuild/new-pipeline-v1`
- 문서 상태: V2 authoritative product contract
- 계약 변경 승인: 2026-08-08 Master Rebuild Directive
- 파일명은 기존 참조 호환을 위해 `v1.0`을 유지하지만, 본문은 V2 계약이 정본이다.

## 0. 문서의 목적과 우선순위

이 문서는 Market Seed, Concept, Legal, 분석 입력 Snapshot, Marketing Source의 제품 흐름을 V2로 고정한다. 기존 Journey, 10개 이상 필수 Idea Brief, 시장분석 기획 변경안, `FinalizedPlanningSnapshot` 중심 계약과 충돌하면 본 문서를 따른다.

제품 흐름은 다음과 같다.

`최소 Market Seed → Safety Gate → AI Interpretation → commitment 확인 → Final Synthesis → 사용자 최종 확인 → Concept 전략 → 후보 생성 → 구조·LOCKED·중복 검증 → 공식 근거 기반 Legal Review → 적격 5개 비교·선택 → 선택 Concept 7개 가설 결정 → 필요한 Delta Legal Review → MarketAnalysisSeedSnapshot → 외부 Market Analysis → 후속 분석 → Marketing`

## 1. 핵심 제품 원칙

- 초기 사용자 필수값은 `ideaOverview`, `problem`, `targetUsers` 세 개뿐이다.
- 사용자가 이미 결정한 선택값은 `USER_INPUT + LOCKED`이며 AI가 바꾸지 않는다.
- AI 해석과 AI 가설을 구분한다.
- 법률에 필요한 사업 구조는 초기 사용자가 아니라 각 Concept가 설계한다.
- Concept는 이름이 아니라 실질적인 사업 구조로 구별한다.
- Concept 구조와 법률 민감 가설을 완성하고 중복을 제거한 뒤 Legal Review를 수행한다.
- 사용자가 선택한 Concept의 AI 가설만 최종 결정한다.
- 법률 민감 가설 변경만 Delta Legal Review를 수행한다.
- 외부 모듈은 내부 Entity가 아닌 불변 Snapshot을 입력으로 받는다.
- Market Analysis는 선택 Concept를 자동 변경하지 않으며 planning-change workflow를 제공하지 않는다.
- Marketing은 시장분석 결과를 기다리지 않고 선택 Concept, 확정 가설, Legal Result를 사용한다.
- Evidence는 값의 source가 아니라 별도의 근거 resource다.

## 2. 사용자 Workflow와 모듈

| 순서 | 사용자 표시명 | 핵심 출력 | 구현 경계 |
|---|---|---|---|
| 1 | 아이디어 / Market Seed | 확인된 Seed와 AI Interpretation | 직접 구현 |
| 2 | 컨셉 생성·법률검토 | 법률 적격 Concept 최대 5개 | 직접 구현 |
| 3 | 컨셉 비교·선택·가설 확정 | Selected Concept와 확정 가설 | 직접 구현 |
| 4 | 시장분석 | 외부 Market Result | Snapshot/Handoff/Shell |
| 5 | BM | 외부 BM Result | Preparation/Handoff/Shell |
| 6 | 기술·운영 분석 | `TechOpsInputSnapshot` | Preparation/Snapshot/Handoff |
| 7 | 재무 분석 | `FinancialInputSnapshot` | Preparation/Snapshot/Handoff |
| 8 | 마케팅 콘텐츠 | 선택 Concept 기반 콘텐츠 | 직접 UI·비동기 경계 |

Persona 외부 계약이 이미 존재하면 독립적으로 유지한다. 이번 V2 변경만으로 삭제하거나 Market Seed 필수 단계로 만들지 않는다.

모듈 상태는 전역 `project.stage` 하나로 통제하지 않는다. 공통 상태는 `NOT_READY`, `READY`, `QUEUED`, `RUNNING`, `NEEDS_INPUT`, `COMPLETED`, `FAILED`, `STALE`, `NOT_CONNECTED`를 사용한다. 페이지 진입은 허용하고 실제 Action에서만 전제조건을 검사한다.

## 3. 값의 출처와 결정권

### Source

- `USER_INPUT`: 사용자가 제공한 사실 또는 선택
- `USER_CONFIRMED`: 자유문장에서 추출된 구체값을 사용자가 확인하거나 수정 후 확인한 값
- `AI_DERIVED`: 사용자 입력 의미를 보존해 구조화한 해석
- `AI_HYPOTHESIS`: 사용자가 정하지 않은 값을 위한 AI 가설
- `CONCEPT_GENERATED`: Concept 설계 과정에서 정의된 구조
- `ANALYSIS_RESULT`: 외부 또는 후속 분석 결과

### Authority와 decision

- `LOCKED`: 사용자가 이미 결정했으며 AI 변경 금지
- `REVIEWABLE`: AI 해석이며 사용자 확인·수정 가능
- `OPEN`: 아직 정해지지 않은 설계 변수
- `PROPOSED`: AI 제안
- `ACCEPTED`: 사용자가 제안 채택
- `USER_EDITED_ACCEPTED`: 사용자가 수정 후 채택
- `REJECTED`: 현재 제안 거절. 대체 제안 경로가 있어야 함

값은 `value`, `source`, `authority` 또는 `decision`을 함께 보존한다. 사용자 원문을 모호한 추상 표현으로 바꾸지 않는다.

## 4. MarketAnalysisSeedDraft

### 필수 입력

- `ideaOverview`
- `problem`
- `targetUsers`

### 선택 입력

- `targetRegion`
- `knownCompetitors`
- `revenueModel`
- `price`
- `channels`
- `differentiators`
- `constraints`
  - `budgetConstraint`
  - `teamConstraint`
  - `timelineConstraint`
  - `otherConstraint`

선택값이 입력되면 `USER_INPUT + LOCKED`, 비어 있으면 `OPEN`이다. 선택값 누락은 Concept 탐색을 막지 않는다.

`beneficiaries`, `usageContext`, `expectedOutcome`, `physicalActivity`, `personalData`, `payment`, `requiredPartners` 같은 기존 법률·운영 상세값은 초기 필수 입력이 아니다. 필요한 정보는 `AI_DERIVED` 또는 `CONCEPT_GENERATED` 영역에서 다룬다.

## 5. Safety Gate와 AI Interpretation

Seed 입력 후 Concept Factory 전에 Safety Gate를 수행한다. 이는 법률검토가 아니라 시스템이 아이디어 구상을 지원해도 되는지 판단하는 별도 Gate다.

최소 안전 범주는 범죄 실행·지원, 폭력·신체 위해, 성적 착취, 미성년자 성적 대상화, 자해 조장, 개인정보 악용·무단감시, 피싱·사칭·기만, 혐오·차별 목적, 위험물·불법 유통, 명백한 착취 목적이다.

판정은 `ALLOW`, `ALLOW_WITH_RESTRICTIONS`, `BLOCK_OR_REFRAME`이다. `BLOCK_OR_REFRAME`이면 Concept Factory를 시작하지 않고 안전한 사용자 설명만 제공한다. Prompt, 내부 policy, raw reasoning은 노출하지 않는다.

AI Interpretation은 다음을 `AI_DERIVED + REVIEWABLE`로 구조화한다.

- `interpretedProblem`
- `interpretedTargetUsers`
- `usageContext`
- `industryCategory`
- `researchScope`
- `conciseIdeaDefinition`
- 필요한 경우 지역·기존 경쟁자 context

사용자 자유문장에 지역, 경쟁자, 수익모델, 가격, 채널, 차별점, 예산·팀·일정·기타 제약의 구체값이 명시되어 있으면 별도 `UserTextCommitmentCandidate`로 추출한다. 최초 의미는 `source=AI_DERIVED`, `origin=USER_TEXT`, `authority=REVIEWABLE`이며 즉시 LOCKED가 아니다. 사용자는 확인, 수정 후 확인, OPEN으로 되돌리기를 선택할 수 있고, 확인한 값만 `USER_CONFIRMED + LOCKED`로 승격한다. dedicated 선택 입력의 `USER_INPUT + LOCKED` 값은 추출 후보보다 항상 우선하며 충돌 후보가 overwrite할 수 없다.

commitment action이 field value, provenance 또는 decisionState를 실제로 바꾸면 기존 assessment는 stale이다. 이때 같은 terminal TaskRun을 재사용하지 않고 새 `FINAL_SYNTHESIS` TaskRun을 생성해 `DERIVING → READY_FOR_REVIEW`를 거친다. 사용자는 새 synthesis 이후 Interpretation을 수정하고 최종 Confirm한다. canonical 변경이 없는 동일 action replay는 중복 실행을 만들지 않는다.

사용자는 “입력하신 아이디어를 이렇게 이해했습니다.” 화면에서 그대로 진행하거나 수정한다. 후속 질문은 문제·사용자·의도가 모호해 Concept 탐색 자체가 불가능한 경우에만 최소화한다. 플랫폼 역할, 결제 주체, 개인정보, 파트너, 물리활동은 초기 질문으로 강제하지 않는다.

## 6. Concept 전략과 목표 수

AI Interpretation 후 전략을 `EXPLORE`, `REFINE`, `AS_IS` 중 하나로 정한다.

- `EXPLORE`: 문제와 사용자를 중심으로 넓은 대안을 탐색한다.
- `REFINE`: LOCKED 구조를 유지하고 열린 축만 변화시킨다.
- `AS_IS`: Candidate 1에 사용자 원안을 의미 손실 없이 구조화하고, Candidate 2~5는 열린 사업 축에서만 대안을 탐색한다.

`AS_IS`도 Concept Factory를 생략하지 않는다.

전략은 Backend deterministic policy가 정본이다. 최소 Seed만 있으면 `EXPLORE`, 일부 상업·채널·운영 commitment가 확인되면 `REFINE`, 구체적인 문제·사용자·해결 mechanism과 복수의 확인된 commitment가 함께 있으면 `AS_IS`다. AI가 반환한 strategy 문자열만으로 결정하지 않는다.

목표는 법률 적격이면서 서로 다른 Concept 5개다. 초기 5개, 전체 최대 15개 검사, replacement round 최대 2회, 후보별 법률 재설계 최대 1회의 bounded rule을 유지한다. 의미상 중복은 수에 포함하지 않는다. 한도 내 5개 확보가 불가능하면 `INSUFFICIENT_DISTINCT_CONCEPTS`로 종료한다.

## 7. ConceptCandidateV2

Legal Review 전에 각 후보는 다음 구조를 완성한다.

- 사용자-facing: `conceptName`, `conceptDefinition`, `introduction`, `coreValue`, `targetUsers`, `industryCategory`, `researchScope`
- Market hypothesis: `targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`, `preMarketSomShareHypothesis`, `preMarketSomHypothesis`
- Solution: `problemScenario`, `solutionMechanism`, `featureSet`
- Operating Structure: `actorRoles`, `platformRole`, `operatingModel`, `partnerModel`
- Legal Fact Pattern 입력: `transactionFlow`, `paymentFlow`, `personalDataUsage`, `physicalActivities`, `partnerRequirements`, `qualificationRequirements`, `advertisingClaims`

Seed의 `targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`, `constraints`가 LOCKED이면 Prompt와 Backend validation 모두 후보의 동일 의미 보존을 검사한다. 위반 후보는 `ORIGIN_INVALID` 또는 `LOCKED_CONSTRAINT_INVALID`로 거부한다.

Seed에서 비어 있던 `targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`는 Concept별 `AI_HYPOTHESIS + OPEN + PROPOSED`로 생성한다. 현재 Legal 지원 범위 때문에 열린 `targetRegion`은 KR-compatible 지역으로 제안한다. 모든 Concept는 사전 SOM 점유율과 금액 가설을 생성하되 `preMarket` 가설임을 명확히 표시한다.

`preMarketSomShareHypothesis`는 `targetSharePercent`, `horizonYears`, `rationale`, `assumptions`를 포함한다. `preMarketSomHypothesis`는 `amount`, `currency`, `period`, `calculationBasis`, `assumptions`, `confidence`를 포함한다. 외부 시장조사 결과처럼 표현하지 않는다.

## 8. Distinctness와 Legal Review

검증 순서는 고정한다.

`GENERATE → SCHEMA VALIDATION → LOCKED/ORIGIN VALIDATION → DISTINCTNESS VALIDATION → LEGAL REVIEW`

Fingerprint는 `targetUsers`, `problemScenario`, `coreValue`, `solutionMechanism`, `revenueModel`, `channels`, `platformRole`, `operatingModel`, `partnerModel`, `transactionFlow`, provider/seller/intermediary role을 사용한다. deterministic normalized fingerprint와 lexical similarity를 cheap first pass로 유지하고, 애매한 pair만 strict structured AI judge로 판정한다. 이름·어순·동의어만 다른 후보는 중복이며 Legal API로 보내지 않는다. Candidate N 생성 입력에는 이미 적격인 후보들의 이 간단 fingerprint만 제공해 생성 시점부터 중복을 피한다.

INITIAL, REPLACEMENT, REDESIGN 후보는 예외 없이 같은 schema → LOCKED/origin → deterministic distinctness → ambiguous semantic judge → Legal 순서를 사용한다. semantic judge의 schema/technical failure가 난 후보는 Legal로 통과시키지 않는다.

Concept별 `LegalFactPattern`은 `jurisdiction/targetRegion`, `actorRoles`, `platformRole`, provider/seller/intermediary role, `transactionFlow`, `paymentFlow`, `personalDataUsage`, `physicalActivities`, `partnerRoles`, `qualificationRequirements`, `advertisingClaims`, `operatingModel`을 포함하고 각 값 source를 보존한다.

Legal Review는 Concept 구조와 legal-sensitive hypothesis가 모두 생성된 후 “정의된 구조와 통제조건대로 구현하면 법적으로 구현 가능한가”를 공식 근거로 검토한다. `revenueModel`, 가격·결제 구조, `targetRegion`은 기본 legal-sensitive이고, 채널과 차별점·표현은 내용에 따라 민감할 수 있다. pre-market SOM 가설은 non-legal이다.

결과 schema는 `IMPLEMENTABLE`, `IMPLEMENTABLE_WITH_CONTROLS`, `REDESIGNABLE`, `NEEDS_FACTS`, `REJECTED`를 유지한다. 사용자에게 공개 가능한 결과는 처음 두 개다. `REDESIGNABLE`은 최대 1회 재설계하고 schema, LOCKED/origin, distinctness, legal을 모두 다시 검사한다. V2 active Concept Factory에서 `NEEDS_FACTS`는 사용자 입력 대기로 전이하지 않고 `LEGAL_EXTERNAL_FACT_UNRESOLVED` business rejection 후 replacement한다. Concept 설계 누락은 incomplete/redesign 대상이다. 외부 사실을 추정하지 않고 강제 통제조건으로 구조화할 수 있을 때만 `IMPLEMENTABLE_WITH_CONTROLS`가 가능하다.

현재 공식 Legal jurisdiction은 KR only다. locked `targetRegion`이 KR로 명확히 해석되지 않으면 run/Legal Provider 전에 `LEGAL_JURISDICTION_UNSUPPORTED`로 차단한다. 열린 region 후보가 foreign/unknown이면 candidate 단계에서 reject/replacement하며, 선택 후 unsupported region edit도 Delta Legal Provider 전에 차단한다. unknown을 조용히 KR로 간주하지 않는다.

## 9. 공개, 선택, 가설 결정

적격 Concept 5개를 확보한 뒤 상세를 한 번에 공개한다. 카드에는 이름, 정의, 핵심 가치, 대상 사용자, 업종, 조사 범위, 수익·가격, 채널, 차별점, 사전 SOM 가설, 법률 상태와 핵심 조건을 표시한다. 내부 Attempt, provider enum, 폐기 Draft는 숨긴다.

사용자는 5개 provisional hypothesis를 비교해 하나를 선택한다. 선택 전에 모든 후보 가설 확정을 요구하지 않는다. 선택한 Concept에 대해서만 다음 가설을 결정한다.

- `targetRegion`
- `revenueModel`
- `price`
- `channels`
- `differentiators`
- `preMarketSomShareHypothesis`
- `preMarketSomHypothesis`

Seed의 `USER_INPUT 또는 USER_CONFIRMED + LOCKED` 값은 이미 확정된 것으로 간주해 다시 묻거나 수정 endpoint로 변경하지 않는다. AI `targetRegion`을 포함한 열린 가설 Action은 채택, 수정 후 채택, 다른 제안이다. 거절하면 `ALTERNATIVE_PROPOSED`를 생성해 dead end를 막는다.

법률 민감 가설을 수정하면 Delta Legal Review 통과 전 승인 완료로 처리하지 않는다. SOM처럼 non-legal인 변경은 즉시 확정할 수 있다. Delta 결과가 부적격이면 대체 가설을 선택할 수 있어야 한다.

`REQUEST_ALTERNATIVE`와 Delta Legal은 독립 TaskRun으로 실행한다. command 요청은 즉시 `202`와 새 job identity를 반환하고 기존 proposal/final value는 worker의 검증된 성공 commit 전까지 보존한다. worker는 selection, decision ID/version, pending task, concept hash를 재검사하며 stale 결과는 최신 상태를 변경하지 않는다. legal business ineligible은 성공한 provider execution과 실패한 domain acceptance를 분리한다.

## 10. MarketAnalysisSeedSnapshot과 Market Handoff

다음 조건을 모두 충족하면 불변 `MarketAnalysisSeedSnapshot`을 만든다.

- Concept 선택 완료
- 필수 hypothesis decision 완료
- 필요한 Delta Legal Review 완료
- 선택 Concept의 legal eligibility 유효

Snapshot은 사용자 원본 Seed와 LOCKED 값, AI Interpretation, 선택 Concept의 solution/operation, 최종 7개 가설 결정, Legal Result와 controls/partner/qualification/prohibited variant/disclosure/official evidence reference를 포함한다. `targetRegion`도 Candidate 값을 직접 복사하지 않고 최종 `TARGET_REGION` decision을 사용한다. `snapshotId`, `schemaVersion`, `hash`, `createdAt`은 필수다.

외부 Market Analysis는 이 Snapshot만 정식 입력으로 받는다. 결과는 최소 `runId`, `inputSnapshotId`, `status`, `resultReference`, `summary`, `competitorProducts`, `marketSizing`, `findings`, `completedAt`, `resultHash`를 포함한다. 시장분석은 분석 결과를 반환하면 끝이며 Concept를 자동 변경하지 않는다.

`planningChangeProposals`, planning decision UI/API/domain, Market 기반 `FinalizedPlanningSnapshot` workflow는 active contract가 아니다.

## 11. 후속 분석 입력 Gate

BM 알고리즘은 외부 소유다. 추가 필수 입력이 확정되면 BM 시작 직전 Preparation/Input Gate에서 받고 초기 Seed 필수값으로 끌어올리지 않는다.

기술·운영 분석 시작 직전 `TechOpsInputPreparation`에서 다음 사용자 사실을 준비한다: `productServiceSpecification`, `targetLaunchDate`, `ownedPersonnel`, `ownedAssetsAndFacilities`, `fixedOperatingCost`, `initialInvestment`, `threeYearTargets`. 상위 신뢰 Snapshot의 확정값은 자동 승계한다.

Concept가 만든 `productServiceSpecification`은 editable `CONCEPT_GENERATED + REVIEW_REQUIRED` prefill이며 사용자의 확인 또는 수정 후 확인 전에는 TechOps 확정 사실이 아니다. 3개년 목표는 TechOps와 Finance가 공통 `{metric, unit, years:[{year,value}]}` 구조를 사용하고, Finance는 유효한 TechOps 확정 목표를 100% read-only로 승계한다.

`deliveryOrProductionMethod`, `expectedMonthlyThroughputOrSales`, `technicalSupplyOperationalConstraints`는 AI가 제안할 수 있으나 분석 전 사용자가 결정해야 한다. 누락 제안은 null 완료형이 아니라 실제 `TECH_OPS_PROPOSAL` 결과로 채운다. 대안 요청은 proposalVersion을 증가시키고 직전 거절값과 다른 제안을 생성한다. 견적서, BOM, 공급사 정보, 사양서, 파일럿 자료는 optional Evidence이며 AI가 생성한 가짜 자료를 Evidence로 저장하지 않는다. 준비 완료 시 불변 `TechOpsInputSnapshot`을 만든다.

TechOps preparation 초기화는 provider를 기다리지 않는다. preparation을 먼저 저장하고 누락 제안이 있으면 세 운영 가설을 한 번에 생성하는 실제 `TECH_OPS_PROPOSAL` TaskRun 하나를 queue한다. 대안 요청도 `202` TaskRun이며 기존 proposal은 worker success 전까지 보존한다. preparation revision, field proposal version, pending task, source Market Seed ID/hash, Snapshot 미확정을 worker commit 전에 재검사한다. AI 실패 뒤에도 사용자는 직접 `EDIT_AND_ACCEPT`할 수 있고 새 command key로 retry할 수 있다.

TechOps preparation 초기화는 provider를 기다리지 않는다. preparation을 먼저 저장하고 누락 제안이 있으면 세 운영 가설을 한 번에 생성하는 실제 `TECH_OPS_PROPOSAL` TaskRun 하나를 queue한다. 대안 요청도 `202` TaskRun이며 기존 proposal은 worker success 전까지 보존한다. preparation revision, field proposal version, pending task, source Market Seed ID/hash, Snapshot 미확정을 worker commit 전에 재검사한다. AI 실패 뒤에도 사용자는 직접 `EDIT_AND_ACCEPT`할 수 있고 새 command key로 retry할 수 있다.

재무분석 시작 직전 `FinancialInputPreparation`은 TechOps 값부터 재사용한다. 없는 값만 입력받는다.

- 고정운영비: `annualFixedLaborCost`, `annualFixedRentAndManagementCost`, `annualFixedInfrastructureCost`
- 초기투자: `initialDevelopmentAndRnDCost`, `initialEquipmentAndInfrastructureCost`, `initialPatentAndLicensingCost`
- 3개년 목표: 사업 유형에 맞는 `salesVolume`, `customerCount`, `subscriberCount`, `transactionCount`
- CAC 구성값: `totalMarketingCost`, `totalSalesCost`, `newCustomerCount`; 시스템이 CAC를 계산

`unitVariableCost`, `paymentFee`, `partnerPayout`, `shippingCost`, `customerIncrementalInfraCost`는 계약상 필요할 때만 조건부로 받는다. AI 추정값은 `AI_HYPOTHESIS` 또는 `AI_ESTIMATE + PROPOSED`이고 사용자 채택 전 사실이 아니다. 준비 완료 시 불변 `FinancialInputSnapshot`을 만든다.

Finance assistance는 비용·3개년 목표·마케팅/영업비·조건부 원가에 실제 `FINANCE_ESTIMATE` 제안을 제공한다. 제안은 value, assumptions, explanation, confidence를 포함한다. 수락값은 `AI_ESTIMATE + ACCEPTED`, 사용자 수정값은 `USER_INPUT + USER_EDITED_ACCEPTED`로 구분한다. `PROPOSED` 값은 Snapshot gate를 통과하지 못하며 CAC는 계속 서버 공식 계산이 정본이다.

## 12. Marketing Source

Marketing의 필수 Source는 선택 Concept, 최종 확정 가설, Legal Review Result다. 가능하면 불변 `MarketingSourceSnapshot`으로 고정한다. 시장분석, BM, TechOps, 재무, Persona 결과는 필수 선행조건이 아니다.

Marketing Source는 최소 `allowedClaims`, `prohibitedClaims`, `requiredDisclosures`, communication 관련 `requiredControls`를 포함한다. Legal Result와 충돌하는 마케팅 표현을 생성하지 않는다.

## 13. Route와 API 방향

새 Shell의 목표 Route는 다음 의미를 제공한다.

- `/idea`: Market Seed와 Interpretation
- `/concepts`: 생성·중복·Legal Workboard
- `/concepts/compare`: 비교·선택·가설 결정
- `/market`: Market Snapshot/Handoff/Result
- `/business-model`: BM Preparation/외부 Shell
- `/tech-ops`: 기술·운영 Preparation/Snapshot/외부 Shell
- `/finance`: 재무 Preparation/Snapshot/외부 Shell
- `/marketing`: 선택 Concept 기반 콘텐츠

정확한 path는 현행 `/api/v3`와 frontend convention을 조사해 최소 breaking change로 정한다. capability는 Seed 조회·수정·안전/해석·확인, Concept Factory/조회/비교, 선택, 가설 결정·대체 제안, Delta Legal Review, Market Seed finalize/query, TechOps/Finance preparation·decision·snapshot finalize, Marketing Source를 제공해야 한다.

## 14. 비동기 실행과 Snapshot

기존 `TaskRun`, `TaskAttempt`, `JobEvent`, SSE, polling fallback, retry/recovery를 재사용한다. 필요한 task type은 `IDEA_SAFETY_REVIEW`, `IDEA_INTERPRETATION`, `CONCEPT_CANDIDATE_V2`, `CONCEPT_DISTINCTNESS_REVIEW`, `CONCEPT_LEGAL_REVIEW`, `CONCEPT_HYPOTHESIS_ALTERNATIVE`, `CONCEPT_DELTA_LEGAL_REVIEW`다. 모든 작은 Action을 무조건 비동기로 만들지 않는다.

Terminal TaskRun/Job ID는 immutable history다. Event는 갱신 신호이고 Query API가 화면 정본이다. Prompt, provider raw error/body, API key, stack trace는 사용자 Event에 노출하지 않는다.

`MarketAnalysisSeedSnapshot`, `TechOpsInputSnapshot`, `FinancialInputSnapshot`, `MarketingSourceSnapshot`은 공통 Entity로 뭉치지 않고 각각 `snapshotId`, `schemaVersion`, `hash`, `createdAt`을 가진 불변 경계다.

## 15. 데이터와 Cutover 원칙

새 핵심 domain은 필요하면 명확한 table/entity로 추가한다. 권장 대상은 `market_analysis_seed_drafts`, typed seed fields, `idea_interpretations`, `idea_safety_reviews`, `concept_hypothesis_decisions`, `market_analysis_seed_snapshots`, `tech_ops_input_snapshots`, `financial_input_snapshots`이다. 현재 migration baseline을 먼저 조사하고 migration history를 임의로 훼손하지 않는다.

active path에서 다음 계약을 제거한다.

- 초기 10개 이상 필수 Idea field
- 법률 상세를 모두 사용자 질문으로 채우는 흐름
- AI 해석을 무조건 사용자 입력/확정 사실로 저장하는 흐름
- source/authority를 버리는 Concept input
- 이름만 다른 Concept를 별도 후보로 인정하는 흐름
- Legal Review 뒤에 수익·가격·채널·차별점 가설을 생성하는 흐름
- `planningChangeProposals`와 시장분석 기반 planning decision
- Marketing의 `FinalizedPlanningSnapshot` 또는 Market Result 필수 의존

Legacy 코드는 최종 cleanup 전까지 남을 수 있으나 active route/controller/import/registration에서 도달할 수 없어야 한다.

## 16. 구현 Unit

1. V2-0 — Authoritative Contract Reset
2. V2-1 — Market Seed Intake and Interpretation
3. V2-2 — ConceptCandidateV2 and Distinctness
4. V2-3 — Concept Legal Fact Pattern Integration
5. V2-4 — Concept Selection and Hypothesis Decision
6. V2-5 — Market Seed Snapshot and Handoff
7. V2-6 — Marketing Source Cutover
8. V2-7 — TechOps Preparation and Snapshot
9. V2-8 — Financial Preparation and Snapshot
10. V2-9 — Active Surface Cleanup and Acceptance
11. V2-10A — Seed Commitment Extraction and Generation Strategy
12. V2-10B — Concept Semantic Distinctness Hardening
13. V2-10C — TechOps Confirmation, Proposal and Shared Target Contract
14. V2-10D — Finance AI Estimate Assistance

각 Unit은 해당 범위만 수행하고 다음 Unit으로 자동 진행하지 않는다.

## 17. 비범위

- 외부 Market Analysis 알고리즘
- 외부 BM 알고리즘
- 외부 TechOps 분석 알고리즘
- 외부 Financial 분석 알고리즘
- Persona 알고리즘 변경
- Marketing A/B 검증 Workspace
- launch strategy validator

이번 파이프라인의 책임은 input contract, immutable snapshot, handoff, frontend shell, integration boundary다.

## 18. 최종 불변식

초기 필수값은 정확히 세 개이고, 사용자 결정값은 LOCKED다. AI Interpretation과 AI Hypothesis는 구분한다. 각 Concept가 Legal Fact Pattern을 만들고, 중복 제거와 가설 완성 뒤 Legal Review를 받는다. 목표는 적격·distinct Concept 5개이며 억지로 수를 채우지 않는다. 선택 Concept의 가설만 사용자가 확정하고 legal-sensitive 변경만 Delta Review를 거친다. `MarketAnalysisSeedSnapshot`은 Market의 유일한 정식 입력이다. Market은 planning을 자동 변경하지 않는다. 후속 분석 입력은 실행 직전에 준비하며 상위 값을 재입력시키지 않는다. Marketing은 선택 Concept, 확정 가설, Legal Result를 사용한다. Evidence와 AI 가설은 동일하지 않다. 모든 모듈 경계는 개별 immutable Snapshot을 사용한다.
