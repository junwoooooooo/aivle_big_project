# DECISION LOG

## D-001 새 파이프라인 전환

- 상태: 승인 대기
- 결정: 기존 G7 이후 계획을 폐기하고 R0~R7 재구축 체계로 전환한다.

## D-002 법률 검토 위치

- 결정: 별도 사용자 단계에서 제거하고 5 Concept Factory 내부 Loop로 통합한다.

## D-003 전역 Stage

- 결정: 전역 Stage 잠금을 제거하고 모듈별 상태와 Soft Dependency를 사용한다.

## D-004 외부 분석

- 결정: Entity·DB 공유 대신 Snapshot Handoff를 사용한다.

## D-005 기획 Revision 표시

- 결정: 사용자에게 v1/v2 대신 의미 기반 이름을 표시한다.

## D-006 DB

- 결정: 보존 데이터가 없으므로 새 Baseline으로 초기화한다.

## D-007 마케팅

- 결정: AIdev의 생성·편집 부분만 선별 이식하고 검증·A/B·Persona 종속은 제외한다.

## D-008 최종 DB Baseline Squash 시점

- 결정: R1에서는 기존 Migration을 유지하고 신규 파이프라인 Foundation을 additive Migration으로 추가한다. 최종 Clean Baseline Squash는 R7에서 수행한다.
- 이유:
  - Legacy Entity가 아직 Compile 및 Entity Scan 대상이어서 R1에서 기존 Table을 제거하면 `ddl-auto: validate` Runtime 검증이 실패할 수 있다.
  - R1A·R1B에서 신규 사용자 Surface와 `/api/v3` API는 이미 Legacy 경로와 분리됐다.
  - 보존할 운영 데이터가 없으므로 Legacy Entity 제거가 끝나는 R7에서 최종 Schema만으로 안전하게 Clean Baseline을 만들 수 있다.
- 제약: R1~R6은 기존 Migration을 수정·재정렬하거나 legacy 행을 변환하지 않는다.

## D-009 Concept Provider Failure 상태 경계

- 상태: 승인
- 결정: `PROVIDER_FAILURE`는 Concept Slot의 영속 상태가 아니다. Provider 실패는 Concept Attempt 오류 분류로 기록한다.
- 공식 Attempt 오류 분류: `SCHEMA_INVALID`, `TRANSIENT_PROVIDER_FAILURE`, `PERMANENT_PROVIDER_FAILURE`, `ORIGIN_INVALID`, `LEGAL_REDESIGN_REQUIRED`, `LEGAL_REJECTED`, `INSUFFICIENT_INFORMATION`, `INTERNAL_EXECUTION_ERROR`.
- 전이:
  - transient provider failure는 retry가 남아 있으면 현재 Slot 실행 상태를 유지하고 동일 Slot을 최대 1회 재시도한다. 소진 시 `REPLACING`으로 전이한다.
  - permanent provider failure는 Slot과 Run을 `FAILED`로 전이하고 `retryable=false`로 종료한다.
  - schema invalid는 `SCHEMA_INVALID` 후 `REPAIR` 1회를 허용하고 재실패 시 `REPLACING`으로 전이한다.
- 근거: Provider 장애는 사용자 작업 진행 상태가 아니라 Attempt 실행 결과이며, Slot 상태로 영속화하면 복구 정책과 사용자 진행 표시가 결합된다.
- 영향: R3A enum과 V8 constraint는 변경하지 않는다. R3B Worker는 Attempt error classification을 저장하고 이 ADR의 전이만 적용한다.

## D-019 Concept 내부 요청 계약 오류 경계

- 상태: 승인
- 결정: Backend가 생성한 AI 내부 DTO가 AI 입력 schema를 위반한 경우 `PERMANENT_PROVIDER_FAILURE`가 아니라 `REQUEST_CONTRACT_INVALID`로 기록한다.
- 전이: 첫 요청 계약 오류에서 현재 Run을 즉시 `FAILED`로 종료하며, 아직 시작하지 않은 Slot의 Attempt나 AI 호출을 만들지 않는다.
- 재시도: `retryable=false`, `canResume=false`, `nextAction=FIX_SYSTEM_AND_START_NEW_RUN`이다.
- 근거: AI 서비스와 Provider의 가용성 문제가 아니라 producer/consumer contract drift인 run-global deterministic system failure다.
- 계약 소유권: Backend `ConceptFingerprint.businessSummary`가 producer이며 AI Candidate와 Distinctness 입력이 `BusinessFingerprint v1` consumer다. `contracts/concept` fixture를 양쪽 테스트가 공유한다.
