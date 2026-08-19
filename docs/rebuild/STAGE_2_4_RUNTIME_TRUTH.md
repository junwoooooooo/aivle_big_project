# Stage 2 / Stage 4 Runtime Truth Audit

## Audit identity and boundary

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START SHA: `8a72e15c7b096a474b298b55d6d09efe6400921d`
- Remote refs were fetched explicitly on 2026-08-19. Both SHAs above remained current.
- Scope: Stage 2 Business Validation and Stage 4 Market Interview only.
- Runtime truth rule: only browser-reachable code and its controller/service/TaskRun/worker/AI/materialization return path are authoritative.

## Stage 2 MAIN: browser -> AI -> browser

### Frontend reachability

`frontEnd/src/main.jsx` -> `app/App.jsx` -> `app/routing/AppRouter.jsx` -> project routes:

| Route | Page | Browser API |
| --- | --- | --- |
| `/app/projects/:projectId/market` | `features/market/MarketResearchPage.jsx` | `POST /api/v3/projects/{id}/market-research`, `GET .../market-research/current` |
| `/app/projects/:projectId/business-model` | `features/market/BmCanvasPage.jsx` | `POST /api/v3/projects/{id}/business-model`, `GET .../business-model/current` |
| `/app/projects/:projectId/concept-refinement` | `features/market/ConceptRefinementPage.jsx` | MAIN v2 refinement API (information architecture only; not a contract to restore) |

`projectJourneyModel.js` defines `2. 사업 검증`; its children come from
`projectModuleModel.js` in this order: `1. 시장 분석`, `2. 사업 모델`, `3. 컨셉 다듬기`.
`ProjectLayout.jsx` renders them through `JourneySubsteps`.

### Market FULL round trip

`MarketResearchPage` -> `marketApi.startMarketResearch` ->
`POST /api/v3/projects/{id}/market-research` -> `MarketResearchController.startFull` ->
`MarketResearchService.startFull` -> `MarketResearchInputFactory.full` ->
`TaskRun(TaskType.MARKET_RESEARCH, subjectType=MARKET_RESEARCH_FULL)` ->
`MarketResearchWorker.claimNext(MARKET_RESEARCH)` -> `InternalAiExecutionClient` ->
`POST /internal/v1/ai/executions` -> `executions.py` `MARKET_RESEARCH` branch ->
`app.research.product_pipeline.run_market_research` -> mode `FULL` ->
`MarketResearchContract.validate` -> `MarketResearchService.complete` ->
`MarketResearchVersion(kind=FULL)` -> `GET .../market-research/current` ->
`MarketResearchPage`/`MarketResultBody`.

### Business Model round trip

`BmCanvasPage` -> `marketApi.startBusinessModel` ->
`POST /api/v3/projects/{id}/business-model` -> `MarketResearchController.startBm` ->
`MarketResearchService.startBm` (binds exact current FULL `MarketResearchVersion`, seed,
selection revision, and BM plan revision) ->
`TaskRun(TaskType.MARKET_RESEARCH, subjectType=MARKET_RESEARCH_BM)` -> same worker/client/
AI `MARKET_RESEARCH` branch -> `product_pipeline` mode `BM` -> contract validation ->
`MarketResearchVersion(kind=BM)` -> `GET .../business-model/current` -> `BmCanvasPage`.

### MAIN automatic chain and failure preservation

The production execution authority is `MarketResearchWorker`:

1. A successfully materialized `MARKET_RESEARCH_FULL` schedules BM with idempotency and
   correlation key `auto-bm-{FULL taskRunId}`.
2. Only `MARKET_RESEARCH_BM` schedules refinement round one.
3. Both scheduling calls happen after `completion.complete`.
4. Both scheduling calls catch `RuntimeException`, log it, and do not rethrow. Therefore a
   completed Market FULL/BM result is not changed to FAILED if downstream scheduling fails.
5. The subject constants are the branch guard; FULL cannot start refinement and BM cannot start BM.

### Dormant BUSINESS_VALIDATION path in MAIN

