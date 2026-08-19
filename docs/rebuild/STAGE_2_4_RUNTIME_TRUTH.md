# V9 Stage 2 / Stage 4 Runtime Truth

## Identity and rule

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START SHA: `3b60bdee05fb90e4e90bf6a7fe5e98c8d03237a3`
- Frontend presentation truth: the files reached by MAIN `AppRouter.jsx`.
- Backend/AI execution truth: **origin/main active core**.
- FULL v3 is outer integration, finalized-source gating, TaskRun transport and lineage storage only.
- No Docker rebuild, provider/paid call, browser E2E or full regression was run.

V8 Stage 4 backend/AI was **not MAIN-equivalent** because production dispatched to
`app.tasks.market_interview.deep_engine`. V8 Stage 2 evidence recovery was **not
MAIN-equivalent** because production used the bounded `section_recall` and
`semantic_relevance` replacements.

V9's 287-file closure statement was also incomplete: the audit did not cross from
`app.research` into the production-imported `app.validation` package. V9.1 follows that boundary
and freezes exact MAIN `validation/{__init__,citation,gate,mapping,runner}.py`; `drift.py` is not
reached by Stage 2.

## Stage 2 complete production graph

### Market FULL

`/market -> MarketResearchPage -> POST /api/v3/projects/{id}/market-research ->
MarketResearchController -> MarketResearchService -> MAIN MarketResearchInputFactory ->
TaskRun(MARKET_RESEARCH, MARKET_RESEARCH_FULL) -> MAIN MarketResearchWorker ->
InternalAiExecutionClient -> POST /internal/v1/ai/executions -> executions.py MARKET_RESEARCH ->
MAIN app.research.product_pipeline -> MAIN app.research.pipeline -> MAIN research2 collection ->
read_sections(pdf_refetch=True) -> reask_sections.build -> reask_sections.merge -> publish_gate ->
promote_cards -> judgment -> prescriptions -> synthesis/report/summary ->
MAIN app.validation.mapping/citation/gate ->
MAIN MarketResearchContract -> TaskResult adoption -> MarketResearchVersion(FULL) ->
GET /market-research/current -> MarketResultBody`.

The FULL input adapter does not select a market strategy. MAIN fixes donor `_계열.계열` to C,
uses MAIN hypothesis/BM-plan/legal mappings, and sends `llmBudget=500`.

### BM

`MarketResearchWorker FULL success -> MarketResearchService.startBm(
idempotency=auto-bm-{fullTaskRunId}) -> exact source MarketResearchVersion ->
MAIN MarketResearchInputFactory.bm -> marketResultJson=<entire FULL result JSON> plus exact
source run/version and pinned plan revision -> TaskRun(MARKET_RESEARCH, MARKET_RESEARCH_BM) ->
same MAIN product pipeline BM path -> app.research.bm contracts/routing/mapping/gate ->
MAIN app.validation.citation tuple enforcement -> MAIN app.validation.gate evaluation ->
MAIN MarketResearchContract -> MarketResearchVersion(BM)`.

No Stage 2 wrapper filters the FULL result or rebuilds a partial BM input.

### Refinement bootstrap and failure propagation

`MarketResearchWorker BM success -> ConceptRefinementService.startFirstRoundAfterResearch`.
The method is a boundary adapter that delegates once to the existing FULL v3 command with the
completed validation-session id in the command key. MAIN worker catches scheduling exceptions after
result adoption. Consequently BM scheduling cannot turn an adopted FULL result into FAILED, and
refinement scheduling cannot turn an adopted BM result into FAILED.

`BusinessValidationSession` and its reconciler observe exact Market/BM task/version,
selection revision and BM-plan revision. They do not claim MARKET_RESEARCH and do not create
automatic continuation work. The only automatic chain authority is MAIN
`MarketResearchWorker`.

### Time and budget

- FULL LLM call ceiling: **500**.
- Market worker budget: **60 minutes**.
- Lease: **63 minutes**.
- Internal market HTTP read timeout/default: **63 minutes**.

The former 96 / 20m / 22m values are no longer in the production Stage 2 core.

## Stage 2 refinement browser graph

`/concept-refinement -> MAIN ConceptRefinementPage/RefinementSummary ->
GET /api/v3/.../business-validation/refinement/presentation -> FULL read-only presentation
projection over exact session rounds/decisions/finalization -> MAIN UI`.

Commands remain FULL v3 decision/apply/next/retry/finalize. This is outside the frozen Market/BM
execution core and preserves proposal keys, round hashes, selection revision, BM-plan revision and
finalized lineage. Module next action remains `/concept-refinement`.

## Stage 4 complete production graph

### Input/gate

`/market-interview -> MAIN MarketInterviewPage -> GET /api/v3/.../market-interview/board ->
FULL MarketInterviewSourceResolver -> current non-stale FINALIZED ConceptRefinementFinal ->
exact final seed/selection revision/BM-plan revision -> deterministic six-cell board`.

The user may edit stimulus wording and cannot edit price. POST validates exact six fields and price,
then the FULL outer factory delegates to the byte-identical MAIN
`pipeline.market.MarketInterviewInputFactory`. The resulting TaskRun input is exactly:

```json
{"conceptBoard": {"conceptName": "...", "targetUsers": "...", "problemScenario": "...",
"featureSet": [], "differentiators": "...", "priceKrw": 0}, "sampleSize": 20}
```

Final id, seed id/hash, selection id/revision and BM-plan revision stay in
`MarketInterviewRun`; they are not inserted into the MAIN input or result envelope.

