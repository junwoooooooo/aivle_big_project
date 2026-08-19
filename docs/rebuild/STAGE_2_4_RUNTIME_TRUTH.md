# V8 Stage 2 / Stage 4 Actual Runtime Truth

## Identity and scope

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START SHA: `a0219e0e54768003ab491a95bd892403a27da6d5`
- Scope: Stage 2 business validation and Stage 4 market interview only.
- Presentation truth: files imported by `origin/main` `AppRouter.jsx`, not same-named FULL files.
- Backend truth: FULL v3 lineage, TaskRun, worker, AI and materialization chain.
- Not run: Docker rebuild, full regression, provider call, paid call, browser E2E.

## MAIN active donor import graph

`main.jsx -> App.jsx -> AppRouter.jsx` reaches these donors.

### Stage 2 market

`/market -> features/market/MarketResearchPage.jsx`

Imports used by that page: `ResearchBasisCard.jsx`, `MarketResultBody.jsx`, `marketResult.js`,
`marketApi.js`, `useMarketPolling.js`, `market.css`, and shared `ProjectWorkspace`,
`ProjectStageHeader`, `Button`, `Alert` and loading components.

### Stage 2 business model

`/business-model -> features/market/BmCanvasPage.jsx`

Imports used by that page: `BmResultBody.jsx`, `BmCanvas.jsx`, `marketResult.js`,
`marketApi.js`, `useMarketPolling.js`, `market.css`, and the same shared shell primitives.

### Stage 2 refinement

`/concept-refinement -> features/market/ConceptRefinementPage.jsx`

Imports used by that page: `RefinementSummary.jsx`, `useConceptRevision.js`,
`conceptRevision.js`, `marketResult.js`, `market.css`, and the shared shell primitives.
The MAIN v2 commands are presentation evidence only and are not backend donors.

### Stage 4

`/market-interview -> features/market-interview/MarketInterviewPage.jsx`

Imports used by that page: `ConceptBoardEditor.jsx`, `SampleSizePicker.jsx`,
`InterviewCard.jsx`, `marketInterviewResult.js`, `useMarketInterviewPolling.js`,
`marketInterviewApi.js`, `sampleSize.js`, `market-interview.css`, and shared UI primitives.

## V7 FULL replacements that were not presentation authority

- `features/market/MarketResearchPage.jsx` as it existed at FULL START: giant report-first layout,
  local navigation and integrated FULL renderer.
- `features/market/BmCanvasPage.jsx` as it existed at FULL START: Plan/PreparedPlan,
  operational editing, financial handoff and duplicate verdict presentation on the normal path.
- `features/business-validation/pages/ConceptRefinementPage.jsx`,
  `components/ConceptRefinementPanel.jsx`, `components/RefinedConceptSummary.jsx`.
- `features/market-interview/pages/MarketInterviewPage.jsx` and
  `features/market-interview/components/MarketInterviewResult.jsx`.

These files may remain for compatibility or history, but `AppRouter.jsx` no longer imports them for
the four canonical routes.

## Stage 2 MAIN browser -> AI -> browser

### Market FULL

`/market -> MarketResearchPage -> POST /api/v3/projects/{id}/market-research ->
MarketResearchController.startFull -> MarketResearchService.startFull ->
TaskRun(MARKET_RESEARCH, subject MARKET_RESEARCH_FULL) -> MarketResearchWorker ->
InternalAiExecutionClient -> POST /internal/v1/ai/executions -> executions.py MARKET_RESEARCH ->
app.research.product_pipeline.run_market_research(mode FULL) -> MarketResearchContract ->
MarketResearchService.complete -> MarketResearchVersion(FULL) -> GET .../market-research/current ->
MarketResearchPage -> MarketResultBody`.

### Business model

