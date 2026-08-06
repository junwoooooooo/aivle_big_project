# Workflow and Task Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Project stage/capability and TaskRun/Attempt/Result/Artifact schema
- Supersedes: Legacy ProjectStage, completion gates and AnalysisJob model
- Implementation Status: NOT_STARTED

## Independent status dimensions

| Dimension | Values | Source of truth |
|---|---|---|
| Project lifecycle | `ACTIVE`, `ON_HOLD`, `COMPLETED`, `ARCHIVED` | Project |
| Workflow Stage | `IDEA_INTAKE`, `LEGAL_REVIEW`, `CONCEPT_BUILDING`, `CONCEPT_ANALYSIS`, `CONCEPT_SELECTION`, `VALIDATION`, `MARKETING`, `FINAL_REPORT` | Project navigation/current-reference projection |
| Domain validity | `CURRENT`, `STALE` | Spring domain stale evaluation |
| TaskRun execution | `QUEUED`, `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT` | TaskRun |
| TaskAttempt execution | `CREATED`, `CLAIMED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `CANCELLED` | TaskAttempt |
| TaskResult validation | `RECEIVED`, `VALIDATED`, `REJECTED`, `ADOPTED` | TaskResult validation/adoption |
| Legal business result | `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` | adopted LegalReviewRun result |

같은 문자열을 사용하더라도 차원이 다르면 enum이나 field를 공유하지 않는다. 특히 TaskRun `FAILED`, Domain validity `STALE`, Legal result `PROHIBITED`는 서로 대체할 수 없다.

## Capability model

Stage는 현재 사용자 여정의 표시값이다. 실제 명령 가능 여부는 별도 Capability로 평가하며 최소한 다음 입력을 사용한다.

- Project status와 owner scope
- required exact current version/reference 존재 여부
- resource/run lifecycle과 `CURRENT`/`STALE` validity
- 사용자 shortlist/selection/confirmation gate
- TaskRun 충돌·진행 상태와 Service Policy

Backtracking은 허용한다. Stage를 뒤로 이동하거나 상류 version을 바꿔도 downstream record를 삭제하지 않고 stale matrix에 따라 `STALE`로 만든다. AI Server 응답은 Project stage, capability, current pointer 또는 user decision을 직접 변경하지 않는다.

Capability는 Spring이 요청 시 계산하는 값이며 별도 업무 source of truth로 저장하지 않는다. Cache는 허용할 수 있지만 Project/resource/TaskRun/Service Policy 변경 시 폐기 가능한 구현 최적화다.

모든 capability는 공통으로 Project status, owner scope, 관련 Service Policy, required exact current reference, resource lifecycle, `CURRENT`/`STALE`, 사용자 gate와 conflicting TaskRun 존재 여부를 평가한다.

| Capability | Required exact current reference / user gate | Policy and conflict direction |
|---|---|---|
| `CAN_EDIT_IDEA` | owner-scoped `ACTIVE` Project에서 IdeaSource 또는 user-authored/final IdeaVersion confirmation 가능 | maintenance/project policy와 conflicting confirmation command 확인 |
| `CAN_INTERPRET_IDEA` | 같은 Project의 `CURRENT` validated IdeaSourceExtraction 하나 이상 | AI policy, bounded payload와 같은 exact input의 conflicting TaskRun 확인; `ARCHIVED` 차단 |
| `CAN_RUN_LEGAL_REVIEW` | confirmed current IdeaVersion, `CURRENT` | AI execution/legal connection policy 허용; 같은 input의 active TaskRun 없음 |
| `CAN_GENERATE_CONCEPTS` | current IdeaVersion과 그 version의 adopted `PASS`/`PASS_WITH_CONDITIONS` LegalReviewRun | AI policy 허용; 다른 legal status와 stale legal result는 차단 |
| `CAN_RUN_QUICK_ASSESSMENT` | current non-stale ConceptVersion | AI policy 허용; 동일 subject/input active TaskRun 없음 |
| `CAN_SET_SHORTLIST` | 하나 이상의 current exact ConceptVersion과 필요한 Quick result 검토 | USER gate; AI ranking만으로 활성화·확정하지 않음 |
| `CAN_RUN_DETAILED_ANALYSIS` | current ShortlistDecision에 포함된 exact current ConceptVersion | AI policy 허용; 동일 analysis/input active TaskRun 없음 |
| `CAN_SELECT_CONCEPT` | current ShortlistDecision과 요구된 Detailed result 검토 | USER gate; AI recommendation은 선택을 대체하지 않음 |
| `CAN_CREATE_PERSONA_STUDY` | current ConceptSelection과 selected exact ConceptVersion | stale selection 차단; Project creation/AI 관련 policy 적용 |
| `CAN_GENERATE_PERSONA_CARDS` | current non-stale PersonaStudy, ConceptSelection과 selected exact ConceptVersion | AI policy 허용; conflicting PersonaCardGenerationRun 없음 |
| `CAN_RUN_PERSONA_INTERVIEW` | current PersonaStudy의 confirmed exact PersonaCardVersion | AI policy 허용; Persona별 conflicting TaskRun만 차단 |
| `CAN_USE_MARKETING_WORKSPACE` | current ConceptSelection, PersonaStudy와 선택한 current evidence | stale upstream 차단; asset edit/generation별 policy와 conflict 확인 |
| `CAN_GENERATE_FINAL_REPORT` | 포함할 exact current upstream reference 집합과 사용자 결정이 모두 준비됨 | report generation/AI policy 허용; active generation TaskRun 없음 |
| `CAN_EXPORT_FINAL_REPORT` | current `AVAILABLE` FinalReportVersion | report generation/export와 file policy 허용; stale source라도 기존 version history export 허용 여부는 command contract에서 명시 |

Owner scope 실패는 capability false를 노출해 resource 존재를 추론하게 하지 않고 public resource lookup의 cross-owner 404 규칙을 따른다.

## Domain Run–TaskRun binding

AI-backed Domain Run은 실행 요청이 Spring에 수락되는 transaction에서 정확히 하나의 TaskRun과 결합한다. 대상은 IdeaInterpretationRun, LegalReviewRun, ConceptGenerationRun, QuickAssessmentRun, DetailedAnalysisRun, PersonaCardGenerationRun, PersonaInterview, AI-backed InterviewSynthesis, MarketingGenerationRun, MarketingComparisonRun과 후속 AI-backed Final Report Run이다.

- Domain Run `1:1` TaskRun, TaskRun `1:N` TaskAttempt, TaskAttempt `1:0..N` TaskResult, TaskResult `1:0..N` TaskArtifact다.
- retry는 동일 TaskRun에 단조 증가하는 새 TaskAttempt를 추가한다.
- 사용자의 명시적 rerun은 새 Domain Run과 새 TaskRun을 생성한다. 이전 run/result는 history로 보존한다.
- TaskRun은 execution lifecycle의 유일한 source of truth다. Domain Run은 exact business input, adopted business result reference, domain validation, `CURRENT`/`STALE`과 provenance를 소유한다.
- Domain Run의 execution 표시값은 TaskRun projection이며 독립적으로 전이시키는 중복 상태가 아니다.
- 모든 TaskRun이 Domain Run을 가질 필요는 없다. Platform task도 허용하지만 subject type/identifier는 allowlist 대상이고 같은 Project owner scope여야 한다.

| Domain execution projection | TaskRun condition | Adopted result / domain validation | Validity |
|---|---|---|---|
| Request accepted | `QUEUED` 또는 `READY` | adopted result 없음 | `CURRENT` 또는 실행 전 upstream 변경 시 `STALE` |
| Executing | `RUNNING` | adopted result 없음 | 별도 평가 |
| Completed | `SUCCEEDED` | exact input과 contract에 대해 `ADOPTED` TaskResult가 있고 domain validation 성공 | `CURRENT` 또는 이후 upstream 변경 시 `STALE` |
| Failed | `FAILED` | adopted business result 없음; rejected result evidence 가능 | validity와 별도 |
| Timed out | `TIMED_OUT` | adopted business result 없음; late result는 non-adopted evidence | validity와 별도 |
| Cancelled | `CANCELLED` | adopted business result 없음 | validity와 별도 |

TaskRun `SUCCEEDED` 값만 읽어 Domain Run 성공을 확정하지 않는다. binding, exact input/hash, `ADOPTED` TaskResult와 domain validation을 함께 확인한다. 정상 transition은 이를 원자적으로 맞추지만 projection은 불완전하거나 오래된 상태를 성공으로 오인하지 않아야 한다.

Public transport에서는 202 수락 후 TaskRun의 `FAILED`, `TIMED_OUT`, `CANCELLED`가 resource terminal state다. TaskRun GET은 이를 200 representation으로 반환하며 502/503/504 HTTP status로 바꾸지 않는다. HTTP 503은 TaskRun 생성 전 dependency 때문에 command를 수락하지 못한 경우, HTTP 504는 request/gateway deadline 자체가 초과된 경우다.

## TaskRun

TaskRun은 Project 소유의 범용 업무 요청 aggregate다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | TaskRun identifier; 정확히 한 Project와 owner scope |
| Subject | task type, subject type과 exact subject identifier; subject는 같은 Project 소속 |
| Input | immutable input snapshot/hash, contract version, idempotency key, correlation 방향 |
| State ownership | 업무 요청과 현재 최종 상태, final adopted TaskResult reference를 Spring이 소유 |
| Retry policy | 생성 당시 retry eligibility, attempt limit/backoff/timeout policy snapshot 방향 |
| Mutability | state/current attempt/final result/lifecycle metadata mutable; subject/input은 immutable |
| Lifecycle | `QUEUED`, `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT` |
| Time | 생성, 최초 시작, terminal, 마지막 갱신 시각 |
| Concurrency | 필수; claim/result adoption/retry race와 lost update 방지 |
| Provenance | initiating actor/system, subject exact ref, input hash와 adopted result |
| Delete | 업무/domain/report provenance가 참조하면 보존; archive/retention 방향 |
| Uniqueness | TaskRun identifier; idempotency key는 정의된 Project/task/subject scope에서 중복 업무 요청 방지 |

TaskRun state가 `SUCCEEDED`가 되려면 validated/adopted TaskResult와 final result reference가 일관되어야 한다. Domain Run 성공 projection은 여기에 exact binding과 domain validation을 추가로 확인한다. late result가 이미 terminal인 TaskRun을 자동 재개하거나 사용자 결정을 교체하지 않는다.

## TaskAttempt

| Concern | Logical contract |
|---|---|
| Identifier/owner | TaskAttempt identifier; TaskRun composition과 Project scope 상속 |
| Cardinality | TaskRun `1:N`; attempt number는 TaskRun 안에서 유일·단조 증가 |
| Claim/lease | worker/claim identity, lease 획득·expiry, heartbeat/renewal 방향 |
| Execution | request/response contract version, provider-neutral execution metadata, started/finished time |
| Failure | timeout, normalized error category/code, retryable direction; secret/raw provider body 제외 |
| Mutability | claim과 lifecycle은 terminal 전 mutable; terminal 후 append-only evidence |
| Lifecycle | `CREATED`, `CLAIMED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `CANCELLED` |
| Concurrency | 필수; 유효 lease owner만 transition/adoption 후보 생성 가능 |
| Provenance | exact TaskRun/input hash, executor identity와 contract version |
| Delete | TaskRun retention을 따름 |

