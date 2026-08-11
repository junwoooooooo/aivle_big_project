# Session 2 Market + BM + Twin 이식 결과

## 판정

Market Research, Business Model, Twin Stimulus Draft, Twin Survey를 `donor-market@f3d6dbd`에서 Target Production Platform으로 이식했다. donor 분석 알고리즘·계약·결과 정보는 보존하고, 실행 경계만 Target의 TaskRun/Worker/JobEvent/SSE/current authority에 결합했다.

## Market

- Backend는 current `ConceptPortfolioSelection(READY_FOR_MARKET)`과 current non-stale `MarketAnalysisSeedSnapshot(CONCEPT_PORTFOLIO_V2)`을 ownership/readiness gate로 검증한다.
- immutable TaskRun input에는 arbitrary selected Concept를 donor Concept 계약으로 변환한 snapshot, confirmed hypotheses, final legal result, selection/revision/seed 식별자와 hash를 결속한다.
- 공식 FULL은 `sourceRun`이나 3개 sample label을 사용하지 않는다.
- `product_runner.py`는 Task별 임시 `RESEARCH2_RUNS_DIR`에서 donor `research2/run.py`를 `--from` 없이 실행하여 A1→A2→A3→A4/ledger→estimate/chain/verdict/cards/scorecard를 연결한다.
- Task 임시 원장은 종료 시 제거되고, canonical authority는 Backend `MarketResearchVersion`이다.
- donor sample 원장 3종은 회귀 fixture로만 보존하며 AI Docker image에서는 제외하고 compose에서 read-only mount한다. 공식 경로에서는 `fixtureMode` 없이는 사용할 수 없다.
- `MarketResearchRun`/`MarketResearchVersion`은 mode, TaskRun, input hash, lineage, state/error, score counts, decision/confidence, evidence/caveat counts, result JSON을 보존한다.
- Worker는 TaskResult 채택과 Version materialization을 한 transaction으로 처리하고 Version을 TaskRun당 하나만 허용한다. terminal JobEvent는 materialization 뒤 발행한다.
- GET current는 상태 전이·동기화·materialization 없이 DB 정본만 읽는다.

## Business Model

- donor의 4개 planned cell과 예산/기간/인원 constraint를 `BmPlanPreparation` revision으로 저장한다.
- BM TaskRun은 정확한 `MarketResearchVersion`과 정확한 BM plan revision을 결속한다. current Market lineage가 바뀌면 과거 Market을 새 BM의 source로 사용하지 않는다.
- `Market Result → MarketJoinData → BM → Financial Handoff` 관계를 유지하며 evidence ID, grade, source, caveat, formula, inputs, assumptions, not-found를 전달한다.
- BMC 9칸, fit/consistency, strength/weakness/risk, legal, evidence/caveat, financial handoff와 missing financial inputs를 결과와 UI에 보존했다.

## Twin

- donor `twin/**`의 task classifier, serviceable gate, 50/100/300 sampling, 1~4 pair, stratification, bilateral X/Y, adaptive repeat, aggregation, Δ/CI/MDE, profiles/interviews, response classes, caveat/not-measurable 계약을 수정하지 않았다.
- `TWIN_STIMULUS_DRAFT`와 `TWIN_SURVEY`를 각각 TaskRun/Worker/Internal AI/JobEvent에 등록했다.
- Draft source는 Backend가 current selected Concept와 current Market Seed에서 생성한다. sample fallback이나 빈 synthetic success는 없다.
- Survey Run에는 source seed/selection/revision lineage를 보존하여 upstream 변경 시 ProjectModuleStatus가 `STALE`을 표시한다.
- Twin Bank는 Git/Docker image/DB/MinIO에 넣지 않고 `./ai/app/twin/bank:/app/app/twin/bank:ro`, `TWIN_BANK_DIR=/app/app/twin/bank` 계약을 유지한다. 미마운트/manifest/frame 오류는 `TWIN_BANK_UNAVAILABLE`로 실패한다.

## Runtime과 UI

- 공식 흐름은 `POST 202 → TaskRun → Worker → Internal AI → atomic materialization → JobEvent → Job/Project SSE → canonical GET refresh → Work Center`다.
- Frontend 네트워크 polling과 GET synchronize를 제거했다. Project SSE의 `liveRevision`이 canonical GET을 재실행하며 interval은 경과시간 표시에만 사용한다.
- Project Shell route와 module status에 Market, Business Model, Twin Survey를 연결했다.
- `docs/integration/02_DONOR_UI_INFORMATION_INVENTORY.md`의 Session 2 companion matrix에서 모든 Market/BM/Twin 필수 행을 `PORTED`로 기록했다. `NOT_PORTED`는 없다.

## DB

- 기존 V1~V13은 수정하지 않았다.
- V14: Market Research Run/Version 및 immutable lineage/FK/unique/constraint.
- V15: Twin Survey Run/Version, source selection/seed lineage, sample/unique/constraint.
- V16: BM Plan Preparation revision과 plan/constraint JSON.
- 새 V17 이상 migration은 없다.

## 검증 결과

- 실제 PostgreSQL 17.10 Testcontainers: 빈 schema V1→V16 16개 migration, Flyway validate, JPA `ddl-auto=validate` PASS.
- Backend: Session 2 최종 targeted 27건 PASS, TaskRun/JobEvent/Project SSE 포함 확장 targeted 45건 PASS, `compileJava` PASS.
- PostgreSQL: 5건 PASS. 빈 schema V1→V16, upgrade 경로, Flyway validate, FK/constraint, JPA validate 포함.
- AI: Market/BM/Twin/Product integration 172건 PASS, 환경 조건 1건 skip, `compileall` PASS. A2 KOSIS/DART 53건, A3 Web 54건, A1/normalization 90건 오프라인 경계 PASS.
- Frontend: Market/BM/Twin render/state/SSE refresh 22 files/187건 PASS, Vite production build PASS(260 modules).
- 정적 검증: `git diff --check` PASS, truncation/conflict marker 0건, V1~V13/CPV2/Persona/Finance/Marketing/TechOps product diff 모두 0건.

donor의 `test_verdict_canvas.py`는 Git에 없는 로컬 `unified-02` 원장을 요구하므로 해당 단독 스크립트 전체는 검증 집계에서 제외했다. source-controlled 3개 sample 원장과 contract/golden 회귀는 별도로 실행하며, 공식 Product 실행에는 이 원장을 사용하지 않는다.
