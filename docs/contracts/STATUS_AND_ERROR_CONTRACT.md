# Status and Error Contract

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: Public Spring API와 Internal Spring–AI 오류의 현재 경계
- Implementation Status: IMPLEMENTED_WITH_TWO_PUBLIC_ENVELOPES

Public과 Internal 오류는 같은 계약이 아니다. Public As-Is는 실제 `ErrorCode`, `GlobalExceptionHandler`, `TaskRunV2ExceptionHandler`가 권위이고 Internal은 `INTERNAL_AI_API_V1_CONTRACT.md`가 권위다.

## Public status와 envelope

Journey Controller는 `ApiResponse`를 사용한다.

- success: `{success:true,data,meta:{requestId,timestamp}}`
- failure: `{success:false,error:{code,message,fieldErrors,retryable,...},meta:{requestId,timestamp}}`
- validation: `VALIDATION_FAILED`/400
- 예기치 않은 오류: `INTERNAL_SERVER_ERROR`/500, `retryable=true`

TaskRun v2는 별도 envelope와 `X-Correlation-Id`를 사용한다.

- success: `{data,meta:{correlationId}}`
- failure: `{error:{code,message,correlationId,taskRunId,details}}`

두 envelope는 현재 공존하며 이번 기준선에서 통일하지 않는다.

## Public HTTP status As-Is

| 영역/조건 | 실제 status | 표현 |
|---|---:|---|
| 일반 Journey 조회·저장·동기 실행 | 200 | ApiResponse success |
| Legal Precheck 시작 | 202 | ApiResponse success의 StartView |
| Concept Generation 시작 | 202 | ApiResponse success의 BatchView |
| TaskRun GET/cancel | 200 | TaskEnvelope |
| TaskRun retry | 202 | TaskEnvelope |
| Bean validation | 400 | `VALIDATION_FAILED` ApiResponse |
| TaskRun header/input validation | 400 | `VALIDATION_ERROR` TaskRun error |
| owner-scoped resource 없음 | 404 | 일반 ErrorCode 또는 `RESOURCE_NOT_FOUND` TaskRun error |
| TaskRun capability/idempotency/active conflict | 409 | TaskRun error |
| AI Provider 설정 오류 | 503 | `AI_CONFIGURATION_INVALID`, retryable=false |
| AI dependency 일시 장애 | 503 | `EXTERNAL_AI_SERVICE_UNAVAILABLE`, retryable=true |
| AI result schema/domain 오류 | 502 | `AI_RESULT_INVALID`, retryable=false |
| maintenance/service policy | 503 | 관련 일반 ErrorCode |

업무별 전체 code/status는 `backend/.../common/exception/ErrorCode.java`가 As-Is registry다. 과거 Target 문서의 `CONFLICT`, `STALE_RESOURCE`, `LEGAL_GATE_BLOCKED` 등은 실제 Controller가 그 문자열을 모두 사용한다는 뜻이 아니다.

## TaskRun 상태와 오류