### Execution

`TaskRun(MARKET_INTERVIEW) -> byte-identical MAIN pipeline.market.MarketInterviewWorker
(10m budget, 13m lease) -> InternalAiExecutionClient -> executions.py MARKET_INTERVIEW ->
from app.interview import execute_market_interview -> exact MAIN targeting -> KISDI bank load ->
LLM TargetCriteria once -> code matching -> TARGET 8/NON-TARGET 2 stratified deterministic draw ->
fixed six-cell stimulus and nine questions -> one-turn respondents -> MAIN coding.py ->
analysis/saturation/caveats -> byte-identical MAIN MarketInterviewContract -> TaskResult adoption`.

The actually called structured-output transport (`app.providers/**`) and the imported twin
`bank/profile/runner/task_type/caveats` modules are also MAIN blobs; FULL transport behavior does
not sit inside the frozen core.

The FULL `pipeline.marketinterview` worker was removed. Therefore only one worker claims
MARKET_INTERVIEW. `app.tasks.market_interview/**` remains dormant history and is not imported by
the execution branch.

### Return/lineage

`MAIN worker adoption -> FULL MarketInterviewService.current synchronization ->
copy unchanged TaskResult JSON into the lineage run -> stale comparison against final id, seed,
selection revision and BM-plan revision -> GET /current -> MAIN marketInterviewResult view ->
MAIN result renderer`.

The copied result remains MAIN canonical:
`conceptBoard, sampleSize, sampling, targeting, comprehension, differentiation, themes,
alternatives, segments, contrast, suggestionLinks, interviews, transcripts, telemetry, caveats,
notes`.

`themes=[]` is valid. Valid transcripts remain available. There is no production
`NO_TRACEABLE_THEME` failure and no Java minimum-one-theme rule.

## V9.1 final business proposal diagnostic appendix

This diagnosis is read-only with respect to the historical run and does not change Stage 4.

- Project: 5
- TaskRun: `94ab6261-35b6-4762-8454-ac4c8689b004`
- Current attempt: `e1b81d5a-9dd0-461a-8a87-9a7b7af1b714`
- TaskRun error: `AI_RESULT_INVALID`
- Attempt error: `RESULT_SCHEMA_INVALID / PROVIDER_RESPONSE_SCHEMA_REJECTED`
- Retryable: false
- Persisted historical `validationFields` / `safeDiagnostics`: absent

The exact stored input validates against `FinalBusinessProposalInput`. Its safe shape is 12
manifest entries, 12 included source types, one omitted type, 457 catalog entries, 457 allowed
keys, and `sha256:` plus 64 hexadecimal characters. Catalog and allowed-key sets are equal.

The rejection occurred after input validation: the response schema expanded the 457 evidence keys
into four large output enum sites, producing 1,876 enum values and 50,024 enum string characters.
The production fix keeps only the `EV-<24 hex>` format constraint in the response schema and keeps
exact catalog membership in the existing fail-closed result validator. Backend-generated input is
also exercised directly through Python `FinalBusinessProposalInput.model_validate`. New worker
failures preserve safe field paths and `diagnosticStage` in job-event technical details without
exposing the input payload.

## Runtime classification

### RUNTIME_ACTIVE

- MAIN donor Stage 2/4 pages and result renderers on the four canonical routes.
- MAIN MarketResearchInputFactory, MarketResearchWorker, MarketResearchContract.
- MAIN `ai/app/research/**` production closure.
- MAIN `ai/app/validation/{__init__,citation,gate,mapping,runner}.py` reached by Stage 2.
- MAIN MarketInterviewInputFactory, MarketInterviewWorker, MarketInterviewContract.
- MAIN `ai/app/interview/**`, MAIN `app.twin.bank` and MAIN `app.twin.profile`.
- FULL v3 route/source/lineage facades before and after those cores.

### RUNTIME_DEPENDENCY

TaskRun infrastructure, InternalAiExecutionClient, auth/current user, selection/seed/BM/final
repositories, canonical hashing, job events, shared Project shell/tokens, and FULL refinement v3
commands/presentation projection.

### LOADED_DORMANT

`TaskType.BUSINESS_VALIDATION`, `BusinessValidationWorker` and
`app.validation.execute_business_validation` remain non-browser dormant. The old
`app.tasks.market_interview/**` files remain on disk but have no dispatch or worker reachability.

### COMPATIBILITY_ONLY

`/business-validation -> /market`, the v3 business-validation read/projection API, FULL service
read helpers used by session reconciliation, and frontend compatibility exports.

### LEGACY_ORPHAN / NOT_ALLOWED

FULL `section_recall`, `semantic_relevance`, deep-engine worker authority and
MarketStrategySelector-driven Stage 2/4 input decisions. The first two source files and the old
FULL market-interview worker were removed from their former production locations.

### TEST_FIXTURE_DOC_ONLY

MAIN recorded market/interview fixtures, imported donor tests, the frozen-blob equivalence test and
the documents in `docs/rebuild`.

## Focused evidence

- AI V9.1 closure/BM/final-proposal focused runs: 53 passed.
- Backend Stage 2/final-proposal focused runs: 91 passed.
- Frontend Stage 2/4 donor UI focused run: 27 passed.
- Frozen manifest: 292 BYTE_IDENTICAL and 10 WRAPPER_ONLY files.
- Stage 4 MAIN dispatch/blob freeze: passed without Stage 4 core edits.
- `git diff --check`: passed.
- Browser presentation verification remains user-run; this document does not claim visual identity
  before that verification.
