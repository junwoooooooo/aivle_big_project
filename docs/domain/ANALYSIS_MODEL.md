# Analysis Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Quick assessment, shortlist, detailed analysis and final concept selection
- Supersedes: Legacy feasibility and financial analysis models
- Implementation Status: NOT_STARTED

## Ownership and common input

QuickAssessmentRun, ShortlistDecision, DetailedAnalysisRun과 ConceptSelection은 Project가 소유하고 owner scope를 적용한다. Run은 AI 실행/proposal이고 Decision/Selection은 사용자 행위다. 모든 record는 exact ConceptVersion을 참조하고 dynamic current pointer를 사용하지 않는다.

이 문서와 후속 구현에서 저비용 공통 평가는 `QuickAssessmentRun`이라는 canonical logical name만 사용한다.

Shared core input snapshot은 IdeaVersion, accepted LegalReviewRun, exact ConceptVersion, facts/assumptions/constraints와 provenance hash 방향을 포함한다. Quick와 Detailed는 이 snapshot reference를 공유할 수 있지만 analysis-specific input/output은 분리한다.

## QuickAssessmentRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | QuickAssessmentRun identifier; Project 소유 |
| Cardinality | ConceptVersion `1:N` QuickAssessmentRun; 각 candidate의 exact version에 실행 가능 |
| Input | exact ConceptVersion, shared core snapshot, Quick-specific input, contract version/hash |
| Task binding | 요청 수락 후 1:1 TaskRun; retry는 같은 TaskRun의 Attempt, rerun은 새 QuickAssessmentRun/TaskRun |
| Result semantics | 비교 dimension별 assessment, evidence/provenance, uncertainty, warnings, evidence needs 방향 |
| Decision boundary | AI proposal이며 shortlist 선택이나 사용자 결정을 생성하지 않음 |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection; adopted result/domain validation과 `CURRENT`/`STALE`은 별도 |
| Time/concurrency | 생성·시작·완료·갱신 시각; lifecycle/adoption에 optimistic concurrency |
| Provenance | TaskRun/TaskResult, exact input snapshot과 model-neutral contract version |
| Delete | history 보존; ShortlistDecision/report가 참조하면 hard delete 금지 |
| Uniqueness | run identifier; idempotency/input hash 중복은 TaskRun 정책으로 방지 |

Quick는 shortlist 후보만이 아니라 비교 대상인 모든 current ConceptCandidate의 exact ConceptVersion에 적용하는 방향이다. 실패한 candidate run을 성공으로 간주하거나 누락을 숨기지 않는다.

## ShortlistDecision

| Concern | Logical contract |
|---|---|
| Identifier/owner | ShortlistDecision identifier; Project composition |
| Cardinality | Project `1:N` decision history; Decision `N:M` ConceptVersion; selected version 하나 이상 |
| Semantics | selected ConceptVersion refs, rejected/considered refs, rationale, decision actor, decision timestamp |
| Input snapshot | 검토한 QuickAssessmentRun/result와 exact candidate version 집합 |
| Decision boundary | USER decision. AI rank/recommendation은 evidence일 뿐 selected set을 자동 확정하지 않음 |
| Mutability/version | immutable decision history; 변경은 새 decision record |
| Lifecycle | `ACTIVE`, `SUPERSEDED`; current/stale 별도 |
| Time/concurrency | 결정 생성 시각; Project current shortlist pointer 변경에 optimistic concurrency |
| Delete | history 보존; DetailedAnalysisRun이 참조하면 hard delete 금지 |
| Uniqueness | current active shortlist 최대 하나; 동일 decision 안의 ConceptVersion 중복 금지 |

새 ShortlistDecision은 이전 결정을 덮어쓰지 않는다. 제외된 ConceptVersion의 기존 DetailedAnalysisRun은 삭제하지 않고 current shortlist 근거로는 `STALE` 처리한다. 다시 shortlist에 포함돼도 자동 current 복구하지 않고 재검증 capability를 요구한다.

## DetailedAnalysisRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | DetailedAnalysisRun identifier; Project 소유 |
| Eligibility | current ShortlistDecision에 포함된 exact ConceptVersion만 입력 가능 |
| Cardinality | ConceptVersion `1:N` runs; 하나의 shortlist decision/reference와 analysis type을 고정 |
| Input | shared snapshot reference와 analysis-specific input; Quick result는 optional provenance일 뿐 confirmed fact가 아님 |
| Task binding | 요청 수락 후 1:1 TaskRun; retry는 같은 TaskRun의 Attempt, rerun은 새 DetailedAnalysisRun/TaskRun |
| Analysis type | 시장, BM, 기술운영, 재무 등 controlled type 방향; 각 type별 input/output contract 분리 |
| Result | type-specific output, evidence, assumptions, uncertainty, research needs, AI explanation 방향 |
| Financial boundary | 결정론적 계산 input/formula/result와 AI 설명·해석을 별도 provenance/result section으로 구분 |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection; adopted result/domain validation과 `CURRENT`/`STALE`은 별도 |
| Time/concurrency | 생성·시작·완료·갱신 시각; lifecycle/adoption에 optimistic concurrency |
| Provenance | TaskRun/TaskResult, exact ShortlistDecision/ConceptVersion/shared snapshot |
| Delete | selection/report 근거면 보존; archive 방향 |
| Uniqueness | run identifier; 동일 analysis request 중복은 idempotency key로 방지 |

Quick output을 Detailed의 source fact로 자동 승격하지 않는다. 재사용할 경우 `AI_PROPOSAL` 또는 prior analysis evidence로 명시하고 Detailed contract가 별도로 검증한다.

Quick/Detailed 모두 TaskRun `SUCCEEDED`만으로 Domain Run 성공을 확정하지 않는다. Exact input과 `ADOPTED` TaskResult, analysis-specific validation을 함께 확인하며 late/duplicate result는 기존 adopted result를 교체하지 않는다.

## ConceptSelection

| Concern | Logical contract |
|---|---|
| Identifier/owner | ConceptSelection identifier; Project composition |
| Cardinality | Project `1:N` selection history; selection마다 exact ConceptVersion 하나; current 최대 하나 |
| Semantics | selected ConceptVersion, rationale, alternatives considered, selected-by user actor, decision timestamp |
| Input snapshot | exact ShortlistDecision, reviewed DetailedAnalysisRun/result 집합과 AI recommendation refs |
| Decision boundary | USER final decision. AI recommendation과 separate reference/category |
| Mutability/version | immutable selection history; 변경은 새 ConceptSelection |
| Lifecycle | `ACTIVE`, `SUPERSEDED`; current/stale 별도 |
| Time/concurrency | 결정 생성 시각; Project current selection pointer 갱신에 optimistic concurrency |
| Delete | Persona/Marketing/Report가 참조하면 보존 |
| Uniqueness | current active selection 최대 하나; selection당 selected version 정확히 하나 |

PersonaStudy는 exact ConceptSelection과 그 selected ConceptVersion을 모두 고정한다. 새 ConceptSelection은 이전 selection 기반 PersonaStudy, Marketing과 FinalReport chain을 `STALE`로 만든다.
