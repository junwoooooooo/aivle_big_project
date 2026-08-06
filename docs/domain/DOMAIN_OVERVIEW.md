# Target Domain Overview

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Logical aggregate ownership, identifiers, cardinality, version references and stale propagation
- Supersedes: Legacy backend domain and ERD documents
- Implementation Status: NOT_STARTED

이 문서는 implementation-ready logical schema를 정의한다. 이름은 업무 의미를 나타내며 SQL type, physical column/index, JPA annotation, cascade 구현 또는 migration DDL을 뜻하지 않는다.

## Global invariants

- `Project`가 Target Workflow의 최상위 owner-scoped aggregate다. 모든 하위 resource는 직접 또는 소유 chain을 통해 정확히 하나의 Project와 owner scope를 가진다.
- logical identifier는 aggregate 안에서 안정적이며 version이나 retry 때 재사용하지 않는다. 외부에 노출할 identifier 형식은 public API 계약에서 정한다.
- 모든 record는 생성 시각을 가진다. mutable record는 마지막 갱신 시각과 optimistic concurrency revision을 가진다. immutable record의 업무 내용은 생성 후 바꾸지 않으며 정정은 새 version/run/decision으로 남긴다.
- lifecycle status와 validity는 분리한다. 실행이 `SUCCEEDED`여도 upstream 변경 후 validity는 `STALE`일 수 있다.
- AI 실행은 `Run → Attempt → Result`, 불변 업무 내용은 `Version`, 사용자 선택은 `Decision` 또는 `Selection`, 현재 여정 표시는 `Stage`, 실행 가능 여부는 `Capability`를 사용한다.
- 모든 Run은 exact input version/reference와 input snapshot/hash를 고정한다. current pointer를 나중에 따라가도록 저장하지 않는다.
- AI-backed Domain Run은 실행 요청이 수락되면 정확히 하나의 TaskRun과 결합한다. TaskRun이 execution lifecycle의 source of truth이고 Domain Run은 exact business input, adopted business result reference, validity와 provenance를 소유한다.
- AI proposal, user-authored content, user decision, external source fact와 assumption을 provenance에서 구분한다.
- `STALE`은 물리 삭제가 아니다. history, 입력 reference와 provenance를 유지하며 current reference와 capability에서 제외한다.
- 기본 삭제는 archive/soft-delete 또는 reference 해제 방향이다. immutable evidence/version/run은 owner Project 삭제·retention 정책 전에는 직접 덮어쓰거나 cascade로 즉시 제거하지 않는다.
- 주요 uniqueness invariant는 application validation과 향후 physical constraint 양쪽에서 보호해야 하지만 구체적 index/DDL은 P3 이후 결정한다.

## Project aggregate

### Project logical schema

| Concern | Contract |
|---|---|
| Logical identifier | Project를 안정적으로 식별하는 단일 identifier |
| Aggregate owner | 인증된 Project owner user; owner 변경 지원 여부는 후속 운영 계약에서 결정 |
| Owner scope | 모든 public/domain 접근에 적용하며 cross-owner resource는 404 방향 유지 |
| Mutability | mutable root; current references, status, stage와 운영 metadata만 갱신 |
| Version strategy | Project 자체 business revision은 optimistic concurrency로 보호하고, 업무 내용 변경은 하위 immutable Version/Decision history로 남김 |
| Lifecycle status | `ACTIVE`, `ON_HOLD`, `COMPLETED`, `ARCHIVED` |
| Workflow stage | `IDEA_INTAKE`, `LEGAL_REVIEW`, `CONCEPT_BUILDING`, `CONCEPT_ANALYSIS`, `CONCEPT_SELECTION`, `VALIDATION`, `MARKETING`, `FINAL_REPORT` |
| Current references | current IdeaVersion, accepted LegalReviewRun, ShortlistDecision, ConceptSelection, PersonaStudy, MarketingWorkspaceVersion, FinalReportVersion 방향. 각 reference는 동일 Project 소속이고 `STALE`이 아니어야 함 |
| Time | 생성 시각과 마지막 갱신 시각 |
| Optimistic concurrency | 필수; status/stage/current-reference lost update 방지 |
| Deletion | `ARCHIVED` 우선. owner 삭제·retention에 따른 물리 삭제는 migration/operations Phase에서 결정 |
| Uniqueness | Project logical identifier 전역 유일; Project당 current reference 종류별 최대 하나 |

Stage는 사용자에게 현재 여정 위치를 표시하지만 capability의 source of truth가 아니다. Capability는 Project status, exact current references, resource/run status, stale validity, 사용자 gate, Service Policy를 함께 평가해 Spring이 산출한다. Project 하나는 다수 IdeaVersion, Concept 관련 record, Run, Decision과 FinalReportVersion history를 가진다.

