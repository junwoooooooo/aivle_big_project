# AI Server Boundary

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: Spring–FastAPI 내부 실행 경계와 책임

## Responsibilities

FastAPI AI Server는 `/internal/v1/ai/executions`에서 한 TaskAttempt의 요청을 검증하고 task별 provider 또는 법률 pipeline을 실행한다. Queue, 업무 RDB, TaskRun 최종 상태와 사용자 결정을 소유하지 않는다. Spring이 실행 identity, persistence, retry policy와 결과 채택을 소유한다.

Internal AI v1 canonical request는 다음 값을 사용한다.

- `contractVersion=1.0`
- `taskSchemaVersion=1.0`
- `locale=ko-KR`
- `TextContent.contentType=TEXT`
- `TextContent.language=ko-KR`

누락, blank, `PLAIN_TEXT`, 다른 locale/language는 `INVALID_REQUEST` 계열로 거부한다. Java `TaskType`과 FastAPI dispatcher는 동일한 13개 값을 사용하며 `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION`을 포함한다.

## Response and adoption

FastAPI success response는 TaskRun ID, TaskAttempt ID, taskType, taskSchemaVersion, correlationId, canonicalInputHash, resultSchemaVersion과 result body를 echo한다. Spring의 공통 `InternalAiExecutionClient`가 이를 검증한 뒤에만 Worker 또는 동기 Journey Service가 domain invariant를 검사하고 adopt한다.

Concept Legal Validation의 현재 공식 경로는 `GUARDRAIL_BATCH`다. 결과는 입력 candidateKey 집합과 정확히 일치해야 하며 누락·중복·알 수 없는 key와 extra field를 거부한다.

## Error semantics

- 요청 deadline 초과: `DEADLINE_EXCEEDED / REQUEST_DEADLINE_EXCEEDED / retryable=true`
- Authorization 누락: `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_MISSING / retryable=false`
- Authorization 불일치: `UNAUTHORIZED_INTERNAL_CALL / SERVICE_TOKEN_INVALID / retryable=false`
- provider/model/legal dependency와 결과 오류: Internal contract의 stable code/reason registry로 제한

`retryable=true`는 현재 Attempt를 되살린다는 뜻이 아니라 Spring retry policy가 새 Attempt를 허용할 수 있다는 뜻이다.

## Execution styles

FastAPI endpoint 자체는 request/response 단위로 동기 실행한다. 호출 측 Spring은 Legal persistent worker, Concept in-memory executor, 일부 Journey service 내부 동기 claim/execute를 함께 사용한다. 이번 기준선은 이들을 하나의 실행 방식으로 통일하지 않는다.

## Hard boundaries

- FastAPI가 Spring 업무 RDB나 JPA entity를 직접 소유하지 않는다.
- Browser는 Internal AI endpoint를 직접 호출하지 않는다.
- 사용자 JWT/session과 실제 secret을 payload나 오류에 포함하지 않는다.
- FILE bytes/base64와 임의 Storage credential을 Internal execution payload로 전달하지 않는다.
- AI 결과는 Spring 검증과 adopt 전까지 업무 사실이 아니다.

기존 `/api/v1`과 artifact/banner 관련 코드는 외부 소비 여부가 확인되지 않았으므로 보존한다. 현재 공식 Journey의 Internal execution 계약과 구분한다.
