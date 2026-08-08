# NEW PIPELINE DATA MODEL AND API CONTRACT — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다.

## 1. 공통 값 계약

```text
ValueSource = USER_INPUT | AI_DERIVED | AI_HYPOTHESIS | CONCEPT_GENERATED | ANALYSIS_RESULT
Authority = LOCKED | REVIEWABLE | OPEN
DecisionStatus = PROPOSED | ACCEPTED | USER_EDITED_ACCEPTED | REJECTED | ALTERNATIVE_PROPOSED
```

Evidence는 `ValueSource`가 아니라 독립 resource와 link다. 값은 가능한 한 `value`, `source`, `authority/decision`, provenance를 함께 저장한다.

## 2. 정본과 불변 Snapshot

- `MarketAnalysisSeedDraft`
- `IdeaSafetyReview`
- `IdeaInterpretation`
- `ConceptCandidateV2`
- `ConceptLegalAssessment`
- `ConceptHypothesisDecision`
- `MarketAnalysisSeedSnapshot`
- `TechOpsInputSnapshot`
- `FinancialInputSnapshot`
- `MarketingSourceSnapshot`

각 Snapshot은 고유 Entity이며 공통 mutable payload Entity로 합치지 않는다. 최소 필드는 `snapshotId`, `schemaVersion`, `hash`, `createdAt`이다. 소비 결과는 input Snapshot ID와 hash를 보존하며 현재 입력과 다르면 `STALE`이지만 과거 결과 조회는 가능하다.

## 3. Market Seed

`MarketAnalysisSeedDraft` 필수 typed field는 `ideaOverview`, `problem`, `targetUsers`다. 선택 field는 `targetRegion`, `knownCompetitors`, `revenueModel`, `price`, `channels`, `differentiators`, 구조화된 `constraints`다.

선택값이 입력되면 `USER_INPUT + LOCKED`, 없으면 `OPEN`이다. `targetRegion`, `beneficiaries`, `usageContext`, `expectedOutcome`, `physicalActivity`, `personalData`, `payment`, `requiredPartners`를 초기 `USER_REQUIRED` catalog로 강제하지 않는다.

권장 persistence:

- `market_analysis_seed_drafts`
- typed seed columns 또는 의미가 분리된 `market_analysis_seed_fields`
- `idea_safety_reviews`
- `idea_interpretations`

## 4. ConceptCandidateV2

후보 payload는 다음 영역을 typed 또는 schema-validated structure로 보존한다.

- identity/presentation: name, definition, introduction, coreValue, targetUsers, industryCategory, researchScope
- solution: problemScenario, solutionMechanism, featureSet
- hypotheses: targetRegion, revenueModel, price, channels, differentiators, preMarketSomShareHypothesis, preMarketSomHypothesis
- operation: actorRoles, platformRole, operatingModel, partnerModel
- legal facts: transactionFlow, paymentFlow, personalDataUsage, physicalActivities, partnerRequirements, qualificationRequirements, advertisingClaims

`preMarketSomShareHypothesis`는 `targetSharePercent`, `horizonYears`, `rationale`, `assumptions`를, `preMarketSomHypothesis`는 `amount`, `currency`, `period`, `calculationBasis`, `assumptions`, `confidence`를 가진다.

후보 validation 순서는 schema → LOCKED/origin → distinctness → legal이다. LOCKED 위반은 `ORIGIN_INVALID`/`LOCKED_CONSTRAINT_INVALID`, 한도 내 distinct 5개 부족은 `INSUFFICIENT_DISTINCT_CONCEPTS`다.

Fingerprint는 `targetUsers`, `problemScenario`, `coreValue`, `solutionMechanism`, `revenueModel`, `channels`, `platformRole`, `operatingModel`, `partnerModel`을 최소 입력으로 한다.

## 5. Legal Fact Pattern과 Legal Result

Concept별 Legal Fact Pattern은 jurisdiction/targetRegion, actorRoles, platformRole, provider/seller/intermediary role, transactionFlow, paymentFlow, personalDataUsage, physicalActivities, partnerRoles, qualificationRequirements, advertisingClaims, operatingModel과 각 값 source를 포함한다.

Legal status:

```text
IMPLEMENTABLE | IMPLEMENTABLE_WITH_CONTROLS | REDESIGNABLE | NEEDS_FACTS | REJECTED
```

Legal mapper는 수익, 가격·결제, 지역과 내용상 민감한 채널·claim을 포함한다. pre-market SOM은 제외한다. `NEEDS_FACTS`는 AI가 설계할 수 없는 실제 외부 사실에만 사용한다.

## 6. ConceptHypothesisDecision

별도 domain/table로 최소 다음을 저장한다.

- `conceptId`
- `hypothesisType`
- `proposedValue`
- `source`
- `decisionStatus`
- `finalValue`
- `proposalVersion`
- `userId`
- `decidedAt`
- `legalImpact`
- `legalReviewStatus`