`TaskType.BUSINESS_VALIDATION`, the Spring-loaded `BusinessValidationWorker`, the registered
FastAPI task type, and `app.validation.execute_business_validation`/`validation.runner` exist.
There is no browser-reachable MAIN controller/service producer that creates a
`TaskRun(BUSINESS_VALIDATION)`. The MAIN controller comments explicitly state that the removed
producer depended on deleted input authorities and must not be inferred back into production.

Classification: **LOADED_DORMANT**, not a MAIN product execution authority and not a transplant source.

## Stage 2 FULL: browser -> AI -> browser before restoration

`AppRouter` maps `/business-validation` to `features/business-validation/pages/BusinessValidationPage.jsx`
and redirects `/market`, `/business-model`, and `/concept-refinement` to it. Its API calls
`POST /api/v3/projects/{id}/business-validation/start` and `GET .../current`.

The backend path is:

`BusinessValidationController` -> `BusinessValidationCoordinator.start` ->
`MarketResearchService.startFull` -> `MARKET_RESEARCH_FULL` -> `MarketResearchWorker` ->
the same Internal AI boundary and full `product_pipeline` -> FULL version ->
`BusinessValidationReconciler` -> `Coordinator.startBmFromVersionAtPlanRevision` ->
`MARKET_RESEARCH_BM` -> same worker/AI -> BM version -> Reconciler publishes
`BusinessValidationCompletedEvent` -> `BusinessValidationRefinementStarter` -> full v3 refinement.

The session binds `marketSeedSnapshotId`, selection id/revision, BM plan revision, Market/BM task
and version ids, command keys, and canonical input hash. It detects current seed/selection/plan
changes and marks the session stale. BM retry is explicit and limited to the pinned Market version
and plan revision.

### Stage 2 authority comparison and decision

| Concern | MAIN worker chain | FULL coordinator chain | Chosen runtime |
| --- | --- | --- | --- |
| Execution authority | Worker subject branches | Reconciler/Event listener | **Worker only** |
| BM trigger | after committed FULL; `auto-bm-{taskRunId}` | reconciler polling | Worker only |
| Refinement trigger | after committed BM; exceptions swallowed | completion event listener | Worker only |
| Exact source binding | exact FULL version in BM run | session pins Market/BM versions | both retained: worker executes, session projects |
| Plan binding | execution-time plan revision | start-time pinned plan revision | session-pinned exact revision |
| Stale detection | current endpoint lineage checks | session seed/selection/plan guard | session projection retained |
| Retry | TaskRun retry + manual BM route | session BM retry | manual session retry remains recovery only |
| Failure propagation | downstream scheduling cannot undo result | after-commit listener isolation | preserve worker isolation |

Chosen authority: restore MAIN's `MarketResearchWorker` as the only automatic execution authority.
`BusinessValidationSession` remains an observer/projection and exact-lineage guard. The reconciler
may repair projection state, but must never auto-create BM or refinement work. The completion event
listener is removed from runtime authority.

### Stage 2 workload and timeout audit

MAIN current uses LLM 500, worker 60m, lease 63m, HTTP read 63m because its section chain was sized
for roughly 94 document reads plus up to four reasks per document (about 470 calls) and reasoning
model extraction.

FULL is not the same workload with old numbers. Its active child runner explicitly caps:

- harness: 3 calls
- collection: 80 calls
- bounded section recall: at most 10 attempts, at most 8 documents and 2 reasks, 4 workers,
  120-second section wall cap
- summary reserve: 3 calls

The total is exactly 96. It still performs harness, dry run, collection, verdict, cards, summary,
subprocess isolation, and adds bounded section recall/promotion; BM consumes stored promoted evidence
without re-reading it. Therefore the FULL 96 / worker 20m / lease 22m / HTTP read 22m values have a
current workload basis and are retained. Copying MAIN 500/60m/63m would discard this explicit bound.

### Existing FULL Stage 2 frontend reuse audit

The reusable files were compared at `origin/main` and the FULL start SHA; filename equality was not
used as authority.