외부 AI/MCP/API 호출 동안 Spring DB transaction을 유지하지 않는다. claim/lease 또는 동등한 control로 실행 권한을 확보하고 transaction 밖에서 호출한 뒤, 짧은 transaction에서 lease, attempt state, input hash와 idempotency를 재검증해 결과를 기록한다. polling과 event wake 모두 이 logical contract를 사용할 수 있으며 outbox 선택은 P3 구현 결정이다.

## TaskResult

| Concern | Logical contract |
|---|---|
| Identifier/owner | TaskResult identifier; TaskRun composition, exact TaskAttempt reference |
| Cardinality | TaskAttempt `1:0..N` Result; duplicate/late response evidence 보존. adopted result는 Attempt당 최대 하나이고 TaskRun final adopted result도 최대 하나 |
| Semantics | provider-neutral result body direction, schema/contract version, provenance, warning/error evidence |
| Validation | `RECEIVED`, `VALIDATED`, `REJECTED`, `ADOPTED`; domain adoption과 transport receipt 구분 |
| Mutability | received payload/provenance immutable; validation/adoption state만 controlled transition |
| Time | 수신, 검증, 채택/거절 시각 |
| Concurrency | validation/adoption에 필수; TaskRun terminal/current result와 원자적으로 검증 |
| Provenance | exact Attempt, input snapshot/hash와 external source/model-neutral metadata |
| Delete | adopted/non-adopted 모두 retry/ambiguity evidence retention 방향 |
| Uniqueness | result identifier; 동일 response identity 중복 채택 금지 |