## Logical entity ownership matrix

`Mutable`은 업무 identity/current pointer/lifecycle을 갱신할 수 있다는 뜻이고, `Immutable`은 정정 시 새 record를 만든다는 뜻이다. 모든 항목은 Project owner scope 대상이다.

| Logical type | Aggregate owner | Mutability and version strategy | Lifecycle/validity | Concurrency | Provenance and decision boundary |
|---|---|---|---|---|---|
| Project | Project | Mutable root revision | Project status + stage | Required | current references는 사용자 gate와 검증된 결과만 가리킴 |
| IdeaSource | Project | Content immutable; lifecycle mutable | received/validated/extracted/rejected/quarantined/archived | Lifecycle update required | USER source; FILE bytes/metadata는 Spring 소유 |
| IdeaSourceExtraction | IdeaSource | Immutable extraction version | succeeded/failed + current/stale | No content update | parser/version/checksum과 source reference |
| IdeaInterpretationRun | Project | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact Extraction set, 1:1 TaskRun; AI proposal |
| IdeaVersion | Project | Immutable, Project-local version sequence | confirmed/superseded + current/stale | Current pointer update required | USER_AUTHORED/AI_ASSISTED와 authenticated confirmation 구분 |
| LegalReviewRun | Project | Input immutable; adopted business result reference mutable | TaskRun state projection + legal result + current/stale | Required | exact IdeaVersion, 1:1 TaskRun과 legal sources |
| LegalFinding | LegalReviewRun | Immutable after run completion | active/superseded-by-new-run + current/stale | No content update | assumption과 confirmed source fact 구분 |
| LegalSourceReference | LegalReviewRun | Immutable observation | authoritative/degraded, freshness/currentness | No content update | MOLEG_API 또는 LEGAL_MCP; secret 제외 |
| ConceptGenerationRun | Project | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact IdeaVersion, accepted LegalReviewRun, 1:1 TaskRun |
| ConceptCandidate | ConceptGenerationRun | Mutable identity/current-version pointer; original proposal immutable | available/rejected/archived + current/stale | Required | AI proposal이며 사용자 채택과 별도 |
| ConceptVersion | ConceptCandidate | Immutable candidate-local version | draft/confirmed/superseded + current/stale | Current pointer update required | AI_GENERATED 또는 USER_EDITED provenance |
| QuickAssessmentRun | Project | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact ConceptVersion, 1:1 TaskRun; AI proposal, selection 아님 |
| ShortlistDecision | Project | Immutable decision history | active/superseded + current/stale | Current pointer update required | USER decision; AI ranking과 별도 |
| DetailedAnalysisRun | Project | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | shortlisted exact ConceptVersion, 1:1 TaskRun과 analysis provenance |
| ConceptSelection | Project | Immutable selection history | active/superseded + current/stale | Current pointer update required | USER selection; AI recommendation과 별도 |
| TaskRun | Project | Mutable workflow execution root | queued/ready/running/succeeded/failed/cancelled/timed_out | Required | subject와 exact input snapshot/hash |
| TaskAttempt | TaskRun | Mutable until terminal, then append-only | created/claimed/running/succeeded/failed/timed_out/cancelled | Required claim/lease | provider-neutral execution evidence |
| TaskResult | TaskRun | Immutable received result | received/validated/rejected/adopted | Adoption update required | exact TaskAttempt; adopted와 non-adopted evidence 구분 |
| TaskArtifact | TaskRun | Metadata/lifecycle mutable; bytes immutable | pending/available/quarantined/deleted | Required | Spring StoredFile reference only |
| PersonaStudy | Project | Mutable study root/current pointers | draft/in_progress/completed/archived + current/stale | Required | exact ConceptSelection/ConceptVersion |
| PersonaCardGenerationRun | PersonaStudy | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact Study/Selection/ConceptVersion, 1:1 TaskRun |
| PersonaCard | PersonaStudy | Mutable logical identity/current-version pointer | active/archived + current/stale | Required | source GenerationRun과 synthetic identity |
| PersonaCardVersion | PersonaCard | Immutable version record | draft/confirmed/archived + current/stale | Current version pointer required | synthetic AI-generated/user-edited content 표시 |
| PersonaInterview | PersonaStudy | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact PersonaCardVersion과 independent 1:1 TaskRun |
| InterviewSynthesis | PersonaStudy | Immutable synthesis version | AI-backed이면 TaskRun projection + current/stale | Current pointer update required | source Interview 집합; AI-backed execution은 1:1 TaskRun |
| MarketingWorkspace | Project | Mutable single logical workspace | active/on_hold/archived + current/stale | Required | current workspace version pointer |
| MarketingWorkspaceVersion | MarketingWorkspace | Immutable context version | draft/confirmed/superseded + current/stale | Current pointer update required | exact ConceptVersion와 Persona evidence snapshot |
| MarketingGenerationRun | MarketingWorkspace | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | exact WorkspaceVersion, target Persona refs, 1:1 TaskRun |
| MarketingAsset | MarketingWorkspace | Mutable logical asset/current-version pointer | active/archived + current/stale | Required | asset identity; content는 version에 존재 |
| MarketingAssetVersion | MarketingAsset | Immutable version | draft/ready/archived + current/stale | Current pointer update required | prompt/input snapshot, text 또는 Spring artifact |
| MarketingComparisonRun | MarketingWorkspace | Input immutable; adopted business result reference mutable | TaskRun state projection + current/stale | Required | 둘 이상 exact AssetVersion, 1:1 TaskRun의 상대 평가 |
| FinalReport | Project | Mutable single logical report/current pointer | draft/generating/ready/failed/archived | Required | current version은 검증된 immutable snapshot만 가리킴 |
| FinalReportVersion | FinalReport | Immutable Project-local report version | available/withdrawn + current/stale-at-source | Current pointer update required | exact upstream set, AI/user/source/assumption 구분 |

