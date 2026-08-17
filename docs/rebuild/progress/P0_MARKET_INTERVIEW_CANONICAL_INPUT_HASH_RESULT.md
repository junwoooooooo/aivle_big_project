# P0 Market Interview canonical input hash 결과

## 변경 파일

- `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewInputFactory.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketinterview/MarketInterviewServiceTests.java`
- `backend/src/test/java/com/aivle/backend/taskrun/integration/MarketInterviewCanonicalInputIntegrationTests.java`

## 구현 계약

- Market Interview의 내부 Task envelope schema는 `1.0`으로 canonical hash를 계산한다.
- 업무 입력은 `market-interview-input-v2`, `schemaVersion=2.0`을 유지한다.
- start와 retry 모두 동일한 Task schema `1.0`으로 정확한 입력 JSON을 hash한다.
- 생성된 TaskRun과 Internal AI 실행 요청은 `taskSchemaVersion=1.0` 및 동일 canonical input hash를 전달한다.
- sampleSize 20/40/80, replay, 변경 입력의 `REQUEST_HASH_MISMATCH` 계약을 유지한다.

## 실제 실행한 검사

- Market Interview focused backend 테스트: 통과
- 실제 `CanonicalInputHasher` + 실제 `TaskRunService` 생성 검증 통합 테스트: 통과
- TaskRun canonical/hash 테스트: 통과
- Internal AI execution client 테스트: 통과
- 백엔드 전체 테스트: 697개 통과, 실패 0, 오류 0, skip 0
- `git diff --check`: 통과

## 의도적으로 생략한 검사

- 실제 provider 호출: 금지 조건에 따라 생략
- Docker 및 실제 브라우저 smoke: 이번 요청의 검증 목록에 포함되지 않아 생략
- frontend 테스트/빌드: frontend 변경 금지 및 변경 없음

## 남은 위험

- 실제 배포 환경의 인증·DB를 사용하는 브라우저 POST는 별도 수동 확인이 필요하다.
- 백엔드 전체 테스트 종료 시 task scheduler shutdown 대기 경고가 있었으나 테스트 결과에는 실패가 없었다.

## 정확한 연속 지점

- 현재 변경 상태를 유지한 채 실제 환경에서 `POST /api/v3/projects/{id}/market-interview`에 `{"sampleSize":20}`을 보내 HTTP 202 및 생성된 TaskRun의 envelope v1 / input v2를 확인한다.

