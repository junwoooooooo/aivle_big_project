# Re-foundation Acceptance Criteria

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Cross-phase acceptance and evidence conditions
- Supersedes: Legacy phase readiness documents
- Implementation Status: PARTIAL

## Every phase

- 범위와 금지 변경을 지킨다.
- Target과 Current를 구분한다.
- decision/change/open/evidence를 갱신한다.
- code/test/canonical docs가 일치한다.
- 실제 command/result를 기록하고 미실행을 성공으로 쓰지 않는다.
- secret·개인정보·provider raw body를 evidence에서 제외한다.

## Platform

Auth/owner/admin/audit가 회귀하지 않고 Spring만 RDB/Storage를 관리해야 한다. AI Server의 DB/Storage/presigned/local artifact 접근이 없어야 한다. TaskRun 상태는 Spring source of truth이며 스키마 변경은 통합 Baseline 이후 새 version으로만 추가한다.

## Migration

통합 Baseline은 빈 PostgreSQL에서 fresh/validate를 통과해야 한다. 기존 DB upgrade는 지원하지 않는다. 대체 test/consumer 전에 legacy table/entity/API를 삭제하지 않는다.

## Workflow and release

각 slice는 owner, version/provenance, stale, safe error, AI contract, frontend와 필요한 E2E를 포함한다. P11은 Admin/Landing 전환 검증, P12는 legacy 제거와 Flyway cutover 검증, P13은 full local gate와 확인 가능한 Remote CI/security/deployment/manual evidence를 요구한다.