| File | Diff finding | Restoration choice |
| --- | --- | --- |
| `MarketResearchPage.jsx` | Same v3 start/current runtime, but FULL has current tokens, stale/error treatment, competitor seed UI and integrated result renderer | retain FULL; route it directly |
| `BmCanvasPage.jsx` | Same v3 BM runtime, but FULL adds persisted BM-plan preparation, current canvas/detail renderer and stale handling | retain FULL; only point the completed next action to refinement |
| `MarketResultBody.jsx` | standalone MAIN file is absent in FULL; its role is already implemented/exported inside FULL `MarketResearchPage.jsx` | do not copy duplicate file |
| `BmCanvas.jsx` | common canvas lineage with FULL display changes | retain FULL unchanged |
| `marketApi.js` | FULL deliberately retains only active v3 Market/BM calls; MAIN's v2 refinement calls were removed | retain FULL and use the existing v3 `businessValidationApi` refinement adapter |

Therefore none of these donor files was copied wholesale and MAIN legacy CSS was not restored.

## Stage 4 MAIN: browser -> AI -> browser

`AppRouter` -> `/market-interview` ->
`frontEnd/src/features/market-interview/MarketInterviewPage.jsx` ->
`GET /api/v2/projects/{id}/market-interview/board` -> deterministic board extraction, then
`POST /api/v2/projects/{id}/market-interview` and `GET .../current` ->
`pipeline.market.MarketInterviewController` -> `MarketInterviewService` ->
`MarketInterviewInputFactory(conceptBoard, sampleSize)` ->
`TaskRun(TaskType.MARKET_INTERVIEW, subject=MARKET_INTERVIEW)` ->
`pipeline.market.MarketInterviewWorker` -> `InternalAiExecutionClient` ->
`POST /internal/v1/ai/executions` -> `executions.py` `MARKET_INTERVIEW` branch ->
`app.interview.execute_market_interview` -> targeting -> deterministic 8:2 sampling ->
respondent runner -> two-pass codebook/assignment -> deterministic quotes/counts/analysis/saturation ->
`MarketInterviewContract` -> TaskResult -> service synchronization -> `MarketInterviewVersion` ->
current endpoint -> page renderer.

### MAIN engine failure policy

| Condition | MAIN behavior |
| --- | --- |
| participant response invalid/provider cell failure | participant dropped; other rows preserved |
| missing respondent | absent from usable answers; minimum checked later |
| coding unknown respondent id | row ignored |
| duplicate respondent coding row | later duplicate ignored |
| unknown theme/alternative | label dropped, count logged; no fabricated classification |
| respondent without accepted assignment | counted as `unclassified` |
| usable below 8 or below half requested | whole task fails (`MARKET_INTERVIEW_NO_USABLE_RESPONSE`) |
| target conditions resolve to zero target rows | whole task fails before respondent spend |
| codebook provider/contract failure | whole task fails; missing axes receive one repair attempt |
| assignment provider batch failure | whole task fails in MAIN |

## Stage 4 FULL: browser -> AI -> browser

`AppRouter` -> `/market-interview` ->
`features/market-interview/pages/MarketInterviewPage.jsx` ->
`GET/POST /api/v3/projects/{id}/market-interview[/current|/retry]` ->
`pipeline.marketinterview.MarketInterviewController` -> `MarketInterviewService` ->
`CurrentConceptSourceResolver` + `MarketInterviewInputFactory` v2 -> immutable seed/selection/BM-plan
lineage -> `TaskRun(MARKET_INTERVIEW)` -> `pipeline.marketinterview.MarketInterviewWorker` ->
Internal AI endpoint -> `executions.py` -> `app.tasks.market_interview.execute_market_interview` ->
`deep_engine` -> full v2 contract validation -> run materialization -> current endpoint -> full page/result.

### Stage 4 exact comparison

| Dimension | MAIN | FULL |
| --- | --- | --- |
| INPUT | editable deterministic six-field `conceptBoard`, sample size | immutable selected concept, validated hypotheses, BM, targeting context, source lineage, sample size |
| TARGETING | free text -> observable criteria | same intent plus customer-unit/organization proxy semantics and taxonomy normalization |
| SAMPLING | deterministic target/non-target 8:2 | deterministic target/comparison/proxy/exploratory with source group preserved |
| INTERVIEW GENERATION | one call/person, concurrency 32, transport retry | one call/person, concurrency 4 default, two respondent attempts |
| CODING | codebook + batches; unknown values dropped | codebook repair, batch repair, single-row fallback, honest `UNCLASSIFIED` |
| QUOTE | deterministic answer-field extraction | deterministic exact answer excerpt per coded theme |
| AGGREGATION | respondentIds, counts, crossings, saturation | same traceability plus group counts and coding trace |
| FAILURE POLICY | participant drop; assignment batch can fail whole task | participant drop; individual coding failure degrades; codebook/semantic/minimum remain hard |
| RESULT | MAIN envelope and MarketInterviewVersion | lineage-rich `market-interview-result-v2` in MarketInterviewRun |
| WORKER BUDGET | 10m / lease 13m | 5m / lease 7m before restoration |
| RETRY | worker recovery; new browser run | worker recovery + lineage-checked explicit retry, max 3 attempts |
| UI | before/during/after, board editor, traceability | two-step token UI, progress events, theme -> respondent -> raw answer explorer |

