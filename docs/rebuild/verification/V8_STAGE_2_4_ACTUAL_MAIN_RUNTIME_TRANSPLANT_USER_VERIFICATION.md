# V8 Stage 2/4 User Runtime Verification

Use one golden project. Do not run a broad regression or paid/provider smoke for this checklist.

## Preconditions

- The project has a current Stage 1 selection and prepared BM plan.
- Use the normal local runtime already configured for the project.
- Confirm browser routes are under `/app/projects/<id>/...`.

## Stage 2 presentation

1. Open `/market`.
2. Confirm step 1, `사업 검증`, `시장 상황과 경쟁 환경을 확인하세요`, `이 값으로 조사해요`,
   seven hypothesis rows, and `시장조사 실행`.
3. Confirm result is one compact expandable-card list in the documented nine-section order and the
   bottom action is `다음 — BM 분석`.
4. Open `/business-model` and confirm step 2, the exact MAIN heading, nine-cell BMC, one open detail
   at a time, strengths/weaknesses/risks, `시장조사로`, and `다음 — 컨셉 다듬기`.
5. Confirm normal success UI does not show PlanPhase, operational edit, financial handoff or duplicate
   verdict cards.
6. Open `/concept-refinement` and confirm step 3, exact MAIN copy, `다듬어진 컨셉`, change checklist,
   `고르면 이렇게 돼요`, bottom actions and legal disclaimer.
7. Click an available evidence link and confirm it opens `/market#sec-...` at an existing section.
8. Open `/business-validation` and confirm it redirects to `/market` only.

## Stage 2 automatic chain

1. Start market analysis once.
2. Wait for Market FULL completion.
3. Confirm BM is scheduled automatically exactly once without another user start action.
4. Wait for BM completion.
5. Confirm refinement round one becomes ready automatically exactly once.
6. If a downstream scheduling failure is induced in a safe local fixture, confirm the completed Market
   or BM result remains successful and the manual recovery action remains available.

## Refinement v3 lineage/actions

1. Select only some proposals and apply them.
2. Confirm selected changes become accepted, unselected changes remain recorded, and current round
   history is preserved.
3. Use `전부 넘기기` where another round is available; confirm `/next` produces the next v3 round.
4. Finalize and confirm the resulting final references the exact current session, seed, selection
   revision and BM plan revision.
5. Confirm no browser request uses `/api/v2/.../concept-refinement`.

## Stage 4 finalized-source gate

1. With no finalized refinement, open `/market-interview`.
2. Confirm the board is rejected with a clear not-ready/stale message; no placeholder board appears.
3. Finalize refinement, reopen Stage 4, and confirm the six board fields derive from that final.
4. Edit the five wording fields and confirm price remains locked.
5. Verify the finalized concept, seed and portfolio selection records are unchanged after editing.

## Stage 4 20-person run

1. Select 20 and start.
2. Confirm the internal steps are only `보여줄 것 확인` and `인터뷰 실행`.
3. Confirm the actual edited board is what the run input and respondent prompt contain.
4. Use the focused 20-usable/19-coded fixture or local stub; confirm the run succeeds and one coding
   row is shown as unclassified/degraded instead of failing the entire task.
5. Confirm result order starts with `이 조사가 센 것`, then target/sample context, barriers,
   current alternatives, suggestions, likes and usage scene.
6. Confirm comprehension/difference/concern, representative respondents, all responses and execution
   record are collapsed by default.
7. Confirm `다시 조사하기` contains the input controls after a result exists.
8. Confirm no percentages and no invented resolved micro-stat appear.

## Stage 4 40-person run

After the 20-person check succeeds, select the default 40 and repeat. Confirm the page remains
collapsed/usable and does not expand all 40 responses by default.

## Stale checks

Individually change BM plan revision, current selection revision, or finalized seed/final in a safe
fixture. Each must make the prior interview stale and prevent retry against the old source.

## Record

Record project id, run ids, final id, seed id, selection revision, BM revision, screenshots of the
four canonical pages, and any console/API errors. Browser verification is required before claiming
visual identity or production readiness.