모든 mutable logical type은 생성·마지막 갱신 시각을 가진다. 모든 immutable type은 생성 시각과 provenance 생성 시각을 가지며, lifecycle 또는 current-pointer metadata를 별도 갱신할 경우 그 갱신 시각과 concurrency revision을 기록한다.

## Cardinality matrix

| From | Relationship | To | Kind | Invariant |
|---|---:|---|---|---|
| Project | 1:N | IdeaSource | Composition/history | source는 정확히 한 Project에 속함 |
| IdeaSource | 1:N | IdeaSourceExtraction | Composition/version | extraction version은 source 안에서 유일; current successful extraction 최대 하나 |
| Project | 1:N | IdeaInterpretationRun | Composition/execution history | Run은 같은 Project의 exact extraction set을 고정 |
| IdeaInterpretationRun | N:M | IdeaSourceExtraction | Reference/snapshot | Run은 extraction 하나 이상; extraction은 여러 Run의 입력 가능 |
| IdeaInterpretationRun | 1:1 | TaskRun | Execution binding | retry는 같은 TaskRun, user rerun은 새 Run/TaskRun |
| IdeaInterpretationRun | 1:0..1 | TaskResult | Adopted result reference | validated/domain-adopted result 최대 하나 |
| Project | 1:N | IdeaVersion | Composition/history | version number는 Project 안에서 유일; current 최대 하나 |
| IdeaVersion | N:M | IdeaSource | Reference | 한 version은 하나 이상 source를 참조할 수 있고 source는 여러 version의 근거가 될 수 있음 |
| IdeaInterpretationRun | 1:N | IdeaVersion | Confirmation reference | AI_ASSISTED Version은 source Run 하나 필수; USER_AUTHORED는 Run 없이 가능 |
| IdeaVersion | 1:N | LegalReviewRun | Reference/history | Run은 exact IdeaVersion 하나를 참조 |
| LegalReviewRun | 1:N | LegalFinding | Composition | finding은 정확히 한 Run 소유 |
| LegalReviewRun | 1:N | LegalSourceReference | Composition | run-level source registry |
| LegalFinding | N:M | LegalSourceReference | Reference/link | finding은 여러 source를, source는 여러 finding을 뒷받침 가능 |
| IdeaVersion | 1:N | ConceptGenerationRun | Reference/history | generation input은 exact IdeaVersion |
| LegalReviewRun | 1:N | ConceptGenerationRun | Reference/history | 각 generation은 `PASS` 또는 `PASS_WITH_CONDITIONS`인 exact LegalReviewRun 하나를 context로 사용 |
| ConceptGenerationRun | 1:N | ConceptCandidate | Composition | 성공 Run은 하나 이상 candidate 방향 |
| ConceptCandidate | 1:N | ConceptVersion | Composition/version | candidate-local version number 유일; current 최대 하나 |
| ConceptVersion | 1:N | QuickAssessmentRun | Reference/history | run마다 exact ConceptVersion 하나 |
| Project | 1:N | ShortlistDecision | Composition/decision history | current active decision 최대 하나 |
| ShortlistDecision | N:M | ConceptVersion | Decision reference | 하나 이상 selected; rejected/considered reference도 동일 decision snapshot에 보존 |
| ConceptVersion | 1:N | DetailedAnalysisRun | Reference/history | shortlisted exact version만 허용 |
| Project | 1:N | ConceptSelection | Composition/selection history | selection마다 exact version 하나; current 최대 하나 |
| ConceptSelection | 1:N | PersonaStudy | Reference/history | study는 exact selection과 selected ConceptVersion을 고정 |
| PersonaStudy | 1:N | PersonaCardGenerationRun | Composition/execution history | run은 exact Study/Selection/ConceptVersion을 고정 |
| PersonaCardGenerationRun | 1:1 | TaskRun | Execution binding | retry는 같은 TaskRun, user rerun은 새 Run/TaskRun |
| PersonaCardGenerationRun | 1:N | PersonaCard | Composition/generated identity | 성공한 Run은 Card identity 하나 이상 생성 |
| PersonaStudy | 1:N | PersonaCard | Composition/identity history | Study 안 logical persona identity |
| PersonaCard | 1:N | PersonaCardVersion | Composition/version history | version number 유일; current 최대 하나 |
| PersonaCardVersion | 1:N | PersonaInterview | Reference/history | interview는 exact card version 하나 |
| PersonaStudy | 1:N | InterviewSynthesis | Composition/version history | synthesis version은 source Interview 집합을 고정 |
| Project | 1:0..1 | MarketingWorkspace | Composition/logical singleton | Project당 logical workspace 최대 하나 |
| MarketingWorkspace | 1:N | MarketingWorkspaceVersion | Composition/version | current 최대 하나 |
| MarketingWorkspaceVersion | 1:N | MarketingGenerationRun | Reference/history | generation은 exact workspace context를 고정 |
| MarketingWorkspace | 1:N | MarketingAsset | Composition | asset logical identity |
| MarketingAsset | 1:N | MarketingAssetVersion | Composition/version | asset-local version number 유일; current 최대 하나 |
| MarketingWorkspace | 1:N | MarketingComparisonRun | Composition/history | comparison은 같은 workspace의 exact context를 사용 |
| MarketingComparisonRun | N:M | MarketingAssetVersion | Reference/snapshot | 비교당 둘 이상 exact version; 동일 asset version 중복 금지 |
| Project | 1:0..1 | FinalReport | Composition/logical singleton | Project당 logical report 최대 하나 |
| FinalReport | 1:N | FinalReportVersion | Composition/version history | report version number 유일; current 최대 하나 |
| Project | 1:N | TaskRun | Composition/execution history | subject는 같은 Project resource여야 함 |
| AI-backed Domain Run | 1:1 | TaskRun | Execution binding | 요청 수락 후 정확히 하나; user rerun은 양쪽을 새로 생성 |
| TaskRun | 1:N | TaskAttempt | Composition/retry history | attempt number는 TaskRun 안에서 유일 |
| TaskAttempt | 1:0..N | TaskResult | Composition/response evidence | 지연·중복 response 보존; adopted result는 attempt당 최대 하나 |
| TaskResult | 1:0..N | TaskArtifact | Composition/reference | JSON-only 정상 결과는 artifact가 없을 수 있고, 존재하면 Spring-owned StoredFile metadata만 참조 |

