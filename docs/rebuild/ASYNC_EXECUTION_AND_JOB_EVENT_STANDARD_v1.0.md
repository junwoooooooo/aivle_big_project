# ASYNC EXECUTION AND JOB EVENT STANDARD — V2 authoritative contract

파일명은 기존 참조 호환을 위해 `v1.0`을 유지한다.

## 1. 유지 기반

`TaskRun`, `TaskAttempt`, worker lease/claim, bounded retry/recovery, `JobEvent`, SSE, polling fallback, replay, idempotency를 재사용한다. 모든 작은 사용자 Action을 무조건 Task로 만들지 않는다.

V2에서 비동기 후보인 task type:

- `IDEA_SAFETY_REVIEW`
- `IDEA_INTERPRETATION`
- `CONCEPT_CANDIDATE_V2`
- `CONCEPT_DISTINCTNESS_REVIEW`
- `CONCEPT_LEGAL_REVIEW`
- `CONCEPT_HYPOTHESIS_ALTERNATIVE`
- `CONCEPT_DELTA_LEGAL_REVIEW`
- `TECH_OPS_PROPOSAL`
- `FINANCE_ESTIMATE`

외부 Market/BM/TechOps/Finance/Persona/Marketing run은 공통 module handoff/run event 계약을 사용한다.

## 2. 실행 흐름과 정본

API transaction에서 `QUEUED` execution을 생성하고 commit 후 worker가 scalar claim context로 claim한다. Domain terminal transition과 TaskRun transition을 완료한 뒤 terminal Event를 발행한다. JPA Entity를 transaction 밖으로 넘기지 않는다.

Event는 상태 변경 신호다. Seed, Interpretation, Concept Run/Slot, Legal Assessment, Hypothesis Decision, Snapshot, external Module Run의 Query API가 화면 정본이다.

## 3. JobEvent 필드

- `eventId`
- `jobId`
- `projectId`
- `taskRunId`
- `module`
- `stageKey`
- `eventType`
- `status`
- `safeMessageKey`
- `safeMessageParams`
- `sequence`
- `occurredAt`

Concept의 안전한 사용자 stage 예시는 candidate design, structure validation, distinctness validation, official-evidence legal review, result preparation이다.

## 4. 금지 정보

Prompt, provider request/response body, provider raw error, 사용자 전체 입력, 첨부 원문, 내부 policy/reasoning, 법률 원문 전문, Authorization, API key/secret, stack trace를 Event, SSE, audit metadata, 사용자 query에 노출하지 않는다.

## 5. Terminal immutability

- Terminal TaskRun과 Job ID는 immutable execution history이며 새 사용자 Action에 재사용하지 않는다.
- 동일 command key replay만 동일 execution을 반환한다. 새 command는 canonical content가 같아도 새 execution identity를 갖는다.
- JobEvent가 `COMPLETED`, `NEEDS_INPUT`, `FAILED`, `BLOCKED`, `CANCELLED`에 도달하면 동일 jobId에 후속 Event를 발행하지 않는다. 위반은 `TERMINAL_JOB_EVENT_IMMUTABLE`로 거부한다.
- 과거 raw `NEEDS_INPUT` TaskRun을 후속 입력 때문에 `SUCCEEDED`로 변경하지 않는다.
- Domain, Attempt, Run, terminal Event의 terminal 의미가 같은 execution에서 일치해야 한다.

## 6. NEEDS_INPUT 의미

`NEEDS_INPUT`은 현재 사용자 행동이 필요한지와 raw terminal outcome을 구분한다.

- active jobs에는 실제 실행 중 상태와 현재 unresolved `NEEDS_INPUT`만 포함한다.
- 최신 relevant job이며 Domain도 여전히 `NEEDS_INPUT`이고 후속 patch/decision/newer run으로 해결되지 않았을 때만 actionable이다.
- 해결된 과거 raw outcome은 `rawStatus=NEEDS_INPUT`, `actionable=false`, `presentationStatus=RESOLVED_INPUT`으로 recent history에 표시할 수 있다.
- Concept 설계 누락은 사용자에게 legal 질문을 던지는 `NEEDS_FACTS`가 아니다. Candidate incomplete/redesign/replacement다.
- `NEEDS_FACTS` schema는 하위호환을 위해 유지하지만 active Concept Factory에서는 actionable `NEEDS_INPUT`을 만들지 않는다. `LEGAL_EXTERNAL_FACT_UNRESOLVED` business rejection 후 bounded replacement한다.

## 7. Concept validation ordering과 Attempt error

Concept pipeline 순서:

`GENERATE → SCHEMA VALIDATION → LOCKED/ORIGIN VALIDATION → DISTINCTNESS VALIDATION → LEGAL REVIEW`

Provider 또는 validation 오류는 Attempt에 다음 중 하나로 기록한다.

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

`PROVIDER_FAILURE`는 Slot registry, 사용자 progress status, query response status로 추가하지 않는다.

## 8. Bounded retry, repair, redesign, replacement

- transient provider retry는 동일 Slot에서 최대 1회다. 소진 시 replacement로 전이한다.
- permanent provider failure는 Run/Slot을 retry 불가 `FAILED`로 종료한다.
- schema repair는 최대 1회이며 재실패 시 replacement로 전이한다.
- legal redesign은 후보별 최대 1회이며 INITIAL/REPLACEMENT와 동일한 schema, LOCKED/origin, deterministic distinctness, ambiguous semantic judge, legal 순서를 다시 검사한다.
- initial candidate 5개, replacement round 최대 2회, 전체 inspected candidate 최대 15개를 유지한다.
- semantic duplicate는 적격 수에 포함하지 않고 Legal Review task를 만들지 않는다.
- 한도 내 distinct eligible 5개를 확보하지 못하면 `INSUFFICIENT_DISTINCT_CONCEPTS`로 terminal 처리한다.
- 무한 retry, repair, redesign, replacement를 금지한다.