| Dimension | 현재 값 |
|---|---|
| TaskRun | `QUEUED`, `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT` |
| TaskAttempt | `CREATED`, `CLAIMED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `CANCELLED` |
| TaskResult validation | `RECEIVED`, `VALIDATED`, `REJECTED`, `ADOPTED` |

TaskRun GET은 terminal 실패 상태도 resource 조회로 200을 반환하며 `state`, `retryable`, `errorSummary`, `resultResource`로 표현한다. retry는 같은 TaskRun의 새 Attempt이고 `Idempotency-Key`가 필수다.

TaskRun command에서 현재 확인된 stable code/reason은 다음과 같다.

| Code | 대표 reason | HTTP |
|---|---|---:|
| `VALIDATION_ERROR` | `IDEMPOTENCY_KEY_INVALID`, `TASK_RUN_INPUT_INVALID` | 400 |
| `RESOURCE_NOT_FOUND` | `TASK_RUN_NOT_FOUND` | 404 |
| `CAPABILITY_NOT_AVAILABLE` | `PROJECT_ARCHIVED`, `ATTEMPT_LIMIT_EXCEEDED`, `TASK_NOT_RETRYABLE` | 409 |
| `IDEMPOTENCY_CONFLICT` | `REQUEST_HASH_MISMATCH`, `RETRY_KEY_CONFLICT` | 409 |
| `TASK_ALREADY_RUNNING` | `SAME_INPUT_ACTIVE`, `TASK_NOT_CLAIMABLE` | 409 |
| `POLICY_BLOCKED` | `MAINTENANCE_MODE` | 503 |
| `AI_RESULT_INVALID` | 검증 실패 reason | service가 결정한 safe status |

## Current Journey business 상태

- Idea Origin: `DRAFT`, `CONFIRMED`
- clarification requirement: `REQUIRED_FOR_IDEA_ORIGIN`, `REQUIRED_FOR_LEGAL_PRECHECK`
- clarification status: `MISSING`, `USER_CONFIRMED`
- Legal Precheck result: `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED`
- Concept batch: `GENERATING`, `VALIDATING_ORIGIN`, `VALIDATING_LEGAL`, `COMPLETED`, `NEEDS_INPUT`, `FAILED`
- Concept eligibility: draft `PENDING`/`ELIGIBLE`/`REJECTED`, published ConceptVersion `ELIGIBLE`

현재 공식 Journey 종료점은 적격 Concept 3개 표시다. 이후 MVP 상태는 보존 구현의 업무 상태이며 공식 Journey stage로 해석하지 않는다.

## Internal AI error mapping

Spring–AI 내부 오류는 [Internal Spring–AI API v1 Contract](INTERNAL_AI_API_V1_CONTRACT.md)의 provider-neutral code와 stable reason registry를 사용한다. 이 절은 Internal 경계만 설명하며 Public path/status/envelope를 변경하지 않는다.

| Internal condition | Stable code / reason | retryable |
|---|---|---:|
| request deadline 초과 | `DEADLINE_EXCEEDED / REQUEST_DEADLINE_EXCEEDED` | true |
| service token 누락 | `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_MISSING` | false |
| service token 불일치 | `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_INVALID` | false |
| model dependency 일시 장애 | `DEPENDENCY_UNAVAILABLE / MODEL_DEPENDENCY_UNAVAILABLE` | true |
| 설정 오류 | `DEPENDENCY_UNAVAILABLE / AI_CONFIGURATION_INVALID` | false |
| 결과 schema/domain 오류 | `RESULT_SCHEMA_INVALID`과 등록된 세부 reason | false |

`retryable=true`는 현재 Attempt를 재개한다는 뜻이 아니다. 현재 Attempt는 terminal로 종료되고 Spring RetryPolicy와 attempt limit가 새 Attempt 가능 여부를 결정한다. Spring은 identity/hash와 code/reason/retryable 조합을 검증한 뒤 결과를 adopt한다.

Internal 오류의 Public 노출 방식은 실행 경로에 따라 일반 `ErrorCode` 또는 TaskRun terminal summary로 정규화된다. Provider raw body, prompt, credential, stack trace와 내부 object key는 Public 오류에 포함하지 않는다.

### Internal failure normalization registry

이 표는 Internal fixture validator가 읽는 stable TaskRun/public summary code registry다. 일반 Journey의 `ErrorCode` 문자열과 동일하다는 뜻은 아니며, 동기 경로에서는 현재 `AI_CONFIGURATION_INVALID`, `AI_RESULT_INVALID`, `EXTERNAL_AI_SERVICE_UNAVAILABLE` 등으로 변환될 수 있다.

| Stable summary code | 현재 의미 |
|---|---|
| `AI_RESULT_INVALID` | 요청·계약·결과 검증 실패의 안전한 요약 |
| `AI_SERVICE_UNAVAILABLE` | Provider 또는 외부 dependency 실패의 TaskRun 요약 |
| `PAYLOAD_TOO_LARGE` | Internal request/response byte limit 초과 |
| `TASK_TIMEOUT` | Internal request deadline 초과 |

### End internal failure normalization registry
