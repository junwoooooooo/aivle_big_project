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

Slot: QUEUED, GENERATING, GENERATED, SCHEMA_INVALID, PROVIDER_FAILURE, VALIDATING_ORIGIN, VALIDATING_LEGAL, REDESIGNING, REPLACING, ELIGIBLE, REJECTED, NEEDS_INPUT, FAILED, STALE

Attempt: INITIAL, REPAIR, REDESIGN, REPLACEMENT

## 8. 법률 상태

IMPLEMENTABLE, IMPLEMENTABLE_WITH_CONTROLS, NEEDS_FACTS, REDESIGNABLE, REJECTED

## 9. 보안

Snapshot에는 필요한 정보만 포함하고, 사용자 원문·첨부 전체·Prompt·Provider Body·Secret은 Event와 Audit Metadata에 저장하지 않는다.
