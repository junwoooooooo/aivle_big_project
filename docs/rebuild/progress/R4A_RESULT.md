# R4A Result — Five-Concept Comparison and Selection Preparation UI

## Outcome

`/app/projects/:projectId/concepts/compare` now renders the five public concepts from the completed Concept Factory run as comparison cards and an attribute comparison view. Users can choose 2–5 concepts to compare, inspect the complete legal-review basis, mark one compared concept as the preferred candidate, and save a browser-session-only preparation draft.

R4A does not create or persist a Selection Snapshot, confirm a selection, or invoke a market-analysis Handoff. Those mutations remain exclusively in R4B.

## Files changed

- `frontEnd/src/app/routing/AppRouter.jsx`
  - Replaced the compare placeholder with the new R4A page.
- `frontEnd/src/features/concept-selection/api/conceptSelectionApi.js`
  - Added read-only access to the current Concept Factory run and its public concepts.
- `frontEnd/src/features/concept-selection/hooks/useConceptSelection.js`
  - Added completed-run gating, public-concept loading, retry, and `sessionStorage` draft persistence.
- `frontEnd/src/features/concept-selection/model/conceptComparisonModel.js`
  - Added the 11-row comparison model, legal labels, deterministic server-data-derived tags, operating-difficulty basis, and local draft invariant.
- `frontEnd/src/features/concept-selection/components/ConceptCard.jsx`
  - Added all requested card fields, compare/preferred controls, and legal-detail action.
- `frontEnd/src/features/concept-selection/components/ConceptComparisonTable.jsx`
  - Added desktop comparison table and mobile two-concept groups without a composite score or automatic rank.
- `frontEnd/src/features/concept-selection/components/LegalDetailDialog.jsx`
  - Added keyboard-contained modal, focus restoration, legal disclaimer, full assessment fields, and official Evidence links.
- `frontEnd/src/features/concept-selection/pages/ConceptComparisonPage.jsx`
  - Added card/table switching, 2–5 compare selection, preferred-candidate preparation, local draft feedback, and not-ready actions.
- `frontEnd/src/features/concept-selection/styles/concept-selection.css`
  - Added responsive desktop/tablet/mobile and reduced-motion presentation.
- `frontEnd/src/features/concept-selection/index.js`
  - Exported the route page.
- `frontEnd/src/features/concept-selection/model/conceptComparisonModel.test.js`
  - Added comparison mapping/tag/draft invariant coverage.
- `frontEnd/src/features/concept-selection/components/ConceptComparisonView.test.jsx`
  - Added card controls and score-free comparison-view coverage.
- `docs/rebuild/progress/R4A_RESULT.md`
- `docs/rebuild/verification/R4A_USER_VERIFICATION.md`

## Contracts implemented

- Consumes only the R3 public concept endpoint after the current run reports `COMPLETED` and exactly five concepts are available.
- Presents concept name, summary, differentiator, target, operating/revenue model, legal state, required controls, risks, details, and preferred-candidate state.
- Presents the specified eleven comparison rows across selected concepts.
- Does not compute or show a single aggregate score, winner, or automatic first place.
- Derives tags deterministically from public server fields: variation focus, partner/physical activity facts, target text, revenue hypothesis, legal status, and required controls.
- Replaces the five-column table on mobile with groups of no more than two concept cards.
- Keeps the R4A selection preparation draft in `sessionStorage` with `SESSION_LOCAL_ONLY`; no server mutation exists in this feature.
- Keeps the route open before readiness and shows the required Concept Factory state plus a direct navigation action.

## Checks actually run

- Targeted Vitest:
  - `npm.cmd test -- --run src/features/concept-selection/model/conceptComparisonModel.test.js src/features/concept-selection/components/ConceptComparisonView.test.jsx`
  - Result: 2 files passed, 3 tests passed.
- Targeted syntax/lint inspection:
  - `npm.cmd exec eslint -- src/features/concept-selection src/app/routing/AppRouter.jsx`
  - Result: passed with no reported errors.
- `git diff --check`
  - Result: recorded after final documentation update; see final task report.

## Checks intentionally omitted

- Backend full tests, AI full tests, full `postgresTest`, Testcontainers, frontend baseline, frontend production build, Docker Compose rebuild, real Provider smoke, and browser manual testing were not run under Fast Mode.
- No database migration or backend/API mutation was added in R4A.

## Remaining risks

- Real R3 payloads with maximum-length Korean content require browser verification for wrapping and modal/table readability.
- Responsive behavior, keyboard focus containment, screen-reader flow, 200% zoom, and session restoration have not been manually browser-tested.
- Deterministic tags intentionally omit a tag when the public concept data does not contain a defensible signal; product review should confirm the displayed tag density on real concepts.
- `sessionStorage` is tab-session-local by design and is not a durable selection. R4B must never treat it as an authoritative Selection Snapshot without explicit user confirmation.

## Exact continuation point

Run `docs/rebuild/verification/R4A_USER_VERIFICATION.md`. Only after every R4A gate passes, begin a separate R4B execution at explicit selection confirmation: validate the selected public concept and source snapshot, create the immutable Selection Snapshot, then implement the market-analysis Handoff envelope and state handling. Do not add those mutations to the R4A local-draft path.
