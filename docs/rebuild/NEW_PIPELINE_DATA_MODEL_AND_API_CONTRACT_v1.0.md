# NEW PIPELINE DATA MODEL AND API CONTRACT v1.0

## 1. 정본

- IdeaBriefSnapshot
- LegalContextPack
- ConceptFactoryRun
- EligibleConcept
- ConceptLegalAssessment
- SelectedConceptSnapshot
- MarketAnalysisResultReference
- PlanningChangeProposal
- FinalizedPlanningSnapshot
- MarketingContentSnapshot

## 2. 핵심 테이블

### Idea
- idea_briefs
- idea_brief_fields
- idea_questions
- idea_answers

### Legal
- legal_context_packs
- legal_evidence
- legal_context_evidence_links

### Concept
- concept_factory_runs
- concept_slots
- concept_attempts
- concepts
- concept_legal_assessments
- concept_legal_evidence_links
- concept_rejection_summaries
- concept_selections

### Planning
- planning_change_proposals
- planning_change_decisions
- planning_snapshots
- finalized_planning_snapshots

### Integration
- module_handoffs
- module_runs
- module_results
- module_events

### Marketing
- marketing_content_requests
- marketing_contents
- marketing_content_revisions
- marketing_assets

## 3. Versioning

모든 Snapshot은 ID, Sequence, Parent Snapshot, Hash, CreatedAt, CreatedBy를 가진다. UI 이름은 의미 기반 Label을 별도로 저장한다.

## 4. Stale

하위 결과는 입력 Snapshot ID와 Hash를 저장한다. 현재 정본과 다르면 STALE이지만 조회는 가능하다.

## 5. API Namespace

신규 `/api/v3`만 사용한다.

### Idea
- GET idea-brief
- POST derive
- PATCH fields
- POST answers
- POST confirm

### Concept
- POST concept-factory-runs
- GET current/run/slots
- POST resume/retry
- GET concepts

### Selection
- POST concept-selections
- GET current

### Planning
- GET current
- GET change-proposals
- POST proposal decisions
- POST finalize

### Modules
- POST module-handoffs
- GET module-runs
- GET module-runs/{id}
- POST internal callbacks/events

### Marketing
- POST/GET marketing-contents
- GET/PATCH content
- POST regenerate/finalize

## 6. Domain Error

```json
{
  "code": "SOURCE_SNAPSHOT_REQUIRED",
  "userMessage": "최종 확정 기획이 필요합니다.",
  "nextAction": {"label": "시장분석 결과 확인", "route": "/market"}
}
```

403과 기술 오류로 전제조건을 표현하지 않는다.

## 7. Concept Factory 상태

Run: QUEUED, GENERATING, VALIDATING, REPLACING, COMPLETED, NEEDS_INPUT, FAILED, STALE

Slot: QUEUED, GENERATING, GENERATED, SCHEMA_INVALID, VALIDATING_ORIGIN, VALIDATING_LEGAL, REDESIGNING, REPLACING, ELIGIBLE, REJECTED, NEEDS_INPUT, FAILED, STALE

Attempt: INITIAL, REPAIR, REDESIGN, REPLACEMENT

### Attempt Error Classification

Provider failure는 Concept Attempt 실행 오류이며 Concept Slot의 영속 상태가 아니다. 공식 분류는 다음과 같다.

- `SCHEMA_INVALID`
- `TRANSIENT_PROVIDER_FAILURE`
- `PERMANENT_PROVIDER_FAILURE`
- `ORIGIN_INVALID`
- `LEGAL_REDESIGN_REQUIRED`
- `LEGAL_REJECTED`
- `INSUFFICIENT_INFORMATION`
- `INTERNAL_EXECUTION_ERROR`

전이 규칙:

1. `TRANSIENT_PROVIDER_FAILURE`이고 단일 retry가 남아 있으면 Slot은 현재 실행 상태를 유지하고 동일 Slot을 재시도한다. 소진 시 `REPLACING`으로 전이한다.
2. `PERMANENT_PROVIDER_FAILURE`이면 Slot과 Run을 `FAILED`로 전이하고 `retryable=false`로 종료한다.
3. `SCHEMA_INVALID`이면 Slot을 `SCHEMA_INVALID`로 전이하고 `REPAIR` Attempt 1회를 허용한다. 재실패 시 `REPLACING`으로 전이한다.
4. Provider failure를 Slot registry 또는 사용자 진행 상태로 노출하지 않는다.

## 8. 법률 상태

IMPLEMENTABLE, IMPLEMENTABLE_WITH_CONTROLS, NEEDS_FACTS, REDESIGNABLE, REJECTED

## 9. 보안

Snapshot에는 필요한 정보만 포함하고, 사용자 원문·첨부 전체·Prompt·Provider Body·Secret은 Event와 Audit Metadata에 저장하지 않는다.
