# Concept Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Concept generation, candidate identity and immutable version schema
- Supersedes: Fixed 12-section plan model
- Implementation Status: NOT_STARTED

## Aggregate ownership

ConceptGenerationRun은 Project 소유 Run이고 ConceptCandidate를 composition으로 소유한다. ConceptCandidate는 지속되는 후보 identity이며 ConceptVersion history와 current-version pointer를 소유한다. 모든 resource는 Project owner scope 대상이다.

## ConceptGenerationRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | generation run identifier; 정확히 한 Project |
| Input references | exact IdeaVersion과 `PASS` 또는 `PASS_WITH_CONDITIONS`인 exact LegalReviewRun |
| Cardinality | IdeaVersion `1:N` Run; LegalReviewRun `1:N` Run; Run `1:N` Candidate |
| Input snapshot | IdeaVersion, LegalReviewRun, legal conditions, generation contract version과 hash |
| Task binding | 요청 수락 후 정확히 하나의 TaskRun; retry는 같은 TaskRun의 새 Attempt, rerun은 새 GenerationRun/TaskRun |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection과 `CURRENT`/`STALE`을 별도 평가; 독립 execution 상태를 소유하지 않음 |
| Time | 생성, 시작, 완료, 마지막 갱신 시각 |
| Concurrency | run transition과 candidate adoption에 optimistic concurrency 필요 |
| Provenance | TaskRun/TaskResult, AI proposal origin, exact upstream refs |
| Delete | archive/history 보존; candidate/downstream 참조 중 hard delete 금지 |
| Uniqueness | run identifier; input/idempotency 중복은 TaskRun 정책으로 방지 |

Run 성공은 exact input에 대한 검증·채택 TaskResult가 존재하는 AI 제안 생성 완료일 뿐 candidate 또는 ConceptVersion의 사용자 채택을 뜻하지 않는다. TaskRun `SUCCEEDED`만으로 business result 성공을 판단하지 않는다.

## ConceptCandidate

| Concern | Logical contract |
|---|---|
| Identifier/owner | candidate identifier; ConceptGenerationRun composition, Project scope 상속 |
| Semantics | 후보 identity, 생성 제안 원본, 사용자 표시 순서/label 방향, current ConceptVersion reference |
| Cardinality | GenerationRun `1:N`; Candidate `1:N` ConceptVersion |
| Mutability | identity와 current pointer/lifecycle은 mutable; original generated proposal은 immutable |
| Version strategy | 사용자 또는 AI 수정은 새 ConceptVersion; candidate identifier는 유지 |
| Lifecycle | `AVAILABLE`, `REJECTED`, `ARCHIVED`; validity current/stale 별도 |
| Time | 생성과 마지막 갱신 시각 |
| Concurrency | current-version/lifecycle 변경에 필요 |
| Provenance | source GenerationRun/TaskResult; AI proposal임을 표시 |
| Delete | archive 우선; assessment/decision 참조가 있으면 history 보존 |
| Uniqueness | candidate identifier 유일; GenerationRun 안의 display identity 중복 방지 방향 |

Candidate의 `REJECTED` lifecycle은 ShortlistDecision의 rejected reference를 대신하지 않는다. 사용자 shortlist/selection은 별도 immutable decision record다.

## ConceptVersion

| Concern | Logical contract |
|---|---|
| Identifier/owner | ConceptVersion identifier; ConceptCandidate composition, Project scope 상속 |
| Cardinality/version | Candidate `1:N`; version number는 Candidate 안에서 유일·단조 증가, current 최대 하나 |
| Required semantics | title, target problem, target user/context, value proposition, solution outline, differentiators, constraints, assumptions, evidence needs |
| Upstream references | exact source IdeaVersion, accepted LegalReviewRun과 ConceptGenerationRun |
| Created by | `AI_GENERATED` 또는 `USER_EDITED`; actor와 source version 방향 |
| AI/user boundary | AI-generated proposal과 user edit/confirmation을 별도 provenance로 보존; AI 결과가 selection을 의미하지 않음 |
| Mutability | immutable; 정정은 새 version |
| Lifecycle | `DRAFT`, `CONFIRMED`, `SUPERSEDED`; validity current/stale 별도 |
| Time | 생성 시각, 사용자 확인 시각 방향 |
| Concurrency | content에는 불필요; Candidate current pointer 갱신에 필수 |
| Input snapshot | exact upstream identifiers, contract version과 hash |
| Delete | archive/history 보존; assessment/decision/persona/report 참조 중 hard delete 금지 |
| Uniqueness | Candidate + version number; Candidate current confirmed version 최대 하나 |

모든 QuickAssessmentRun, DetailedAnalysisRun, ShortlistDecision과 ConceptSelection은 exact ConceptVersion identifier를 참조한다. Candidate current pointer를 동적으로 따라가지 않는다.

## Stale rules

- 새 current IdeaVersion 또는 accepted LegalReviewRun은 이전 upstream 기반 GenerationRun, Candidate와 ConceptVersion chain을 `STALE`로 만든다.
- 새 ConceptVersion은 이전 version을 삭제하지 않는다. 이전 version 기반 Quick/Detailed/Decision과 transitive downstream만 stale 처리한다.
- 한 candidate의 version 변경은 다른 candidate의 assessment chain을 stale로 만들지 않는다.
