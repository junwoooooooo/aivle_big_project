# V25 Twin Panel Survey Alignment — Result

Status: IMPLEMENTED — test execution deferred to the Final Integration Gate.

## Files changed

- Backend product: shared current-concept resolver; Twin run/input/service/controller; Twin and Market Interview result contracts; project journey summary.
- Backend persistence: V37 Twin source-lineage migration.
- AI product: Twin input/result/caveat contracts and Market Interview identity validator.
- Frontend product: Twin API/live state/page/result/sample copy; central journey/module presentation; Market Interview and Marketing step copy.
- Tests: focused Backend Twin/Market Interview contracts, AI Twin contracts, Frontend Twin page and journey expectations.
- Documentation: this result and the V25 user-verification handoff.

## Implemented

- Preserved the canonical `/app/projects/:projectId/twin-survey` route and renamed the user-facing product to **트윈 패널 조사**.
- Separated product meaning: Market Research remains external evidence, Market Interview remains qualitative synthetic exploration, and Twin Panel Survey is a quantitative synthetic-panel simulation.
- Added a visible non-consumer-survey disclosure and scoped all displayed proportions to the virtual panel.
- Preserved the Full sampling authority: `50 / 100 / 300`, default `100`, and the existing balanced cell/calibration behavior. Donor `20 / 40 / 80` and `80:20` policies were not imported.
- Added `CurrentConceptSourceResolver` and use it for both Market Interview and Twin Panel Survey. The resolver binds the current non-stale V2 Market Seed, exact Selection/revision, and current BM revision.
- Expanded the canonical Twin input with source identity, current selected concept, validated hypotheses, BM plan/constraints, sample size, survey design version, and explicit simulation boundaries. The existing canonical hasher remains authoritative.
- Added durable Twin run BM revision and manual attempt lineage in V37. Existing run/version history remains intact.
- Added `STALE` handling for Seed, Selection revision, BM revision, and late completion/failure. Historical results are retained and never presented as current.
- Added explicit FAILED-only manual retry, limited to three domain attempts and rejected after a source change. TaskRun/JobEvent and idempotency remain the orchestration authority.
- Added `/api/v3/projects/{projectId}/twin-survey/retry`; start/retry network ambiguity recovers with one current GET and never auto-replays a mutation.
- Added explicit `synthetic=true` to the existing Twin result envelope and aligned Backend result validation and Frontend normalization.
- Removed Twin-facing qualitative interview wording from the Twin page and replaced it with virtual-panel response language. No Market Interview result is used as Twin input or prerequisite.
- Aligned central journey metadata to: 현황 점검 → 문제 발굴 → 사업성 검증 → 시장 인터뷰 → 트윈 패널 조사 → 마케팅 실행 → 출시 준비 → 결과 보고서.
- Hardened V24 Market Interview result validation to 3–5 canonical unique `P1..P5` participants and exactly one matching interview for every participant in both AI and Backend contracts.

## Tests authored

- Backend: current source/Selection/BM lineage, 50/100/300 whitelist, arbitrary sample rejection, idempotent replay/conflict, stale and late-result behavior, FAILED retry/source rejection, Market Interview independence, canonical input contents, synthetic result validation, and V24 participant/interview cardinality.
- AI: exact 50/100/300 input contract, current concept binding, no Market Interview input, explicit synthetic output, and real-population claim boundary.
- Frontend: product title/disclosure, 50/100/300 with default 100, explicit start, running/succeeded/failed/stale views, retry, ambiguity recovery, route separation, and canonical journey ordering.

## Test execution deferred

No Gradle, pytest, Vitest, production build, Docker, browser, or provider command was executed. This is intentional under the V25 implementation-first contract.

Static check actually run:

- Python AST parse for seven changed Python source/test files: `PYTHON_AST_OK=7`.
- Final `git diff --check` is recorded in the handoff report.

## Migration

- `V37__twin_panel_source_lineage.sql`
  - `source_bm_plan_revision`
  - `attempt`
  - `STALE` state check
  - BM revision and attempt constraints

## Remaining risks

- No executable test result is claimed in this stage.
- V37 has received static SQL/entity alignment review only; PostgreSQL execution is deferred.
- Visual and responsive behavior requires the Final Integration Gate review.

## Continuation point

Proceed to the next product module implementation. Execute the accumulated V23/V24/V25 gates only in the Final Integration Gate.