권장 table은 `concept_hypothesis_decisions`다. AI 문자열을 candidate JSON에만 묻지 않는다. Seed의 LOCKED 값은 이미 final이며 decision mutation 대상이 아니다. `REJECTED`는 alternative proposal version을 만든다.

## 7. MarketAnalysisSeedSnapshot

생성 조건은 Concept selected, required hypothesis decisions complete, required Delta Legal Review complete, legal eligible이다.

본문은 다음을 포함한다.

- 원본 Seed와 optional LOCKED values
- AI Interpretation
- Selected Concept identity/solution/operation
- final targetRegion/revenueModel/price/channels/differentiators/pre-market SOM decisions
- Legal status, controls, partner/qualification requirements, prohibited variants, disclosures, official Evidence references

Market 외부 module의 유일한 정식 입력이다. `planning_change_proposals`, `planning_change_decisions`, `planning_snapshots`, `finalized_planning_snapshots`는 V2 active contract가 아니다.

## 8. TechOps와 Finance

권장 persistence:

- `tech_ops_input_preparations`
- `tech_ops_input_snapshots`
- `financial_input_preparations`
- `financial_input_snapshots`

TechOps preparation은 제품/서비스 사양, 출시일, 보유 인력·자산, 고정운영비, 초기투자, 3개년 목표와 사용자 결정이 필요한 AI 제안을 보존한다. Finance preparation은 TechOps provenance를 보존하고 고정비, 초기투자, 목표 metric, CAC 구성값, 필요한 조건부 단위원가를 보존한다. Evidence link는 AI hypothesis와 분리한다.

## 9. MarketingSourceSnapshot

본문은 Selected Concept, final accepted hypotheses, Legal Result, `allowedClaims`, `prohibitedClaims`, `requiredDisclosures`, communication 관련 `requiredControls`를 포함한다. Market Result와 `FinalizedPlanningSnapshot`은 필수 foreign key가 아니다.

## 10. API namespace와 capability

신규 API는 `/api/v3` convention을 유지하되 기존 route를 조사해 중복 route를 만들지 않는다.

### Seed

- current seed 조회
- seed input 생성·수정
- safety/interpret 실행
- interpreted/user field 수정
- interpretation 확인

### Concept

- factory 시작
- run/slot/concept 조회
- comparison 조회
- 선택 생성·조회

### Hypothesis와 Delta Legal

- 선택 Concept hypothesis 조회
- accept, edit-and-accept, reject/request-alternative
- legal-sensitive 변경의 Delta Legal Review queue/query

### Snapshot과 외부 module

- Market Seed finalize/current snapshot
- Market handoff/run/result
- TechOps preparation PATCH, proposal decision, snapshot finalize
- Finance preparation PATCH, proposal decision, snapshot finalize
- Marketing Source finalize/query

## 11. Domain error

전제조건은 403이나 기술 오류 대신 안전한 domain response로 표현한다.

```json
{
  "code": "MARKET_SEED_SNAPSHOT_NOT_READY",
  "userMessage": "선택한 컨셉의 가설 결정을 완료해 주세요.",
  "nextAction": {"label": "가설 확인", "route": "/concepts/compare"}
}
```

주요 code에는 `SAFETY_BLOCKED`, `LOCKED_CONSTRAINT_INVALID`, `DUPLICATE_CONCEPT`, `INSUFFICIENT_DISTINCT_CONCEPTS`, `DELTA_LEGAL_REVIEW_REQUIRED`, `MARKET_SEED_SNAPSHOT_NOT_READY`가 포함된다.

## 12. Concept 실행 상태

Run은 `QUEUED`, `GENERATING`, `VALIDATING`, `REPLACING`, `COMPLETED`, `NEEDS_INPUT`, `FAILED`, `STALE`을 사용한다. Slot은 schema, origin, distinctness, legal, redesign, replacement 단계를 표현하되 provider failure를 영속 Slot status로 추가하지 않는다.

Attempt error classification:

- `SCHEMA_INVALID`
- `TRANSIENT_PROVIDER_FAILURE`
- `PERMANENT_PROVIDER_FAILURE`
- `ORIGIN_INVALID`
- `LOCKED_CONSTRAINT_INVALID`
- `DUPLICATE_CONCEPT`
- `LEGAL_REDESIGN_REQUIRED`
- `LEGAL_REJECTED`
- `INSUFFICIENT_INFORMATION`
- `INTERNAL_EXECUTION_ERROR`

transient retry 1회, schema repair 1회, legal redesign 1회, replacement round 2회, 전체 inspected candidate 15개로 bounded한다. permanent provider failure는 retry 불가 terminal failure다.

## 13. 보안과 immutability

Snapshot과 Event에는 필요한 구조만 포함한다. Prompt, provider body/raw error, 사용자 전체 원문, 첨부 원문, secret, authorization, stack trace를 저장·노출하지 않는다. Terminal TaskRun/JobEvent history는 새 Action으로 덮어쓰거나 재사용하지 않는다.
