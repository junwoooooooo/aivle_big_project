# V2-10E — Long-running Action Async Hardening 결과

## 현재 상태

IMPLEMENTATION COMPLETE

RUNTIME ACCEPTANCE PENDING

초기 PARTIAL 이후 E1/E2/E3로 분할하여 완료했다. Concept Selection alternative/Delta Legal, TechOps proposal, Finance lazy estimate가 모두 실제 claimed TaskRun worker 경계로 전환되었고 각 분할 Unit의 targeted validation이 통과했다.

## 초기 PARTIAL 이력 (보존)

초기 실행은 Fast profile 실행 단위 시간 상한에서 `PARTIAL — 구현 미완료`로 중단되었으며, 당시 V2-10F는 선행 E 완료 조건 때문에 시작하지 않았다. 아래 조사 내용과 continuation point는 그 시점의 기록으로 보존한다.

## 확인한 현재 동기 경계

- `ConceptSelectionService.decide`: `CONCEPT_HYPOTHESIS_ALTERNATIVE`와 Delta Legal Provider를 HTTP Action 트랜잭션 안에서 호출한다.
- `TechOpsService.initialize/decideProposal`: 초기 proposal과 REQUEST_ALTERNATIVE Provider를 동기 호출한다.
- `FinancialService.initialize/decideEstimate`: estimate와 REQUEST_ALTERNATIVE Provider를 동기 호출한다.

## 이번 Unit에서 변경한 파일

- 없음. TaskRun/SSE 불변식을 충족하지 못하는 임시 background-thread 구현을 추가하지 않았다.

## 실제 실행한 검사

- 위 서비스의 동기 gateway 호출 위치를 정적 대조했다.
- E 전용 compile/test는 실행하지 않았다.

## 의도적으로 생략한 검사

- E1~E5 async 구현 및 테스트 전부.
- 전체 suite, provider/browser smoke.

## 남은 위험

- 대안, Delta Legal, TechOps proposal, Finance estimate 요청은 provider 지연 동안 HTTP timeout/화면 정지 가능성이 남아 있다.
- 기술 실패와 legal ineligible의 비동기 terminal state 분리가 아직 없다.

## 정확한 계속 지점

1. 기존 Idea/Concept worker와 동일한 claim/start/adopt/fail 패턴으로 네 Action subject를 표현할 TaskRun command payload를 정의한다.
2. Action endpoint는 idempotency key로 non-terminal replay만 재사용하고 즉시 `202 QUEUED + taskRunId`를 반환한다.
3. worker 성공 시에만 proposal/finalValue를 transactionally 반영하고 JobEvent를 terminalize한다.
4. retry는 반드시 새 TaskRun ID를 만들고 이전 terminal run을 immutable history로 둔다.
5. Delta technical failure는 decision을 reject하지 않고 retryable FAILED, legal ineligible만 대안 필요 상태로 저장한다.
6. frontend는 Query API를 정본으로 제안 생성/법률 검토/실패/재시도/새로고침 복원을 표시한다.