## 9. Safety, Interpretation, Hypothesis, Delta Legal

Safety Review와 Legal Review는 task type, status meaning, 사용자 message를 공유하지 않는다. `BLOCK_OR_REFRAME`은 Concept Factory queue를 만들지 않는다.

Interpretation task는 `AI_DERIVED + REVIEWABLE` 결과를 만들며 사용자 입력값으로 위장하지 않는다. legal detail 누락만으로 `NEEDS_INPUT`을 만들지 않는다.

commitment review가 canonical Idea field를 실제 변경하면 현재 assessment는 stale이며 새 command identity로 새 `FINAL_SYNTHESIS` TaskRun을 queue한다. 동일 command replay만 기존 non-terminal 실행을 반환한다. Frontend는 `DERIVING` 응답에서 Interpretation patch/Confirm을 멈추고 terminal signal 뒤 Query로 Review를 복원한다.

Alternative hypothesis task는 선택 Concept에 새 proposal version을 만들고 기존 rejected proposal을 덮어쓰지 않는다. legal-sensitive edit는 Delta Legal Review execution을 만들며 통과 전 decision을 final acceptance로 전이하지 않는다. non-legal SOM edit는 Delta Legal task를 만들지 않는다.

Alternative/Delta command는 `Idempotency-Key`가 필수이고 provider가 필요한 경우 HTTP `202`로 `taskRunId=jobId`, `QUEUED`, action/hypothesis/version을 반환한다. worker는 `claimNext → startExecution → safe progress → provider → stale guard와 domain commit → TaskResult adopt/fail → terminal Event` 순서를 사용한다. terminal Event는 domain transaction이 성공한 뒤에만 발행한다.

Alternative provider 실패는 기존 proposal을 변경하지 않는다. Delta Legal의 `REDESIGNABLE | REJECTED | NEEDS_FACTS`는 provider 실패가 아니라 `LEGAL_INELIGIBLE` domain outcome이며 TaskRun result는 성공으로 채택한다. worker는 current selection, decision ID/version, pending TaskRun, concept hash 불일치를 `STALE_ACTION_RESULT`로 종료한다.

TechOps 초기 누락 proposal은 preparation 저장 뒤 `TECH_OPS_PROPOSAL` batch TaskRun 하나로 생성한다. `REJECT_AND_REQUEST_ALTERNATIVE`도 같은 task type의 독립 command지만 field/version/rejected value를 input에 고정한다. HTTP service가 synthetic `TaskRunWorkerContext`를 만들어 provider를 호출하지 않으며 실제 claimed context만 허용한다. worker는 preparation mutable/Snapshot 미확정/revision/field version/pending task/source hash를 재검사한다. 실패 뒤 직접 입력은 항상 가능하고 늦은 결과는 `STALE_ACTION_RESULT`로 폐기한다.

Finance 초기화는 provider-free다. 사용자가 field별 추천을 요청할 때만 `FINANCE_ESTIMATE`를 queue하며 generate/alternative command는 `202`, ACCEPT/EDIT_AND_ACCEPT는 `200`이다. 실제 claimed worker만 provider를 호출하고, 성공 completion은 active field task, preparation revision, source TechOps Snapshot ID/hash, Snapshot 미확정을 다시 검증한 뒤 proposal만 commit한다. provider failure는 field와 기존 proposal을 보존하며 late result는 `STALE_ACTION_RESULT`로 폐기한다.

## 10. SSE, replay, polling

- SSE는 `Last-Event-ID` replay와 sequence dedupe를 지원한다.
- completion, timeout, error 시 emitter를 정리한다.
- SSE response commit 뒤 JSON error를 쓰지 않는다.
- polling은 SSE 실패 시 bounded fallback이다. 2초 고정 polling을 금지한다.
- 초기 load, 재연결, 중요 Event, terminal Event, 수동 새로고침에서 Query한다.
- 새로고침은 current Run, domain query, Event replay, SSE reconnect 순서로 복원한다.

## 11. 외부 module event

외부 module event type은 `module.accepted`, `module.queued`, `module.started`, `module.progress`, `module.completed`, `module.failed`다. progress는 가짜 퍼센트 없이 `stageKey`와 `safeMessageKey`를 사용한다.

Idempotency는 `module + inputSnapshotHash + requestedOperation`을 사용한다. callback은 signature/authentication, timestamp, replay protection, project/handoff/input hash 일치를 검증한다. terminal external run 결과는 input Snapshot ID/hash와 result hash를 보존한다.

## 12. Job Center projection

Job Center는 raw execution status와 현재 사용자 actionability를 분리한다. 안전한 summary, 현재 stage, retry 가능 여부, next action만 표시한다. resolved input notice는 지우거나 해결됨으로 바꾸며 provider 내부 오류를 노출하지 않는다.

## 13. 관찰성과 검증

각 비동기 task는 safe progress Event, attempt count, bounded retry reason code, terminal domain alignment를 관찰 가능하게 한다. Provider smoke는 최종 runtime acceptance 항목이지만 V2 Unit fast profile에서는 사용자가 명시적으로 요청하지 않으면 실행하지 않는다.
