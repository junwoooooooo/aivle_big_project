# R3C Result — Five-Slot Workboard, Timeline and Public Reveal UI

## Outcome

R3C replaces the `/concepts` placeholder with a responsive five-Slot Concept Factory Workboard. It restores the active run after navigation/refresh, follows Job Events through SSE with replay and polling fallback, shows only safe Slot progress before completion, and reveals all five detailed concepts together only after a strict client-side contract check.

## Files changed

- `frontEnd/src/features/concept-factory/**`
- `frontEnd/src/features/job-center/**`
- `frontEnd/src/app/routing/AppRouter.jsx`
- `frontEnd/src/app/layouts/AppShell.jsx`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/api/ConceptFactoryApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/repository/ConceptAttemptRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/repository/ConceptLegalEvidenceLinkRepository.java`
- `docs/rebuild/progress/R3C_RESULT.md`
- `docs/rebuild/verification/R3C_USER_VERIFICATION.md`
- `docs/rebuild/progress/R3_RESULT.md`
- `docs/rebuild/verification/R3_USER_VERIFICATION.md`

## Contracts implemented

- `/app/projects/:projectId/concepts` now renders the new Workboard.
- Summary shows eligible count, inspected candidates, redesigns, replacements, and safe rejection count.
- Five accessible Slot cards use 3+2 desktop, two-column tablet, and one-column mobile layouts.
- Slot detail is limited to number, variation focus, state, current Attempt phase, inspected count, update time, and safe copy. Eligible Slots still reveal no concept draft until the global gate opens.
- Timeline supports all/Slot filters, sequence ordering, deduplication, replay, SSE connection status, reconnect, and existing bounded polling fallback.
- Terminal states use `role=alert`; live regions, accessible Slot labels, accordion semantics, 44px controls, and reduced-motion CSS are included.
- Retry and Needs Input actions are provided; current run, Slots, concepts, active job, and event replay are restored by query on return or refresh.
- Global Job Center records Concept Factory runs and links back to their project Workboard.
- Reveal requires: completed Run, five eligible Slots, five public concepts, identical snapshot hash, no stale concept, two public legal statuses only, and unique canonical plus major-field hashes. Any failure hides every concept detail.
- Public API responses were additively extended with current Attempt phase/update time and completed-only candidate/legal/Evidence data required for the reveal UI.

## Checks actually run

- First frontend test command through `npm.ps1` was blocked by the local PowerShell execution policy; no test code ran.
- Retry through `npm.cmd`: two targeted files, four tests passed in 2.99 seconds.
- Targeted ESLint for the new features plus modified router/shell passed with no output.
- Backend `compileJava` passed in 4 seconds.
- `git diff --check` was run after final document edits; no errors reported.

## Checks intentionally omitted

Full frontend lint, production build, baseline, full frontend/backend/AI suites, PostgreSQL/Testcontainers, Docker rebuild, Provider smoke, and browser/manual/accessibility verification were intentionally omitted.

## Remaining risks

- Browser rendering at 390/768/1280 widths, screen-reader flow, 200% zoom, and real SSE reconnection remain manual gates.
- The global Job Center uses safe local persisted registration because no global cross-project job-list API exists yet; server query remains the Workboard source of truth.
- Completed legal detail depends on the additive R3C API fields and must be verified against PostgreSQL-backed R3B data.

## Exact continuation point

Run `docs/rebuild/verification/R3_USER_VERIFICATION.md`. Do not begin R4 until all integrated R3 gates pass, especially Provider evidence integrity, PostgreSQL V8/V9, failure isolation, refresh/SSE replay, and simultaneous five-concept reveal.
