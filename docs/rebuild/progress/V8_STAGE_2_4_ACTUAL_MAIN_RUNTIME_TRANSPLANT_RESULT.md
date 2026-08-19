# V8 Stage 2/4 Actual MAIN Runtime Transplant Result

## Result

- MAIN HEAD: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL START SHA: `a0219e0e54768003ab491a95bd892403a27da6d5`
- Commit/push: not performed.
- Browser E2E/provider/Docker/full regression: not performed by instruction.

## A. Actual MAIN donor files

Stage 2 Market: `MarketResearchPage.jsx`, `ResearchBasisCard.jsx`, `MarketResultBody.jsx`,
`marketResult.js`, `market.css`.

Stage 2 BM: `BmCanvasPage.jsx`, `BmResultBody.jsx`, `BmCanvas.jsx`, `market.css`.

Stage 2 refinement: `ConceptRefinementPage.jsx`, `RefinementSummary.jsx`,
`useConceptRevision.js`, `conceptRevision.js`, `marketResult.js`, `market.css`.

Stage 4: `MarketInterviewPage.jsx`, `ConceptBoardEditor.jsx`, `SampleSizePicker.jsx`,
`InterviewCard.jsx`, `marketInterviewResult.js`, `useMarketInterviewPolling.js`,
`market-interview.css` and their API/sample helpers.

## B. V7 FULL replacements removed from canonical presentation

- FULL report-first MarketResearch page/renderer.
- FULL BM Plan/PreparedPlan/financial-handoff normal-path renderer.
- `features/business-validation` refinement page/panel/summary.
- nested FULL `features/market-interview/pages` and `components/MarketInterviewResult`.

They were not broadly deleted; canonical route imports now point directly to MAIN donor pages.

## C. Stage 2 transplant

- Routes remain `/market`, `/business-model`, `/concept-refinement`; compatibility URL redirects to
  `/market`.
- MAIN step numbers, headings, descriptions, actions, section order, expandable rows, 920px compact
  stage CSS, BMC interaction and refinement copy are active.
- Market normal result renders `MarketResultBody`; BM renders `BmResultBody`; refinement renders
  `RefinementSummary`.
- FULL Market/BM v3 API and worker auto-chain remain unchanged execution authority.
- FULL result fields that do not contain MAIN's historical 2/8/9 report prose are shown as missing,
  not synthesized.

## D. Refinement v3 presentation adapter

- Added read-only `GET .../business-validation/refinement/presentation`.
- Projects exact current session cycle history into MAIN change semantics.
- Preserves proposal key, round, before/after, rationale, source, evidence IDs, legal reference and
  accepted/declined/undecided state.
- MAIN actions call FULL v3 `/decision`, `/apply`, `/next`, `/retry`, `/finalize` with existing hashes.
- No v2 command authority, entity or state machine was restored.
- No unvalidated narrative or legal prose is generated.
- Module next action is `/concept-refinement`.

## E. Stage 4 finalized-source gate

- Added Stage4-only `MarketInterviewSourceResolver`.
- Board/start require current non-stale `ConceptRefinementFinal` and exact final seed, selection,
  selection revision and BM revision.
- Module status uses the same resolver and required input `conceptRefinementFinal`.
- TaskRun/domain row/current response bind `conceptRefinementFinalId`.
- Added V44 nullable legacy lineage column; old unmatched rows remain stale-readable history.

## F. Editable concept-board v3 adapter

- Added LLM-free v3 board endpoint.
- Board is exactly six fields; the five wording fields are editable stimulus only.
- Price is derived from final hypotheses and rejected server-side if tampered.
- `conceptBoard` is explicit in backend input, canonical hash, AI Pydantic input/result and output
  contract. No extra-field path is used.
- Canonical concept/seed/final are never mutated by stimulus edits.

## G. Preserved deep_engine behavior

- bounded respondent retry and valid respondent preservation;
- codebook/batch repair and single-row fallback;
- `UNCLASSIFIED` and usable/coded/coding-failure counts;
- quotes from actual answers;
- semantic integrity and source lineage;
- whole-task failure only at the existing minimum/target/codebook/provider/schema/semantic boundaries.

The actual stimulus board now drives respondent prompt and target text. The typed result includes
`targetRequested`, avoiding a frontend estimate of the 80% target quota.

## H. MAIN copy and import golden

`mainRuntimeTransplant.golden.test.js` fixes the four direct route imports, direct renderer imports,
and the requested exact Market/BM/refinement/interview copy. It also prevents the Stage 4 donor page
from reintroducing the heavy replacement header/mission surface.

## I. Focused validation

- Frontend: 148 passed, 7 skipped across route, donor copy, market result, BMC, refinement and
  interview presentation tests.
- AI: 52 passed in market-interview deep-engine focused tests.
- Backend: 82 passed across Market worker authority/runtime, v3 refinement projection/commands,
  finalized-source resolver, board/input/service, strict result contract, canonical browser input,
  module status and journey presentation.
- `git diff --check`: passed.

## Known verification boundary

No browser E2E or screenshot comparison was run. The exact files/copy/structure are transplanted and
focused contracts pass, but pixel identity and live golden-project behavior remain user verification.
