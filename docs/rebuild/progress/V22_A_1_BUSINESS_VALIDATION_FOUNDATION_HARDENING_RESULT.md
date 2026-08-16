# V22-A.1 Business Validation Foundation Hardening 결과

- START SHA: `5849b91fbf261e0e03a7255ae2f7988c0ebd87c8`
- 복구 게이트: `full`, `HEAD == 5849b91fbf261e0e03a7255ae2f7988c0ebd87c8`; 이전 V22-A.1 변경을 보존해 재개
- 상태: **COMPLETE · READY FOR V22-B**

## 경계 보강

- **STALE ACTIVE ROOT CAUSE:** stale을 계산하고 return만 해 persisted active state가 남았다. `STALE` terminal state와 `markStale()`을 추가하고 reconciler가 이를 저장한다. `ACTIVE_STATES`에는 STALE이 없으며 이후 결과가 도착해도 session transition method가 STALE을 되돌리지 않는다.
- **DUPLICATE ACTIVE ROOT CAUSE:** `start()`가 active session 확인 전에 Market을 시작했다. project row `PESSIMISTIC_WRITE` lock을 먼저 획득하고, same-key replay를 보존한 뒤 다른 key의 non-stale active session은 그대로 반환한다. stale active는 terminal 처리한 뒤에만 새 start를 허용한다.
- **BM PLAN REVISION ROOT CAUSE:** Market 완료 뒤 BM이 최신 plan을 다시 읽었다. coordinator가 start 시점의 revision을 신규 session에 pin하고, continuation/retry 직전 동일 revision만 BM TaskRun 생성을 허용한다. legacy null은 추측하거나 backfill하지 않고 기존 호환 경로로 처리한다.

## Migration

- V28 수정 없음.
- `V29__business_validation_input_hardening.sql`: nullable `source_bm_plan_revision` 한 칼럼만 추가.
- PostgreSQL 실행은 생략: **ENVIRONMENT VERIFICATION PENDING**.

## Focused test

- focused command 시도 2회: 첫 시도는 Gradle 배포본 다운로드 네트워크 제한으로 test 실행 전 차단, 승인 재실행 1회.
- `BusinessValidationCoordinatorTests`: 승인 재실행에서 **10 tests PASS**(failure/error/skipped 0); test suite 실제 실행 1회.
- backend compile/classes: 같은 command에서 PASS.
- `git diff --check`: PASS.
- Frontend diff/test/lint/build, 전체 suite, Docker, browser, 외부 AI: 의도적으로 생략.

## 변경하지 않음

Frontend, Concept Refinement, Market Interview, donor AI/research2/structured provider, Launch Readiness, Finance, Legal, Marketing, Final Report, Twin Survey, ProjectLayout.

## 계속 지점

V22-A.1 세 경계는 닫혔다. 다음 단계는 **READY FOR V22-B**이며 Concept Refinement는 V22-B의 별도 명시 범위에서만 시작한다.
