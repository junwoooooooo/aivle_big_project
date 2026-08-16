# Persona and Interview Logical Model

- Status: DRAFT_CONTRACT
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: PersonaStudy, versioned PersonaCard, independent Interview and Synthesis
- Supersedes: Fixed cluster persona and panel interview models
- Implementation Status: NOT_STARTED

## PersonaStudy

| Concern | Logical contract |
|---|---|
| Identifier/owner | PersonaStudy identifier; Project composition과 owner scope |
| Input | exact active ConceptSelection과 selected ConceptVersion, study input snapshot/hash |
| Cardinality | ConceptSelection `1:N` PersonaStudy; Project에 여러 study history 허용, current study 최대 하나 |
| Semantics | study purpose/context, current card/synthesis references와 synthetic disclosure 방향 |
| Mutability | study lifecycle/current pointers mutable; input selection/version immutable |
| Lifecycle | `DRAFT`, `IN_PROGRESS`, `COMPLETED`, `ARCHIVED`; current/stale 별도 |
| Time/concurrency | 생성·완료·갱신 시각; current pointers/lifecycle에 optimistic concurrency |
| Provenance | exact selection/version, initiating user와 TaskRun references |
| Delete | interview/marketing/report 근거면 보존; archive 우선 |
| Uniqueness | Study identifier; Project current study 최대 하나 |

새 ConceptSelection은 이전 PersonaStudy와 하위 Card/Interview/Synthesis를 삭제하지 않고 `STALE`로 만든다.

## PersonaCardGenerationRun

PersonaCardGenerationRun은 Project owner scope를 상속하는 AI-backed Domain Run이다.

| Concern | Logical contract |
|---|---|
| Identifier/owner | Run identifier; exact PersonaStudy composition과 Project owner scope |
| Input | exact PersonaStudy, exact current ConceptSelection, exact selected ConceptVersion, generation input snapshot/hash |
| Options | required bounded personaCount; optional bounded generationOptions |
| Task binding | 요청 수락 후 정확히 하나의 TaskRun; TaskRun이 execution lifecycle source of truth |
| Result | exact TaskRun의 validated/adopted TaskResult; 성공 시 하나 이상의 PersonaCard와 initial PersonaCardVersion 생성 |
| Mutability/validity | input immutable; adopted result reference와 `CURRENT`/`STALE`만 controlled update |
| Retry/rerun | retry는 같은 TaskRun의 새 TaskAttempt; user rerun은 새 PersonaCardGenerationRun과 새 TaskRun |
| Provenance | input snapshot/hash, TaskRun/TaskResult, generated Card/Version references |
| Uniqueness | run identifier/idempotency scope; 같은 result의 persona identity 중복 금지 |

## PersonaCard and PersonaCardVersion

PersonaCard는 study-local synthetic persona logical identity이고 PersonaCardVersion은 immutable content다. GenerationRun은 각 identity의 최초 version을 만들고 사용자 편집은 같은 identity의 새 version을 만든다.

| Concern | PersonaCard | PersonaCardVersion |
|---|---|---|
| Identifier/owner | Card identifier; PersonaStudy composition | version identifier; PersonaCard composition |
| Cardinality | GenerationRun `1:N` Card; Study `1:N` Card | Card `1:N` Version; current 최대 하나 |
| Content/version | current-version pointer와 lifecycle만 mutable | version number 유일·단조 증가; content immutable |
| Three layers | content 없음 | Role and Context; Problem and Needs; Behavior and Decision |
| Disclosure | current version의 synthetic disclosure 필수 | 실제 고객 조사나 대표 통계가 아니라는 표시 필수 |
| Prohibitions | synthetic identity | demographic-only, purchase probability, market share, actual customer statistic 금지 |
| Created by | identity origin Run | `AI_GENERATED` 또는 `USER_EDITED`; AI proposal과 user confirmation 구분 |
| Lifecycle/validity | `ACTIVE`, `ARCHIVED`; current/stale | `DRAFT`, `CONFIRMED`, `ARCHIVED`; current/stale |
| Time/concurrency | 생성·갱신 시각; current-version pointer에 optimistic concurrency | 생성·사용자 확인 시각; immutable |
| Provenance | exact GenerationRun/Study | exact Study/ConceptVersion/TaskResult 또는 base version/edit rationale |
| Delete | Version/Interview/Marketing/Report 참조 시 보존 | Interview/Marketing/Report 참조 시 보존 |
| Uniqueness | Study 안 logical identity | Card + version number; current confirmed version 최대 하나 |