### Stage 4 engine decision

The MAIN core cannot be reused as a small adapter. The FULL branch deleted the ten-file
`app.interview` package, changed provider transport and result contracts, and requires group/source
lineage, classification status, exact evidence objects, semantic-integrity checks, and retry
diagnostics. Reintroducing MAIN would require roughly 1,800 lines plus a meaning-bearing result
adapter and would regress FULL's already-implemented individual coding degradation.

Decision: retain `deep_engine`. Its current policy already matches the requested MAIN stability
principle more closely than MAIN for coding failures:

- invalid respondent output is dropped after bounded retry; valid rows remain;
- valid transcript + unrecoverable coding becomes `UNCLASSIFIED`, not whole-task failure;
- unknown labels are excluded; quotes are taken from actual answer fields;
- whole-task failure is limited to target unavailable/insufficient, usable minimum or group-coverage
  collapse, codebook collapse, provider-wide inability, no traceable theme, schema/semantic integrity.

The v2 input can deterministically produce a MAIN-shaped concept board, but doing so would throw away
validated-hypothesis, customer-unit and lineage semantics and would not remove the result-adapter work.
No fake board values should be generated, so that adapter is not selected.

### Stage 4 result-contract comparison

| Result concern | MAIN `app.interview` | FULL `deep_engine` | Adapter consequence |
| --- | --- | --- | --- |
| themes/respondent IDs | `themes[].respondentIds`, count and one deterministic quote | `themes[].participantIds`, mention/target/non-target counts and exact evidence quote | mechanical renaming is possible, but group/evidence invariants must remain FULL-native |
| transcripts | `transcripts` plus bucketed interview cards | `participants`, complete `interviews`, `transcriptProvenance` | FULL preserves every usable respondent and immutable source group |
| comprehension | counts plus representative cards | exact four-bucket counts including `unclassified`; per-row codingTrace | representative display can be derived without new AI meaning |
| differentiation | verdict counts and representative cards | exact four-bucket counts including `unclassified`; per-row codingTrace | same |
| sampling/targeting | requested/drawn/target/non-target diagnostics | requested/drawn/attempted/usable/failed plus target/proxy/exploratory and representation status | FULL lineage and proxy semantics cannot be discarded |
| open questions | codebook follow-up questions | `followUpQuestions` | direct display mapping |
| caveats | structured `caveats` plus notes | explicit boundaries carried into `limitations` plus synthetic/representation warnings | wording must come from stored result, not a new AI call |
| saturation | telemetry homogeneity map | typed saturation with coded/usable/failure counts and axis diagnostics | FULL is a strict superset |
| coding counts | answered/LLM telemetry; missing assignment becomes unclassified | usable/coded/codingFailure at top level and saturation, one trace row per usable respondent | FULL truthful degradation contract must remain authoritative |
| lineage | concept board only | exact seed hash/id, selection id/revision and BM plan revision | required by persisted FULL current/stale contract |

Only presentation derivations (labels, ordering and collapsed sections) are added. No result adapter asks
AI to invent missing semantics.

### Stage 4 timeout decision

FULL is heavier than MAIN in retry/fallback work and has much lower default respondent concurrency.
There is no workload basis for 5m. Restore worker budget 10m and lease 13m. Route
`MARKET_INTERVIEW` through the existing 14m twin-survey transport client (as MAIN does), rather than
the 7m generic long-running client. Provider calls remain independently bounded by the shared 60s
provider timeout.

## File classification

### RUNTIME_ACTIVE