검증 실패 또는 stale lease의 결과는 domain result로 채택하지 않지만 non-adopted evidence로 보존할 수 있다. 지연 response는 current Attempt/TaskRun 상태와 exact contract를 다시 확인하며 이미 채택된 결과를 덮어쓰지 않는다.

## TaskArtifact

| Concern | Logical contract |
|---|---|
| Identifier/owner | TaskArtifact identifier; TaskResult composition/reference와 Project scope |
| Cardinality | TaskResult `1:0..N` artifact; JSON-only 정상 결과는 artifact가 없을 수 있음 |
| Storage ownership | Spring-owned StoredFile/Object Storage metadata reference만 허용 |
| Semantics | artifact role/type, content metadata/checksum/size, producing result reference 방향 |
| Mutability | bytes immutable; metadata/lifecycle mutable |
| Lifecycle | `PENDING`, `AVAILABLE`, `QUARANTINED`, `DELETED` |
| Time/concurrency | 생성·검증·삭제 시각; lifecycle transition에 optimistic concurrency |
| Provenance | exact TaskResult, Spring validation과 generator identity |
| Delete | RDB reference/retention 확인 후 Spring만 수행 |
| Uniqueness | artifact identifier와 Storage object identity 중복 방지 방향 |

AI Server의 RDB/Object Storage reference, object key, presigned URL 또는 local artifact path는 TaskArtifact 계약에 포함하지 않는다. 초기 AI binary transport는 지원하지 않는다. Spring이 생성한 Final Report PDF 같은 artifact는 TaskArtifact 또는 report export reference와 연결할 수 있다.

## Stale and adoption rules

- TaskRun input subject/version이 stale이면 새 Attempt를 claim할 capability가 없다.
- 실행 중 upstream이 변경되면 response를 수신해도 domain result로 자동 채택하지 않고 stale/non-adopted evidence로 남긴다.
- retry 성공은 이전 user Decision/Selection을 암묵적으로 교체하지 않는다.
- stale lease나 late/duplicate result는 이미 adopted된 result를 덮어쓰지 않고 `REJECTED` 또는 non-adopted evidence로 보존할 수 있다.
- upstream 변경 시 stale current pointer를 해제하거나 새 current reference로 원자적으로 교체한다. immutable history와 FinalReportVersion은 수정하지 않는다.
- stale FinalReportVersion을 자동 재작성하지 않는다. 새 보고서는 `CAN_GENERATE_FINAL_REPORT` 검증을 통과한 명시적 generation command로만 생성한다.
