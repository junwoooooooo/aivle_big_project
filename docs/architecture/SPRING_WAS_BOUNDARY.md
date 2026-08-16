# Spring WAS Boundary

- Status: TARGET_CANONICAL
- Reviewed Against Current Baseline: 3aeff219d72e1be502ba4ad1cade7f7aca83d10e
- Document Phase: P2
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Spring responsibilities, flows and TaskRun ownership
- Supersedes: Legacy backend architecture documents
- Implementation Status: PARTIALLY_IMPLEMENTED_TARGET

## Currently implemented

- Spring owns TaskRun/TaskAttempt/TaskResult persistence and result adoption.
- Spring–FastAPI Internal v1 uses `/internal/v1/ai/executions` and shared identity/hash validation.
- PostgreSQL starts from the single Baseline V1; Object Storage uses the S3-compatible boundary.

## Retained compatibility

- Public `/api/v1`, AnalysisJob and direct provider adapters remain referenced compatibility paths.
- Preserved MVP services and routes remain available but are not the official Journey continuation.

## Remaining target direction

- Unified durable execution, production circuit breaker, full observability and deployment automation remain target work.
- Compatibility paths are not treated as already migrated merely because TaskRun exists.

## Owned responsibilities

Spring은 auth/JWT/refresh, admin authorization, Project owner scope, public /api/v1 stable core와 /api/v2 workflow, domain transaction, Flyway, Object Storage, audit, Service Policy, TaskRun 계열을 소유한다.

TaskRun은 업무 요청과 현재 최종 상태의 source of truth다. TaskAttempt는 개별 실행, retry, timeout, 오류와 응답을 소유하고 TaskResult는 검증된 결과, TaskArtifact는 Spring이 소유한 artifact metadata/lifecycle 방향을 가진다. 외부 AI 호출 동안 DB transaction을 유지하지 않으며 claim/lease 또는 동등한 동시성 제어, idempotency key와 input snapshot/hash를 보존한다. 상세 schema는 P2.2/P3에서 확정한다.

## Input and result flow

사용자 파일은 Spring multipart 또는 후속 public upload contract를 통해 들어온다. 초기 allowlist는 DOCX와 일반 텍스트다. Spring이 owner, policy, filename, content type, size, checksum을 검증하고 metadata와 bytes를 각각 RDB/Storage에 저장한 뒤 내용을 추출한다. AI Server에는 추출 text만 전달한다.

AI JSON 작업은 Spring이 특정 input version을 snapshot/reference하여 bounded inline request와 필요한 text chunk 배열을 만들고 TaskAttempt identity, canonical input hash와 함께 [Internal Spring–AI API v1 Contract](../contracts/INTERNAL_AI_API_V1_CONTRACT.md)로 전달한다. 상한을 넘고 chunk contract로 표현할 수 없는 입력은 `PAYLOAD_TOO_LARGE`로 종료한다. 응답은 identity, type/version, size/schema, provenance, domain invariants를 독립 검증한 뒤에만 한 번 채택한다. Network ambiguity에 따른 at-least-once 실행 가능성을 허용하되 AI Server durable idempotency를 전제로 하지 않는다.

초기 계약은 AI binary/streaming transport를 정의하지 않는다. 후속 확장에서도 Storage URL, presigned URL 또는 임시 공유 Storage를 AI Server에 제공하지 않으며 Spring이 bytes를 받아 검증하고 Storage에 기록해야 한다.

## Timeout, retry and error

- Spring이 업무 timeout, retry eligibility, attempt 생성과 최종 TaskRun 상태를 결정한다.
- claim/lease 또는 동등한 제어로 동시 attempt 실행과 lease expiry를 관리한다.
- polling과 event wake를 모두 수용하며 outbox/event wake 선택은 P3 구현 결정으로 남긴다.
- AI Server는 한 attempt 내부 provider timeout/error를 정규화한다.
- network ambiguity가 결과 중복 채택으로 이어지지 않도록 attempt/result identity를 검증한다.
- public error는 내부 provider body와 secret을 숨긴다.
- 사용자 입력 수정이 필요한 실패와 자동 retry 가능한 실패를 구분한다.

## Current gap

현재 AnalysisJob과 provider 직접 adapter는 Target TaskRun/Spring–AI boundary가 아니다. 외부 소비와 보존 MVP를 확인한 뒤 별도 전환해야 한다.