AI-backed Domain Run 범위는 IdeaInterpretationRun, LegalReviewRun, ConceptGenerationRun, QuickAssessmentRun, DetailedAnalysisRun, PersonaCardGenerationRun, PersonaInterview, AI 실행으로 생성되는 InterviewSynthesis, MarketingGenerationRun, MarketingComparisonRun과 후속 AI-backed Final Report Run이다. 모든 TaskRun이 Domain Run을 필요로 하지는 않으며 platform task도 허용한다. 어느 경우든 TaskRun subject는 허용된 logical type이고 같은 Project owner scope여야 한다.

## Version and current-reference rules

- Version number는 해당 logical parent 범위에서 단조 증가하고 재사용하지 않는다. 삭제나 archive 후에도 번호를 재배정하지 않는다.
- current reference 변경은 해당 Project owner scope, terminal/validation status와 non-stale 조건을 확인하고 optimistic concurrency로 보호한다.
- Run과 Decision은 생성 당시 exact Version/Run/Result identifier를 참조한다. `current`를 동적으로 해석해 과거 결과 의미를 바꾸지 않는다.
- 사용자 수정은 IdeaVersion, ConceptVersion, PersonaCard, MarketingWorkspaceVersion/AssetVersion 또는 FinalReportVersion을 새로 만든다. 기존 immutable record를 수정하지 않는다.
- 사용자 Decision/Selection 변경은 새 history record를 만들고 이전 record를 `superseded`로 표시하며 관련 downstream stale propagation을 발생시킨다.

