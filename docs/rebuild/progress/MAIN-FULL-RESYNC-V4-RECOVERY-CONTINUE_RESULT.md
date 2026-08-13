# MAIN-FULL-RESYNC-V4-RECOVERY-CONTINUE 결과

## 조사 범위

- recovery checkpoint와 `origin/full`, donor main tree/blob/call-path 비교
- Market generated ledger, orchestration, recollect
- TechOps confirmed-input provenance
- AI 전체 tests 및 research quality script
- Backend compile 및 PostgreSQL 제외 전체 package tests
- Frontend active path, build, lint, 전체/영향 tests
- Docker/PostgreSQL live migration 실행 가능성

## 발견 및 변경

- checkpoint의 P0 run-path, exact main pipeline, quality resource, TechOps provenance 복구는 모두 보존돼 있었다.
- `ai/app/research/serialize.py`에서 main의 `extract_capped`와 `fetch_empty` 사용자 진단 의미가 누락된 것을 새로 발견했다.
- 두 donor 분기를 복구하고 `ai/tests/test_v4_market_orchestration.py`에 재발 방지 2건을 추가했다.
- Product recollect raw ledger persistence는 여전히 PARTIAL이다.

## 검증 요약

- AI: `654 passed / 4 failed / 1 skipped`; 4건은 donor main 동일 재현.
- Backend 비-PostgreSQL: `463 passed / 2 failed`; 두 실패는 V4 diff 0 범위에서 단독 재현.
- Frontend 영향: `156/156 passed`; 전체 `418 passed / 18 failed`; build PASS; lint 10 errors.
- PostgreSQL/Flyway live: Docker와 완전한 server resource가 없어 미검증.
- AI unexplained diff: 0.

## 남은 위험과 연속 지점

1. raw ledger를 기존 Artifact/TaskRun 저장 구조에 안전하게 결속한 뒤 Product recollect end-to-end를 추가해야 한다.
2. Docker 또는 완전한 PostgreSQL server 환경에서 V1→V23과 `ddl-auto=validate`를 실행해야 한다.
3. 기존 CPV2/Concept/Idea/frontend 실패와 design-score fixture를 별도 품질 작업으로 정리해야 한다.

상세 표와 수치는 `docs/rebuild/MAIN-FULL-RESYNC-V4-RECOVERY-CONTINUE_FINAL_REPORT.md`를 따른다.