세 layer의 상세 field와 optional attribute vocabulary는 P2.4 이후 정의한다. demographic 정보는 세 layer를 보조할 수 있지만 card identity나 validity의 유일한 근거가 될 수 없다.

## PersonaInterview

| Concern | Logical contract |
|---|---|
| Identifier/owner | PersonaInterview identifier; PersonaStudy composition과 Project scope |
| Input | exact PersonaCard와 exact PersonaCardVersion, question set/context snapshot, independent TaskRun |
| Cardinality | PersonaCardVersion `1:N` Interview; retry는 TaskAttempt history로 추적 |
| Task binding | 요청 수락 후 Interview `1:1` TaskRun; retry는 같은 TaskRun, 사용자 rerun은 새 Interview/TaskRun |
| Semantics | 질문, synthetic 답변, interpretation, evidence need를 구분 |
| Isolation | 다른 PersonaCard/Interview의 prompt, answer, hidden context를 공유하지 않음 |
| Disclosure | 실제 고객 인터뷰·조사·전문가 판단으로 표현하지 않음 |
| Mutability | input immutable; adopted business result reference와 validity만 controlled update; execution lifecycle은 TaskRun 소유 |
| Execution/validity | TaskRun 상태 projection과 `CURRENT`/`STALE`을 별도 평가 |
| Time/concurrency | 생성·시작·완료·갱신 시각; lifecycle/adoption에 optimistic concurrency |
| Provenance | exact Card/TaskRun/TaskResult, question contract version |
| Delete | Synthesis/Marketing/Report가 참조하면 보존 |
| Uniqueness | Interview identifier; idempotency는 TaskRun input/card/question snapshot으로 보호 |

한 PersonaInterview 실패는 다른 Persona의 실행/result를 실패 또는 변경시키지 않는다.

## InterviewSynthesis

| Concern | Logical contract |
|---|---|
| Identifier/owner | synthesis identifier; PersonaStudy composition |
| Cardinality | PersonaStudy `1:N` immutable version history |
| Input | exact PersonaInterview result 집합; 포함·제외된 interview identifiers 고정 |
| Semantics | 공통 응답, 상충 응답, unresolved questions, research recommendations |
| Boundary | 개별 Interview 원본을 유지하고 synthesis로 덮어쓰지 않음; user decision이나 실제 조사 결론이 아님 |
| Mutability/version | immutable; source set/content 변경은 새 synthesis version |
| Lifecycle | `DRAFT`, `FINALIZED`, `ARCHIVED`; current/stale 별도 |
| Time/concurrency | 생성·finalize 시각; current pointer 갱신에 optimistic concurrency |
| Provenance | exact Interview/TaskResult refs, synthesis TaskRun과 AI/user edits |
| Delete | Marketing/Report가 참조하면 보존 |
| Uniqueness | Study + synthesis version number; current finalized 최대 하나 |

AI-backed InterviewSynthesis는 요청 수락 후 정확히 하나의 TaskRun과 연결한다. 비AI 사용자 편집/정리만으로 생성되는 synthesis version은 TaskRun을 요구하지 않는다. AI-backed retry는 같은 TaskRun의 새 Attempt이고 rerun은 새 synthesis version과 새 TaskRun이다. Interview와 AI-backed synthesis 성공은 TaskRun `SUCCEEDED`, adopted TaskResult와 domain validation을 함께 요구한다.

## Stale rules

- 새 PersonaCardVersion은 이전 version 기반 Interview와 해당 Interview를 포함한 Synthesis를 `STALE`로 만든다.
- PersonaCard current version 변경의 stale 영향은 해당 persona branch에 우선 한정하고, 이를 참조한 Marketing/FinalReport로 전파한다.
- 새 InterviewSynthesis는 개별 Interview validity를 변경하지 않으며 current synthesis pointer만 이동한다.
