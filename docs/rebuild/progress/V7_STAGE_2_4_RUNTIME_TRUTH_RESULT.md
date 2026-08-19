# V7 Stage 2 / Stage 4 Runtime Truth 결과

## 기준과 범위

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START SHA: `8a72e15c7b096a474b298b55d6d09efe6400921d`
- 구현 전 감사: `docs/rebuild/STAGE_2_4_RUNTIME_TRUTH.md`
- Stage 2 사업 검증과 Stage 4 시장 인터뷰만 변경했다.
- commit/push, Docker, 전체 regression/build, 브라우저 E2E, 외부 provider 호출은 실행하지 않았다.

## Stage 2

### 확인한 MAIN 정본

- Browser route는 `/market`, `/business-model`, `/concept-refinement` 세 개다.
- Market/BM은 `MarketResearchController` -> `MarketResearchService` ->
  `TaskType.MARKET_RESEARCH` -> subject `MARKET_RESEARCH_FULL`/`MARKET_RESEARCH_BM` ->
  `MarketResearchWorker` -> Internal AI `MARKET_RESEARCH` ->
  `app.research.product_pipeline.run_market_research` -> FULL/BM version -> current endpoint 순서다.
- MAIN의 `BUSINESS_VALIDATION` TaskType/worker/AI validation runner는 load되지만 browser producer가
  없어 `LOADED_DORMANT`다.
- MAIN 자동 authority는 Worker이며 FULL 성공 뒤 key `auto-bm-{taskRunId}`, BM 성공 뒤 key
  `auto-refinement-{taskRunId}`를 사용한다. 후속 scheduling 예외는 완료 결과를 실패로 되돌리지 않는다.

### 복원한 계약

- `MarketResearchWorker` 하나만 자동 실행 authority로 정했다.
- FULL의 `BusinessValidationSession`은 seed/selection revision/BM plan revision/Market·BM exact
  task/version을 기록하고 stale을 판단하는 projection으로 축소했다.
- Reconciler는 이미 만들어진 exact BM run의 projection 누락만 복구하며 BM/refinement를 만들지 않는다.
- `BusinessValidationCompletedEvent`와 `BusinessValidationRefinementStarter`를 제거해 두 번째
  refinement scheduler를 없앴다.
- direct Market controller start도 session projection을 생성하므로 실제 `/market` browser 경로가
  worker auto-chain에 연결된다.
- `/market`, `/business-model`, `/concept-refinement`를 각각 실제 page에 연결했다.
  `/business-validation`만 `/market` compatibility redirect로 남겼다.
- Journey child는 `1. 시장 분석`, `2. 사업 모델`, `3. 컨셉 다듬기`이며 각각 독립 route다.
- 새 독립 refinement 화면은 MAIN v2로 되돌리지 않고 FULL v3
  `/api/v3/projects/{id}/business-validation/refinement/*`와 exact session lineage를 사용한다.
- BM 결과의 다음 동작은 Stage 3가 아니라 `컨셉 다듬기`로 연결했다.

### Budget 판단

- MAIN 500/60m/63m은 약 94 document와 최대 4 reask의 약 470-call workload를 위한 값이다.
- FULL은 harness 3 + collect 80 + section attempt 최대 10 + summary 3 = 정확히 96으로 제한되고,
  section은 document 8/reask 2/120초 wall cap이며 BM은 promoted evidence를 재사용한다.
- 따라서 FULL의 96 call, worker 20분, lease/HTTP 22분을 유지했다. 근거 없이 MAIN 60분을 복사하지 않았다.

## Stage 4

### 확인한 MAIN 정본과 FULL 차이

- MAIN은 v2 controller/service/worker 뒤 `app.interview.execute_market_interview`를 사용한다.
- FULL은 v3 immutable lineage input과 `app.tasks.market_interview.deep_engine`을 사용한다.
- MAIN core를 FULL input에서 호출하려면 삭제된 약 1,800줄 core 복원과 lineage/group/evidence/
  classification/semantic-integrity result adapter가 필요하다. 작은 wrapper가 아니며 FULL의 개별
  coding degradation을 잃으므로 재사용하지 않았다.

### 선택한 engine·failure policy·timeout

- FULL `deep_engine`을 유지했다.
- respondent 출력 실패는 bounded retry 뒤 해당 participant만 제외하고 나머지를 보존한다.
- 유효 transcript의 개별 coding 실패는 `UNCLASSIFIED`로 보존하며 theme 집계에서 정직하게 제외한다.
- quote는 실제 answer substring만 사용하고 unknown theme은 제외한다.
- 전체 실패는 target unavailable/usable minimum 또는 group coverage 붕괴/codebook 붕괴/provider-wide
  장애/traceable theme 부재/schema·semantic integrity 붕괴에 제한한다.
- FULL은 concurrency 4, respondent retry, codebook repair, batch repair, single-row fallback이 있어
  MAIN보다 가볍지 않다. Worker 5분을 10분, lease 7분을 13분으로 복원하고
  `MARKET_INTERVIEW`를 MAIN과 같은 14분 read client로 연결했다. provider 1회 기본 timeout은 60초다.

### Frontend 결과

- `1 보여줄 것 확인` / `2 인터뷰 실행` 두 단계와 before/during 구조를 유지했다.
- 완료 결과 순서는 핵심 발견 -> 타겟/표본 범위 -> 주요 theme -> 접힌 이해도·차별점 ->
  대표 응답자와 원문 -> 접힌 실행 lineage/다양성/한계로 정리했다.
- theme -> respondent -> raw answer 추적성을 유지하며 응답자는 한 번에 한 명만 열어 40명을
  기본 상태에서 모두 펼치지 않는다.

## Focused validation

- Backend: Stage 2/4 지정 13 classes, 70 tests PASS. Worker 단일 authority, exact
  Market task/BM plan repository binding, stale/session lineage와 10m/13m/14m timeout을 포함한다.
- Frontend: 지정 9 files, 99 tests PASS.
- AI Market Interview: 8 tests PASS. 20명 성공, 20 usable/19 coded, 19 usable participant
  degradation, target zero, minimum usable, codebook repair, unknown theme, exact quote trace를 포함한다.
- AI Market budget/bridge: 6 tests PASS. 96-call bound, document cap, BM evidence reuse와 envelope를 포함한다.
- 변경 Frontend JS/JSX ESLint: PASS.
- `git diff --check`: PASS(CRLF 안내만 있음).

최종 `git status --short`/`git diff --stat`은 handoff 직전 별도로 기록한다.

## 실행하지 않은 검증

실제 Golden project Stage 2/Stage 4 provider 실행과 20명 뒤 40명 실행은 외부 provider 호출 및
브라우저 E2E 금지 때문에 수행하지 않았다. 절차와 성공 기준은
`docs/rebuild/verification/V7_STAGE_2_4_RUNTIME_TRUTH_USER_VERIFICATION.md`에 남겼다.
