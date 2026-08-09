# NEW PIPELINE DATA MODEL AND API CONTRACT — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다.

## 1. 공통 값 계약

```text
ValueSource = USER_INPUT | USER_CONFIRMED | AI_DERIVED | AI_HYPOTHESIS | CONCEPT_GENERATED | ANALYSIS_RESULT
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

자유문장 commitment 확인/수정확인/OPEN 복귀가 canonical field를 실제 변경하면 새 `IDEA_BRIEF_DERIVATION`의 `FINAL_SYNTHESIS` execution을 생성한다. 응답은 `DERIVING + activeJobId`이며 동일 command replay만 같은 실행을 재사용할 수 있다.

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

현재 `jurisdiction=KR`만 지원한다. deterministic resolver가 `KR | UNSUPPORTED`를 반환하며 UNKNOWN/AMBIGUOUS도 `UNSUPPORTED`다. active Concept Factory의 `NEEDS_FACTS`는 `LEGAL_EXTERNAL_FACT_UNRESOLVED` rejection/replacement로 저장하고 run/slot을 actionable `NEEDS_INPUT`으로 만들지 않는다.

## 6. ConceptHypothesisDecision

별도 domain/table로 최소 다음을 저장한다.

- `conceptId`
- `hypothesisType` (`TARGET_REGION`, `REVENUE_MODEL`, `PRICE`, `CHANNELS`, `DIFFERENTIATORS`, `PRE_MARKET_SOM_SHARE`, `PRE_MARKET_SOM`)
- `proposedValue`
- `source`
- `decisionStatus`
- `finalValue`
- `proposalVersion`
- `userId`
- `decidedAt`
- `legalImpact`
- `legalReviewStatus`

권장 table은 `concept_hypothesis_decisions`다. AI 문자열을 candidate JSON에만 묻지 않는다. Seed의 `USER_INPUT 또는 USER_CONFIRMED + LOCKED` 값은 이미 final이며 decision mutation 대상이 아니다. `REJECTED`는 alternative proposal version을 만든다.

`concept_selections` query는 `activeActionTaskRunId`, `pendingActionType`, `pendingHypothesisType`, `actionStatus`, `safeActionError`와 최신 decision 목록을 함께 반환한다. `actionStatus`는 `IDLE | QUEUED | RUNNING | SUCCEEDED | FAILED | LEGAL_INELIGIBLE | STALE_ACTION_RESULT`다. Query가 정본이고 JobEvent/SSE는 재조회 신호다.

`REQUEST_ALTERNATIVE`와 baseline이 바뀐 legal-sensitive `ACCEPT | EDIT_AND_ACCEPT`는 각각 `CONCEPT_HYPOTHESIS_ALTERNATIVE`, `CONCEPT_DELTA_LEGAL_REVIEW` TaskRun을 생성하고 HTTP `202`를 반환한다. `Idempotency-Key`가 필수이며 response는 최소 `taskRunId`, `jobId`, `status`, `actionType`, `hypothesisType`, `proposalVersion`을 포함한다. provider가 필요 없는 baseline 동일 accept와 non-legal edit는 동기 완료한다.

Alternative 실행 중에는 기존 decision을 `REJECTED`로 바꾸지 않는다. worker 성공 commit에서만 기존 proposal을 reject하고 다른 값의 `proposalVersion + 1`, `AI_HYPOTHESIS`, `ALTERNATIVE_PROPOSED` decision을 만든다. Delta Legal은 `IMPLEMENTABLE | IMPLEMENTABLE_WITH_CONTROLS`에서만 final value를 accept한다. `REDESIGNABLE | REJECTED | NEEDS_FACTS`는 TaskRun execution 성공과 별개인 `LEGAL_INELIGIBLE` domain outcome이며 final value를 확정하지 않는다. provider/network/schema 실패는 TaskRun `FAILED`이고 기존 decision을 보존한다.

worker commit은 current selection, current decision ID/version, pending TaskRun ID, concept ID/hash를 다시 검증한다. 불일치 결과는 `STALE_ACTION_RESULT`로 terminal 처리하며 최신 decision을 덮어쓰지 않는다.

`concept_selections` query는 `activeActionTaskRunId`, `pendingActionType`, `pendingHypothesisType`, `actionStatus`, `safeActionError`와 최신 decision 목록을 함께 반환한다. `actionStatus`는 `IDLE | QUEUED | RUNNING | SUCCEEDED | FAILED | LEGAL_INELIGIBLE | STALE_ACTION_RESULT`다. Query가 정본이고 JobEvent/SSE는 재조회 신호다.

`REQUEST_ALTERNATIVE`와 baseline이 바뀐 legal-sensitive `ACCEPT | EDIT_AND_ACCEPT`는 각각 `CONCEPT_HYPOTHESIS_ALTERNATIVE`, `CONCEPT_DELTA_LEGAL_REVIEW` TaskRun을 생성하고 HTTP `202`를 반환한다. `Idempotency-Key`가 필수이며 response는 최소 `taskRunId`, `jobId`, `status`, `actionType`, `hypothesisType`, `proposalVersion`을 포함한다. provider가 필요 없는 baseline 동일 accept와 non-legal edit는 동기 완료한다.

Alternative 실행 중에는 기존 decision을 `REJECTED`로 바꾸지 않는다. worker 성공 commit에서만 기존 proposal을 reject하고 다른 값의 `proposalVersion + 1`, `AI_HYPOTHESIS`, `ALTERNATIVE_PROPOSED` decision을 만든다. Delta Legal은 `IMPLEMENTABLE | IMPLEMENTABLE_WITH_CONTROLS`에서만 final value를 accept한다. `REDESIGNABLE | REJECTED | NEEDS_FACTS`는 TaskRun execution 성공과 별개인 `LEGAL_INELIGIBLE` domain outcome이며 final value를 확정하지 않는다. provider/network/schema 실패는 TaskRun `FAILED`이고 기존 decision을 보존한다.

worker commit은 current selection, current decision ID/version, pending TaskRun ID, concept ID/hash를 다시 검증한다. 불일치 결과는 `STALE_ACTION_RESULT`로 terminal 처리하며 최신 decision을 덮어쓰지 않는다.

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

TechOps Evidence의 active path는 자유 `artifactRef` 문자열을 받지 않는다. `project_evidence_artifacts`가 project ownership, UUID storage key/filename, sanitized original filename, canonical media type, size, SHA-256, creator와 soft-delete lifecycle을 보존한다. 실제 bytes는 `ObjectStoragePort`의 local/S3 구현에 저장하고 DB에는 넣지 않는다. 업로드/다운로드는 모두 project ownership과 deleted 상태를 검증하며, extension allowlist와 content signature를 함께 검사한다. Evidence reference 삭제는 artifact 삭제와 별개다.

TechOps Query는 `proposalGenerationStatus`, `activeProposalTaskRunId`, `safeError`와 각 proposal field의 `pendingAlternativeTaskRunId`를 반환한다. 초기화는 preparation을 저장한 뒤 누락 제안이 있으면 `TECH_OPS_PROPOSAL` batch TaskRun 하나를 queue하고 즉시 preparation을 반환한다. 이미 `QUEUED | READY | RUNNING`인 task가 있으면 중복 생성하지 않는다.

`REJECT_AND_REQUEST_ALTERNATIVE`는 `Idempotency-Key`가 필요한 `202` command다. input은 preparation/field/current version/rejected proposal/source Market Seed ID·hash/current revision을 보존한다. 성공 worker만 `proposalVersion + 1`, `AI_HYPOTHESIS`, `PROPOSED`, `finalValue=null`을 commit한다. canonical 또는 의미상 동일한 대안은 거부한다. provider failure 뒤 `EDIT_AND_ACCEPT` 직접 입력과 새 command key retry를 허용한다.

Finance initialization은 TechOps 상속값, 빈 field, 설명과 예시만 저장하며 provider를 호출하거나 TaskRun을 자동 생성하지 않는다. 각 estimate 대상 field Query는 `estimateStatus`, `activeTaskRunId`, `proposalValue`, `proposalVersion`, `safeError`를 반환한다. `newCustomerCount`와 TechOps에서 read-only로 상속된 값은 estimate command 대상이 아니다.

`POST /api/v3/projects/{projectId}/finance/preparation/assistance/{fieldKey}/generate`는 `Idempotency-Key`가 필요한 `202` command이며 `FINANCE_ESTIMATE`를 queue한다. input은 preparation ID, field, proposal version, 대안이면 rejected proposal, source TechOps Snapshot ID/hash, expected preparation revision과 현재 관련 재무 context를 고정한다. `REQUEST_ALTERNATIVE`도 새 TaskRun을 만드는 `202` command이고 성공 전 기존 proposal을 보존한다. `ACCEPT | EDIT_AND_ACCEPT`는 각각 `AI_ESTIMATE + ACCEPTED`, `USER_INPUT + USER_EDITED_ACCEPTED`를 동기로 commit한다. worker 실패와 stale 결과는 final financial field 및 기존 proposal을 덮어쓰지 않는다. Snapshot은 `PROPOSED` assistance value를 입력값으로 포함하지 않으며 CAC는 서버 deterministic formula만 사용한다.

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

가설 action endpoint의 provider-backed 응답은 `202 Accepted`, provider-free 응답은 `200 OK`다. 동일 command key 재전송만 기존 execution을 replay하고 사용자가 다시 시도하면 새 command key와 새 TaskRun을 사용한다.

### Snapshot과 외부 module

- Market Seed finalize/current snapshot
- Market handoff/run/result
- TechOps preparation PATCH, proposal decision, snapshot finalize
- Project Evidence Artifact multipart upload/download/delete and TechOps evidence reference registration
- Finance preparation PATCH, lazy estimate generate, proposal decision, snapshot finalize
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

주요 code에는 `SAFETY_BLOCKED`, `LOCKED_CONSTRAINT_INVALID`, `DUPLICATE_CONCEPT`, `INSUFFICIENT_DISTINCT_CONCEPTS`, `LEGAL_JURISDICTION_UNSUPPORTED`, `LEGAL_EXTERNAL_FACT_UNRESOLVED`, `DELTA_LEGAL_REVIEW_REQUIRED`, `MARKET_SEED_SNAPSHOT_NOT_READY`가 포함된다.

## 12. Concept 실행 상태

Run은 `QUEUED`, `GENERATING`, `VALIDATING`, `REPLACING`, `COMPLETED`, `NEEDS_INPUT`, `FAILED`, `STALE`을 사용한다. Slot은 schema, origin, distinctness, legal, redesign, replacement 단계를 표현하되 provider failure를 영속 Slot status로 추가하지 않는다.

Attempt error classification:

- `SCHEMA_INVALID`
- `REQUEST_CONTRACT_INVALID`
- `TRANSIENT_PROVIDER_FAILURE`
- `PERMANENT_PROVIDER_FAILURE`
- `ORIGIN_INVALID`
- `LOCKED_CONSTRAINT_INVALID`
- `DUPLICATE_CONCEPT`
- `LEGAL_REDESIGN_REQUIRED`
- `LEGAL_REJECTED`
- `LEGAL_EXTERNAL_FACT_UNRESOLVED`
- `INSUFFICIENT_INFORMATION`
- `INTERNAL_EXECUTION_ERROR`

transient retry, schema repair, legal redesign, replacement round, 전체 inspected candidate는 구현 limit로 bounded한다. `REQUEST_CONTRACT_INVALID`는 Backend가 AI 내부 입력 계약을 위반한 run-global fatal 오류이므로 남은 Slot 호출을 즉시 중단하고 같은 Run의 resume을 허용하지 않는다. permanent provider/config failure는 retry 불가 terminal failure다.

## 13. 보안과 immutability

Snapshot과 Event에는 필요한 구조만 포함한다. Prompt, provider body/raw error, 사용자 전체 원문, 첨부 원문, secret, authorization, stack trace를 저장·노출하지 않는다. Terminal TaskRun/JobEvent history는 새 Action으로 덮어쓰거나 재사용하지 않는다.