## Stale propagation matrix

| Trigger | Affected downstream | Validity/history rule | Current pointer rule | Capability effect |
|---|---|---|---|---|
| 새 current IdeaVersion | 이전 IdeaVersion 기반 LegalReviewRun, ConceptGenerationRun과 모든 후속 assessment/decision/persona/marketing/report | 이전 chain을 유지하고 `STALE`; 새 chain은 새 IdeaVersion에서 시작 | 이전 legal 및 모든 downstream current pointer 해제 | 새 legal review 전 concept 이후 capability 차단 |
| 새 current IdeaSourceExtraction 또는 사용자 source 변경 | 이전 extraction set 기반 IdeaInterpretationRun | 기존 AI proposal/result를 보존하고 input mismatch 시 `STALE` | stale Interpretation은 IdeaVersion confirmation source로 사용하지 않음 | 새 exact input에 대해 `CAN_INTERPRET_IDEA` 재평가 |
| LegalReview correction으로 새 IdeaVersion | 이전 legal/concept chain 전체 | 기존 결과를 수정하지 않고 보존; correction source를 참조하는 새 IdeaVersion과 새 LegalReviewRun 생성 | accepted legal/concept downstream pointer 해제 | 새 legal result가 gate를 통과할 때까지 `CAN_GENERATE_CONCEPTS` false |
| 새 current ConceptVersion | 해당 이전 version 기반 Quick/Detailed, ShortlistDecision, ConceptSelection과 그 downstream | exact version 기준으로 `STALE`; 다른 candidate chain은 영향 없음 | stale assessment/decision/selection pointer 해제 | 새 Quick/shortlist/detail/selection gate 재평가 |
| 새 ShortlistDecision | 제외된 ConceptVersion의 기존 DetailedAnalysisRun | history는 보존하되 current shortlist 근거로는 `STALE`; 다시 포함돼도 자동 current 복구 안 함 | 제외된 version의 detailed current reference 해제 | 제외 항목의 `CAN_RUN_DETAILED_ANALYSIS` false; 재포함 시 재검증 |
| 새 ConceptSelection | 이전 selection 기반 PersonaStudy, Interview, Synthesis, MarketingWorkspaceVersion/Run/Asset/Comparison, FinalReportVersion | 모두 `STALE`; 새 selection chain 생성 | 이전 persona/marketing/report current pointer 해제 | Persona/Marketing/Report capability를 새 selection 기준으로 재평가 |
| 새 PersonaCardVersion | 이전 version 기반 PersonaInterview, 관련 InterviewSynthesis, Marketing evidence와 FinalReportVersion | 해당 persona branch부터 `STALE`; 다른 card/interview branch는 유지 | 해당 Card의 current-version pointer 이동, 이전 interview/synthesis 및 downstream pointer 해제 | 새 version 기준 interview와 dependent Marketing/Report capability 재평가 |
| 새 MarketingAssetVersion | 이전 asset version을 입력으로 한 MarketingComparisonRun과 FinalReportVersion | 해당 비교/report만 `STALE`; asset history 유지 | 해당 comparison/report current pointer 해제 | 새 comparison과 report generation capability 재평가 |
| 새 MarketingWorkspaceVersion | 이전 workspace context 기반 generation/comparison/report | 이전 context chain `STALE`; asset version 자체는 provenance와 함께 유지 | 이전 workspace context 기반 run/comparison/report pointer 해제 | Marketing generation/comparison/report capability 재평가 |
| 새 FinalReportVersion | 이전 FinalReportVersion | 이전 version은 immutable history로 유지하며 자동 수정하지 않음 | 검증된 새 version으로 current pointer만 이동 | `CAN_EXPORT_FINAL_REPORT`는 새 current version 기준; 재생성은 명시적 command만 허용 |

Stale 판정과 current reference는 Spring이 관리한다. AI Server는 stale 상태를 계산하거나 Project current pointer를 변경하지 않는다. Stale target은 history 조회에는 남지만 capability 충족 근거나 current pointer target이 될 수 없다.

## Deletion and retention direction

- Project archive는 하위 history를 즉시 물리 삭제하지 않는다.
- immutable version/run/result/decision은 audit, provenance와 report 재현성에 필요한 동안 보존한다.
- FILE/PDF artifact 삭제는 RDB reference 해제와 retention 확인 후 Spring이 수행한다. AI Server는 RDB/Object Storage identifier, presigned URL 또는 삭제 권한을 갖지 않는다.
- 정확한 retention 기간, hard-delete 순서와 physical cascade는 operations 및 migration Phase에서 결정한다.
