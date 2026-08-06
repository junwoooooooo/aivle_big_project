# Change Impact Ledger

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Cross-phase changes to earlier decisions
- Supersedes: None
- Implementation Status: PARTIAL

| Change ID | Origin Phase | Applied Phase | Previous Decision | New Decision | Reason | Documents Changed | Code Impact | DB Impact | API Impact | Test Impact | Migration Impact | Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| CHG-001 | P0 | P1 | Presigned transfer 허용 여부 미정 | AI Server presigned GET/PUT 금지 | Spring Storage 전담 확정 | AI/data architecture, contracts | artifact transfer 교체 | 없음(Phase 1) | internal AI contract 변경 예정 | boundary negative test | 없음(Phase 1) | ACCEPTED |
| CHG-002 | P0 | P1/P3 | AnalysisJob 재사용 후보 | 신규 TaskRun 계열 채택 | legacy source FK와 의미 분리 | architecture, migration, domain, current-to-target mapping | P3 `taskrun` package implemented separately; legacy job retained | V27 adds three Target tables | v2 TaskRun GET/retry/cancel | lifecycle/client/H2/PostgreSQL tests | V27 implemented; no legacy table change | IMPLEMENTED |
| CHG-003 | P0 | P1 | API version 미정 | 신규 Workflow /api/v2 | stable core와 legacy 계약 분리 | public API principles | 신규 controller/client 예정 | 없음 | v2 namespace | contract/owner tests | 없음 | ACCEPTED |
| CHG-004 | P0 | P1 | legacy data drop/archive 미정 | 테스트 데이터 이관 없음 | 보존 요구 없음 | migration docs | legacy code 제거 | legacy tables drop 예정 | legacy API 삭제 | fresh/upgrade/validate | 후속 drop migration | ACCEPTED |
| CHG-005 | P0 | P1 | runtime report 또는 persisted report 미정 | persisted FinalReportVersion | snapshot/version/export 필요 | product/domain/architecture | report aggregate/API | RDB snapshot metadata | v2 report API | version/export | 신규 schema 예정 | ACCEPTED |
| CHG-006 | P0 | P1 | baseline/fixed Persona 재사용 검토 | fixed Persona 제거, Three-Layer card | Target concept 기반 Persona 필요 | product/persona/migration | persona catalog/policy 제거 | legacy persona tables drop | legacy persona API 삭제 | 대체 card tests | 후속 drop migration | ACCEPTED |
| CHG-007 | P0 | P1 | market response MVP 존재 | 시장반응·구매확률 제외 | 실제 데이터 없는 예측 주장 방지 | scope/terminology/marketing | validation market 제거 | table drop 예정 | endpoint 삭제 | legacy tests 제거 | 후속 drop migration | ACCEPTED |
| CHG-008 | P0 | P1 | validation 이후 marketing | A/B 비교를 Marketing Workspace로 이동 | 시안 상대 비교라는 의미 정합성 | workflow/marketing/UIUX | workspace comparison slice | 신규 run 방향 | v2 marketing API | comparison claim tests | 신규/legacy migration | ACCEPTED |
| CHG-009 | P1 | P1.1 Closure | Persona Card와 Interview가 연속 범위로만 표현됨 | P7 Persona Card와 P8 Independent Persona Interview로 Phase 분리 | Persona 정의·검토와 독립 실행·실패 격리의 완료 조건을 분리 | phase status, roadmap, product/quality phase plans | P7 card slice와 P8 interview orchestration 분리 | 각 Phase schema 변경을 별도 migration으로 관리 | Persona Card API와 Interview API 구현 시점 분리 | card validation과 interview isolation test 분리 | P7/P8별 신규 migration 검토 | ACCEPTED |
| CHG-010 | P1 | P1.1 Closure | P11이 Admin, Landing, legacy removal, DB cutover, release hardening을 통합 | P11 Admin & Landing Transition, P12 Legacy Removal & Database Cutover, P13 Integrated Quality, Manual Testing & Release Hardening | consumer 전환, destructive cutover, release evidence를 독립 gate로 관리 | program/phase status, implementation phases, product/quality plans | P11 UI/운영 전환 후 P12 code 제거, P13 release hardening | P12에서만 신규 legacy drop migration | P11 consumer 전환 후 P12 legacy API 삭제 | P11 기능, P12 replacement/Flyway, P13 full/manual/CI test 분리 | P12 fresh/upgrade/validate 및 rollback 검토 | ACCEPTED |

Status는 PROPOSED, ACCEPTED, IMPLEMENTED, SUPERSEDED를 사용한다. 문서 결정이 코드 구현을 의미하지 않는다.