- Frontend entry/routing/journey: `frontEnd/src/main.jsx`, `app/App.jsx`,
  `app/routing/AppRouter.jsx`, `projectRoutes.js`, `app/project-shell/ProjectLayout.jsx`,
  `app/module-status/projectJourneyModel.js`, `projectModuleModel.js`.
- Stage 2 UI/API: `features/market/MarketResearchPage.jsx`, `BmCanvasPage.jsx`,
  `MarketReportView.jsx`, `MarketResultBody` export, `BmCanvas.jsx`, `marketApi.js`, polling/result
  models; restored independent refinement page using full refinement components/API.
- Stage 2 backend: `pipeline.market.MarketResearchController`, `MarketResearchService`,
  `MarketResearchInputFactory`, `MarketResearchWorker`, run/version entities and repositories,
  `pipeline.businessvalidation` session/coordinator/reconciler as projection only, and full v3
  `pipeline.refinement` controller/service/materialization path.
- Stage 2 AI: `ai/app/api/executions.py` MARKET_RESEARCH branch,
  `ai/app/research/product_pipeline.py`, `product_runner.py`, `pipeline.py`, active research2 engine.
- Stage 4 UI/API: `features/market-interview/pages/MarketInterviewPage.jsx`, API, model, result and
  dashboard components/styles.
- Stage 4 backend: `pipeline.marketinterview` controller/service/input factory/worker/run repository,
  `CurrentConceptSourceResolver`, and `MarketInterviewContract`.
- Stage 4 AI: `ai/app/api/executions.py` MARKET_INTERVIEW branch and
  `ai/app/tasks/market_interview/{service,deep_engine,models,panel_sampling,questions,provider,semantic_integrity}.py`.

### RUNTIME_DEPENDENCY

- Shared `TaskRunService`, `TaskType`, `InternalAiExecutionClient`, AI REST clients/properties,
  canonical hashing, job events/SSE, shared UI (`ProjectWorkspace`, `ProjectStageHeader`,
  `JourneySubsteps`, `Button`, `Card`, `Alert`), profile bank/twin sampling and provider transport.
- Stage 2 source authorities: current portfolio selection, Market Analysis Seed, BM plan preparation,
  market ledger artifacts, and refinement decision/application/finalization services.

### LOADED_DORMANT

- MAIN `TaskType.BUSINESS_VALIDATION`, `pipeline.market.BusinessValidationWorker`, registered FastAPI
  `BUSINESS_VALIDATION` branch, and `ai/app/validation.execute_business_validation`: loaded/registered
  but no MAIN browser/controller TaskRun producer.

### COMPATIBILITY_ONLY

- `/app/projects/:id/business-validation` browser URL redirects to canonical `/market` after
  restoration.
- `/virtual-interview` and `/twin-survey` browser aliases continue to redirect to `/market-interview`.
- Full `BusinessValidationController` API remains a compatibility entry to the same worker-owned
  Market chain; it is not an automatic BM/refinement authority.

### LEGACY_ORPHAN

- MAIN v2 refinement browser API paths are not restored; full v3 refinement is authoritative.
- Full pre-restoration combined `BusinessValidationPage` ceases to be route-reachable.
- `BusinessValidationCompletedEvent`/`BusinessValidationRefinementStarter` cease to be runtime
  execution authority and are removed rather than left as a second scheduler.
- MAIN `app.interview` files are audit donor code only in `origin/main`; they are not imported by FULL.

### TEST_FIXTURE_DOC_ONLY

- Files under `backend/src/test`, `frontEnd/src/**/*.test.*`, `ai/tests`, research2 test/runs/data
  fixtures, and historical docs/mockups are validation evidence only and never runtime authority.

## Focused validation contract

No provider, Docker, browser E2E, full regression, or production build is permitted. Focused checks
must cover three Stage 2 routes/JourneySubsteps, direct Market/BM API contracts, worker subject
branching and exactly-once keys, one automatic authority, exact session/version/plan lineage and stale
semantics, Market 96/20m/22m workload contract, Stage 4 v2 input/contract, respondent degradation,
20 success, 19/20 usable, one coding failure, target zero/minimum failure, 10m/13m/14m timeout chain,
and collapsed respondent/result detail.