`/business-model -> BmCanvasPage -> POST /api/v3/projects/{id}/business-model ->
MarketResearchController.startBm -> MarketResearchService.startBm -> exact FULL version binding ->
TaskRun(MARKET_RESEARCH, subject MARKET_RESEARCH_BM) -> MarketResearchWorker -> same AI branch ->
product_pipeline(mode BM) -> MarketResearchVersion(BM) -> GET .../business-model/current ->
BmCanvasPage -> BmResultBody -> BmCanvas`.

### Automatic authority

`MarketResearchWorker` remains the only automatic execution authority:

- FULL completion schedules BM once with `auto-bm-{fullTaskRunId}`.
- BM completion schedules refinement round one once.
- scheduling occurs after result materialization;
- downstream scheduling exceptions are caught and logged, so completed FULL/BM results remain
  successful;
- `BusinessValidationSession` remains lineage/session projection, not a second execution producer.

FULL retains its audited 96-call / 20-minute / 22-minute lease-and-transport boundary. V8 does not
alter the V7 execution authority or workload budget decision.

## Stage 2 FULL browser -> v3 refinement -> browser

The restored pages use FULL current v3 Market/BM endpoints. Refinement uses:

`/concept-refinement -> MAIN ConceptRefinementPage -> GET
/api/v3/projects/{id}/business-validation/refinement/presentation ->
ConceptRefinementPresentationService -> ConceptRefinementService.current + exact session cycle
round history + ConceptRefinementFinal/seed projection -> MAIN RefinementSummary`.

Commands remain FULL v3:

- selected proposal keys: `/decision`, then `/apply`;
- decline and request another round: `/next`;
- failed proposal: `/retry`;
- accepted/keep-current completion: `/finalize`.

The projection maps `proposalKey`, round, field, title, before/after, rationale, source,
`evidenceIds`, legal reference and accepted `true/false/null`. It includes earlier rounds from the
same `BusinessValidationSession`. It does not fabricate narrative or legal prose; MAIN's existing
concept-document/highlight fallback is used. Evidence links remain `/market#sec-<subject>` and only
exist for stored evidence IDs.

Canonical routes are `/market`, `/business-model`, `/concept-refinement`; `/business-validation`
is compatibility-only and redirects to `/market`. Module next action is `/concept-refinement`.

## Stage 4 MAIN complete graph

`/market-interview -> origin/main MarketInterviewPage -> GET v2 board -> editable six-cell board ->
POST v2 {conceptBoard,sampleSize} -> pipeline.market.MarketInterviewController/Service/Worker ->
TaskRun(MARKET_INTERVIEW) -> InternalAiExecutionClient -> executions.py MARKET_INTERVIEW ->
app.interview.execute_market_interview -> targeting/sampling/respondent/coding/analysis/saturation ->
version/current -> MAIN result presentation`.

This is the presentation donor only. Its old v2 backend authority and DB state were not restored.

## Stage 4 FULL complete graph after V8

`/market-interview -> MAIN MarketInterviewPage ->
GET /api/v3/projects/{id}/market-interview/board -> MarketInterviewController ->
MarketInterviewService -> MarketInterviewSourceResolver -> current non-stale finalized
ConceptRefinementFinal -> MarketInterviewInputFactory.board (LLM 0) -> editable six-cell board`.

Start and return:

`POST /api/v3/projects/{id}/market-interview {conceptBoard,sampleSize} -> server exact-field and
price validation -> market-interview-input-v2 with conceptRefinementFinalId/seed/selection/
selectionRevision/BM revision/hash -> TaskRun(MARKET_INTERVIEW) -> MarketInterviewWorker
(10m budget, 13m lease) -> InternalAiExecutionClient (14m read boundary) -> executions.py ->
app.tasks.market_interview.deep_engine -> respondent bounded retry -> codebook/batch repair/
single-row fallback/UNCLASSIFIED -> strict market-interview-result-v2 -> MarketInterviewRun ->
GET .../current -> deterministic FULL-result-to-MAIN-view adapter -> MAIN result renderer`.

