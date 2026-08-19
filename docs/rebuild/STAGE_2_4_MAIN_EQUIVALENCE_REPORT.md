# Stage 2 / Stage 4 MAIN Equivalence Report

## Refs and scope

- MAIN: `aab1db2d0924bddbd307893c604426a3b0f7bf44`
- FULL start: `8f3fc7a25e56d1b0d04e018dd89240a1f174fff5`
- No merge/cherry-pick/reset/clean/stash/commit/push.
- No provider/paid call, Docker rebuild, browser E2E or full regression.

## V8 correction

V8 Stage 4 backend/AI was not MAIN-equivalent: it kept
`app.tasks.market_interview.deep_engine` as production authority. V8 Stage 2 evidence recovery
was not MAIN-equivalent: it kept bounded `section_recall` and pre-result
`semantic_relevance`. Both production substitutions are removed in V9.

## Stage 2 result

- [x] Active Java input factory is the MAIN blob.
- [x] `LLM_BUDGET_FULL=500`.
- [x] donor `_계열` remains C and no MarketStrategySelector is injected.
- [x] active worker is the MAIN blob.
- [x] worker budget/lease are 60m/63m.
- [x] market HTTP read boundary is 63m.
- [x] the entire `ai/app/research` donor tree (262 files) matches MAIN blobs.
- [x] `read_sections(pdf_refetch=True)`, `REASK.build`, `REASK.merge`,
  publish/promote/judgment/prescription/synthesis/report are the MAIN implementations.
- [x] FULL `section_recall.py`, its rule and `semantic_relevance.py` are absent.
- [x] BM input factory sends the entire exact `marketResultJson`, not a selected projection.
- [x] MAIN MarketResearchContract is active.
- [x] FULL success queues BM with `auto-bm-{fullTaskRunId}`.
- [x] BM success calls refinement bootstrap after adoption.
- [x] both downstream scheduling paths catch runtime failures, preserving adopted results.

The only non-MAIN Java files in this chain are documented outer wrappers: v3 route, TaskRun/session
projection helpers and the FULL refinement command bridge. They do not alter the MAIN AI input after
the donor input factory or the result before donor contract validation.

## Stage 4 result

- [x] production dispatch imports `app.interview.execute_market_interview`.
- [x] the 10-file `app/interview` tree matches MAIN blobs.
- [x] the imported `app.twin.bank/profile/runner/task_type/caveats` match MAIN blobs.
- [x] the called `app.providers` structured-output transport matches MAIN blobs.
- [x] Java MarketInterviewInputFactory, Worker and Contract match MAIN blobs.
- [x] TaskRun input after the source facade is only `conceptBoard` and `sampleSize`.
- [x] finalized source lineage is stored outside the MAIN result.
- [x] the FULL MarketStrategySelector targeting envelope is gone from the core input.
- [x] targeting, 8:2 split, stratified draw, six-cell stimulus, nine questions, coding,
  verification, quote selection, analysis and saturation run MAIN code.
- [x] worker budget/lease are 10m/13m.
- [x] the former FULL worker was removed; only MAIN worker claims MARKET_INTERVIEW.
- [x] old deep-engine files have no production dispatch/worker reachability.
- [x] MAIN result contract is canonical and accepts `themes=[]`.
- [x] 40 usable responses plus zero traceable themes succeeds and retains 40 transcripts.
- [x] frontend produces “분류된 답이 없어요” from an empty theme set.

## Golden/equivalence evidence

The production core is not a rewritten comparison implementation: FULL executes the same donor
blobs. `test_main_frozen_core_equivalence.py` compares every synchronized Stage 2 research blob,
all Stage 4 interview blobs and the Java/twin donor blobs to the fetched MAIN tree. It also checks
the actual dispatch branch and rejects the replaced authorities.

Recorded/provider-stub tests cover:

| case | evidence |
|---|---|
| Stage 2 FULL/BM envelope, evidence grades, mappings, contract | MAIN `test_market_research.py`, MAIN Java product/contract tests |
| Section read/reask production reachability | exact MAIN pipeline hash plus explicit READ/REASK import/merge assertions |
| 20 respondents | MAIN provider-stub orchestration test |
| 40 respondents | MAIN provider-stub orchestration test |
| target zero before spend | MAIN `test_zero_target_stops_before_a_single_response_is_bought` |
| missing/invented/unknown coding labels and IDs | MAIN coding verify tests |
| duplicate respondent | MAIN coding verify test |
| punctuation/label normalization | exact MAIN `_match_key` implementation and coding tests |
| empty themes | MAIN coding + 40-person orchestration + MAIN Java contract |
| alternative max one/person | MAIN coding test |
| resolved count | MAIN actual-answer resolved-count test |
| malformed/lost respondents | MAIN usable-minimum and answered-count tests |
| deterministic result | MAIN same-input reproducibility test |
| canonical result fixture | MAIN Python golden plus MAIN Java contract reading the same fixture |

Lineage metadata is intentionally excluded from result equivalence because it is stored in FULL
entities rather than inserted into MAIN JSON.

## Focused results

- AI: **151 passed, 1 skipped** after adding the 20/40 and zero-theme production-core regressions.
- Backend: **155 passed, 0 failed**.
- Frontend: **142 passed, 7 skipped** after adding the empty-theme presentation regression.
- Frozen manifest: **287 BYTE_IDENTICAL** files and **10 WRAPPER_ONLY** files.
- `git diff --check`: passed.

## Remaining user verification

Browser E2E was prohibited, so no pixel/runtime browser equivalence or “Production Ready” claim is
made. The next validation is the user's golden-project browser run against the already verified
core and contracts.
