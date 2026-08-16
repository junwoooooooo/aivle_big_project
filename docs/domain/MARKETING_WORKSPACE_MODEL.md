# Marketing Workspace Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Workspace context, generation, versioned assets and Persona comparison
- Supersedes: Legacy validation-to-marketing and market response models
- Implementation Status: NOT_STARTED

## Workspace decision

Project당 최대 하나의 logical MarketingWorkspace를 둔다(`1:0..1`). 선택 concept나 Persona evidence가 바뀔 때 workspace identity를 새로 만들지 않고 immutable MarketingWorkspaceVersion을 추가한다. Asset, generation과 comparison history는 exact workspace/asset version reference로 보존한다.

## MarketingWorkspace

| Concern | Logical contract |
|---|---|
| Identifier/owner | workspace identifier; Project composition과 owner scope |
| Cardinality | Project `1:0..1` logical workspace; Workspace `1:N` versions/assets/runs |
| Semantics | current MarketingWorkspaceVersion, current asset pointers와 운영 상태 방향 |
| Mutability | logical identity/current pointers/lifecycle mutable |
| Lifecycle | `ACTIVE`, `ON_HOLD`, `ARCHIVED`; current/stale 별도 |
| Time/concurrency | 생성·마지막 갱신 시각; current pointer/lifecycle에 optimistic concurrency |
| Provenance | Project와 생성 actor; content provenance는 immutable version/run에 위치 |
| Delete | asset/comparison/report history가 있으면 archive; hard delete는 retention 후 |
| Uniqueness | Project당 logical workspace 최대 하나 |

## MarketingWorkspaceVersion

| Concern | Logical contract |
|---|---|
| Identifier/owner | workspace version identifier; MarketingWorkspace composition |
| Cardinality | Workspace `1:N`; version number 유일·단조 증가, current 최대 하나 |
| Input | exact active ConceptSelection/ConceptVersion, exact PersonaStudy와 선택한 PersonaCard/Interview/Synthesis evidence refs |
| Semantics | marketing objective/context, constraints, approved evidence snapshot 방향 |
| Mutability | immutable; input/context 변경은 새 version |
| Lifecycle | `DRAFT`, `CONFIRMED`, `SUPERSEDED`; current/stale 별도 |
| Time/concurrency | 생성·확정 시각; current pointer 변경에 optimistic concurrency |
| Provenance | user-confirmed context와 AI proposal 구분, input snapshot/hash |
| Delete | generation/asset/comparison/report가 참조하면 보존 |
| Uniqueness | Workspace + version number |

ConceptSelection 또는 참조 Persona evidence가 바뀌면 이전 WorkspaceVersion과 그 downstream을 `STALE`로 만들고 새 version을 생성한다.

## MarketingGenerationRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | generation run identifier; MarketingWorkspace 소유 |
| Input | exact MarketingWorkspaceVersion, target PersonaCard refs, generation contract/input snapshot |
| Task binding | 요청 수락 후 1:1 TaskRun; retry는 같은 TaskRun의 Attempt, rerun은 새 GenerationRun/TaskRun |
| Cardinality | WorkspaceVersion `1:N` generation runs; run은 여러 asset proposal 생성 가능 |
| Decision boundary | AI-generated proposal이며 asset의 사용자 승인이나 comparison winner 결정이 아님 |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection과 adopted result/domain validation, `CURRENT`/`STALE`을 분리 |
| Time/concurrency | 생성·시작·완료·갱신 시각; lifecycle/adoption에 optimistic concurrency |
| Provenance | TaskRun/TaskResult, exact WorkspaceVersion/persona/input hash |
| Delete | asset/report provenance면 보존 |
| Uniqueness | run identifier; idempotency는 TaskRun 정책 |

## MarketingAsset and MarketingAssetVersion

MarketingAsset은 하나의 시안 identity이고 content history는 MarketingAssetVersion에 존재한다.

| Concern | MarketingAsset | MarketingAssetVersion |
|---|---|---|
| Identifier/owner | asset identifier; Workspace composition | version identifier; Asset composition |
| Cardinality | Workspace `1:N` Asset | Asset `1:N`; version number 유일·current 최대 하나 |
| Semantics | asset type, display identity, current version reference | text content 또는 Spring-owned binary TaskArtifact/StoredFile reference, prompt/input snapshot, target Persona refs |
| Mutability | identity/current pointer/lifecycle mutable | immutable; 편집·재생성은 새 version |
| Lifecycle | `ACTIVE`, `ARCHIVED`; current/stale 별도 | `DRAFT`, `READY`, `ARCHIVED`; current/stale 별도 |
| Time/concurrency | 생성·갱신 시각; pointer에 optimistic concurrency | 생성·확정 시각; pointer adoption에 concurrency |
| Provenance | workspace identity | exact WorkspaceVersion/GenerationRun/TaskResult와 user edit 구분 |
| Delete | comparison/report 참조 시 archive | reference 중 보존; binary 삭제는 Spring retention |
| Uniqueness | asset identifier; Workspace 안 display identity 방향 | Asset + version number |

AI Server local output path, object key, Storage reference나 presigned URL은 MarketingAssetVersion에 저장하지 않는다. Text는 검증된 result로, binary는 Spring이 생성·수신·검증한 artifact reference로만 연결한다. 초기 Spring–AI binary transport는 지원하지 않는다.

## MarketingComparisonRun

| Concern | Logical contract |
|---|---|
| Identifier/owner | comparison run identifier; MarketingWorkspace 소유 |
| Input | exact MarketingWorkspaceVersion, 둘 이상 exact MarketingAssetVersion과 Persona evidence snapshot |
| Task binding | 요청 수락 후 1:1 TaskRun; retry는 같은 TaskRun의 Attempt, rerun은 새 ComparisonRun/TaskRun |
| Cardinality | Workspace `1:N` Run; Run `N:M` AssetVersion; 동일 version 중복 금지 |
| Result | dimension별 relative assessment, Persona별 관점, caveat, evidence gap와 AI recommendation 방향 |
| Prohibitions | statistical experiment, actual-user A/B, winner probability, purchase/conversion prediction 표현 금지 |
| Decision boundary | 상대 AI assessment이며 사용자 asset 선택이나 실제 성과 사실이 아님 |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection과 adopted result/domain validation, `CURRENT`/`STALE`을 분리 |
| Time/concurrency | 생성·시작·완료·갱신 시각; lifecycle/result adoption에 optimistic concurrency |
| Provenance | exact AssetVersion/Persona evidence/TaskRun/TaskResult |
| Delete | FinalReport가 참조하면 보존 |
| Uniqueness | run identifier; 비교 input set identity 중복은 idempotency로 방지 |

새 MarketingAssetVersion은 이전 version을 입력으로 사용한 ComparisonRun과 FinalReportVersion만 `STALE`로 만든다. 다른 asset/comparison history는 유지한다.

두 Marketing Run 모두 TaskRun `SUCCEEDED`만으로 business result 성공을 확정하지 않는다. Exact input asset/workspace references, `ADOPTED` TaskResult와 domain validation을 함께 확인한다.