### Finalized-source gate

The Stage 4-only `MarketInterviewSourceResolver` requires:

- latest `ConceptRefinementFinal`;
- referenced round is `FINALIZED`, same final id, final seed and session;
- current selection id and hypothesis revision match;
- final seed is current, non-stale and belongs to the same project/selection;
- current BM plan revision matches;
- final JSON is `concept-refinement-final-v1` and its final seed/revisions match the entity.

No final returns `MODULE_INPUT_STALE` from board/start. Module status is `NOT_READY` with required
input `conceptRefinementFinal`. A changed final, seed, selection revision or BM revision makes an
existing run stale. `CurrentConceptSourceResolver` and Stage 1/3/5/6 consumers are unchanged.

### Editable stimulus contract

The board has exactly `conceptName`, `targetUsers`, `problemScenario`, `featureSet`,
`differentiators`, `priceKrw`. The first five are stimulus wording; price must equal the finalized
hypothesis server-side. Editing does not mutate ConceptPortfolio, MarketSeed or RefinementFinal.
The exact board is part of the canonical hash, TaskRun input, AI Pydantic input/result and persisted
result, and it drives respondent prompts and targeting text.

### Deep engine and presentation adapter

Preserved: respondent bounded retry, valid-row preservation, codebook repair, batch repair,
single-row coding fallback, honest `UNCLASSIFIED`, usable/coded/failure counts, actual-answer quotes,
semantic integrity and full lineage.

The deterministic view adapter maps `usableInterviewCount` to answered, `title` to label,
`participantIds` to respondent IDs, alternatives from coding trace, all transcripts by participant
join, representative cards by comprehension quota, and targeting/sampling/caveats from typed result.
`targetRequested` is now an engine-emitted typed count, not a frontend guess. No percentage or
missing barrier `resolvedCount` is invented.

## File classification

### RUNTIME_ACTIVE

- The four donor pages and their donor renderers/styles listed above.
- `AppRouter.jsx`, project route/journey models and shared project shell/UI.
- FULL Market/BM controller/service/worker/product pipeline/materialization.
- FULL v3 refinement service/commands plus V8 read-only presentation service/controller endpoint.
- Stage4 controller/service/run/input factory/source resolver/worker/contract, AI deep engine and
  MAIN presentation adapter.

### RUNTIME_DEPENDENCY

- shared API client, auth/current-user handling, TaskRun service/repository, canonical hasher,
  InternalAiExecutionClient, Jackson/Pydantic contracts, selection/seed/BM repositories, and shared
  ProjectWorkspace/ProjectStageHeader/Button/Card/Alert.

### LOADED_DORMANT

- `TaskType.BUSINESS_VALIDATION`, `BusinessValidationWorker`, FastAPI registration and
  `app.validation.execute_business_validation`: loaded/registered but no active browser controller
  produces this TaskRun. They are not Stage 2 execution authority.

### COMPATIBILITY_ONLY

- `/business-validation -> /market`.
- source-compatible MarketInterview service overloads used by focused tests/internal callers.
- compatibility exports retained in `marketResult.js` for other existing consumers.

### LEGACY_ORPHAN

- FULL replacement refinement and market-interview page/component trees that are no longer imported
  by the canonical routes.
- MAIN v2 refinement backend commands and entities: donor evidence only, not restored.
- MAIN `app.interview` backend core: compared but not restored; FULL deep engine remains active.

### TEST_FIXTURE_DOC_ONLY

- donor component tests, exact-copy/import golden tests, focused Java/Python tests, and this audit.

## Focused evidence

- Frontend donor/import/copy/result tests: 148 passed, 7 skipped.
- AI market-interview tests: 52 passed.
- Backend Stage 2/4 focused classes: 82 passed, 0 failed.
- `git diff --check`: no whitespace errors.
- Browser verification remains user-run; no claim of pixel identity is made before that check.
