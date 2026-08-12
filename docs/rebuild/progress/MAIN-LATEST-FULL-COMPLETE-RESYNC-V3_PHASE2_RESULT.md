# MAIN-LATEST-FULL-COMPLETE-RESYNC-V3 PHASE 2 결과

## 1. 조사한 범위

- Market canonical Concept/Market FULL/BM lineage, TechOps preparation/snapshot/report, Finance, Marketing, Twin backend
- ownership, current/stale/history, TaskRun/Attempt, retry/recovery, SSE, downstream handoff

## 2. 발견한 gap과 처리

- `PARTIAL`: Market AI input factory가 main의 제품 입력을 축약했다. 명시적 9개 Concept field, structured hypotheses, target region, price parser, BM plan, legal, competitor seed를 포함하도록 복구했다.
- `MISSING`: 프로젝트별 경쟁사 seed 저장·조회·검증 API를 full pipeline에 추가했다.
- `CONTRACT_MISMATCH`: current BM plan constraint가 Research2 입력까지 전달되지 않았다. current BM source에서 전달하도록 연결했다.

## 3. 변경 파일

- `backend/.../pipeline/market/MarketResearchInputFactory.java`
- `MarketResearchService.java`, `MarketResearchController.java`
- `ResearchCompetitorSeed*.java`, 관련 backend tests

## 4. main에서 가져온 것

- 실제 선택 Concept fail-closed, 구조화 hypothesis, BM planning constraint, competitor seed의 제품 의미
- seed 최대 8개, 중복 차단, 표시 순서 보존, empty warning

## 5. full 때문에 adapter한 것

- source 결정은 full CPV2 selection/current Market/BM lineage를 authority로 유지했다.
- 실행은 full TaskRun/Worker/JobEvent/SSE, persistence는 full current/history/snapshot을 사용한다.

## 6. main과 일부러 다르게 둔 것

- main의 synchronous TechOps advisory controller와 localStorage persistence는 사용하지 않는다.
- Finance는 main의 과거 TechOps prerequisite를 복원하지 않고 current Market FULL + current BM exact lineage를 유지한다.

## 7. 검증

- Market backend targeted: 19 passed
- TechOps/Finance/Marketing/Module backend targeted: 24 classes, 91 passed
- 합계 targeted: 110 passed, 0 failed, 0 skipped
- backend 전체 회귀는 124초 제한에서 완료되지 않아 `ENVIRONMENTAL_INCOMPLETE`로 분류했다.

## 8. 남은 문제

- 실제 PostgreSQL/Flyway, worker lease/recovery, SSE browser journey는 live 미검증이다.

## 9. 변경량

- Market backend 제품 adapter와 seed domain에 국한했다. TaskRun core 및 타 모듈 계산 코드는 변경하지 않았다.

## 10. 계속 지점

- PHASE 3에서 migration 번호·dependency·runtime 설정을 검증한다.
