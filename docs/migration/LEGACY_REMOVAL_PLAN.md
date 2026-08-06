# Legacy Removal Plan

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Principles and dependency order for legacy removal
- Supersedes: Legacy dead-code and data-model audits
- Implementation Status: NOT_STARTED

기존 데이터는 승계하지 않는다. 2026-08-04 승인 결정에 따라 과거 Flyway V1~V36은 최종 스키마 Baseline V1으로 통합되었고 기존 DB upgrade는 지원하지 않는다. 향후 신규 Workflow 전환과 대체 테스트 준비 후에는 새 V2 이상의 migration으로 legacy FK, index, table을 의존성 역순으로 제거한다.

제거 순서는 대체 contract/test 확보, frontend consumer와 compatibility route 제거, controller/service/job/adapter 제거, entity/repository 제거, 신규 drop migration, Object Storage 참조 해제와 artifact 삭제, 문서/테스트 정리 순이다. 실제 FK/table 순서는 DB migration 설계 Phase에서 확정한다.

Spring 직접 provider adapter와 AI Server의 presigned/로컬 output 경로는 Target AI boundary 적용 시 제거한다. compatibility 코드를 남기지 않는다.
