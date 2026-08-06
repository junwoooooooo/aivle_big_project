# ASYNC EXECUTION AND JOB EVENT STANDARD v1.0

## 1. 유지 기반

TaskRun, Lease, Claim, Retry, Recovery, JobEvent, SSE, Polling fallback, Replay, Idempotency를 유지한다.

## 2. 실행 흐름

API Transaction에서 QUEUED 생성 → Commit 후 Worker Claim → Domain 저장 → Terminal Event.

JPA Entity를 Transaction 밖으로 전달하지 않고 Scalar Claim Context를 사용한다.

## 3. Event 정본

Event는 상태 변경 신호다. 화면 데이터는 Query API가 정본이다.

## 4. Event 필드

- eventId
- jobId
- projectId
- taskRunId
- module
- stageKey
- eventType
- status
- safeMessageKey
- safeMessageParams
- sequence
- occurredAt

## 5. 금지 필드

Prompt, Provider Body, 사용자 전체 입력, 첨부 원문, 법률 원문, Authorization, Key, Stack Trace.

## 6. 사용자 Timeline

내부 `claimed`, lease 갱신 같은 Event는 기본 Timeline에서 숨기거나 상세로 제한한다.

## 7. SSE

- Last-Event-ID Replay
- sequence dedupe
- completion/timeout/error cleanup
- heartbeat failure emitter 제거
- SSE commit 뒤 JSON Error 쓰기 금지

## 8. Polling

SSE 실패 시 bounded fallback. 2초 고정 Polling 금지. 초기·재연결·중요 Event·Terminal·수동 새로고침에서 Query한다.

## 9. 실패 분류

- Schema repairable
- Provider transient
- Provider permanent
- Domain needs input
- Internal permanent

무한 Retry를 금지하고 RUNNING 고착이 없어야 한다.

## 10. Provider Smoke

실제 Provider Output Schema와 모델 호환을 synthetic input으로 검증하는 Smoke를 각 핵심 AI Task의 완료 Gate로 둔다.
