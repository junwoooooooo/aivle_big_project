# V28 Final Report Authority Alignment — Result

## IMPLEMENTED

- Final Report source resolution now starts from `CurrentConceptSourceResolver` and pins Market Seed, Selection, Selection revision, and BM revision.
- Market/BM payloads are loaded only by the exact IDs stored on a matching `COMPLETED` Business Validation session; latest-by-project selection was removed from Final Report.
- Market Interview and Twin Panel Survey are independent optional sources and sections. Their report language identifies AI virtual-customer qualitative exploration and AI virtual-panel quantitative simulation truthfully.
- Marketing, Launch Readiness Technology/Operations, and Finance are included only when their current-concept lineage is exact and non-stale. Optional absence does not block generation and is represented as not run.
- Source manifest schema v2 records the current-concept binding, exact source identities, hashes, timestamps, and omitted optional sources. Report snapshots remain immutable.
- `V40__final_report_authority_alignment.sql` adds nullable exact lineage, manifest version, source-binding hash, and idempotent command identity fields. Legacy rows are not backfilled and therefore cannot be CURRENT.
- `POST /generate` now requires `Idempotency-Key`. Same key/same identity replays; same key/different identity conflicts; a new key can create a new immutable version.
- The source set is resolved again immediately before save; drift prevents a CURRENT snapshot from being stored.
- Frontend journey step is 8, Market Interview/Twin labels are separated, synthetic caveat and optional-missing messages are visible, raw IDs/hashes/revisions are not rendered, and ambiguous POST failure performs one GET without POST replay.
- Final Report remains deterministic composition only: no TaskRun, JobEvent, AI provider, or upstream product mutation was added.

## TESTS AUTHORED

- Backend composer tests: manifest v2 binding, deterministic source changes, separate Market Interview/Twin sections, truthful caveat, missing-source language.
- Backend snapshot tests: exact lineage requirement, legacy snapshot historical behavior, immutable new version with a new command key.
- Backend service tests: exact Business Validation version lookup, latest-by-project rejection, optional-source non-blocking generation, idempotency replay/conflict, missing-current-concept NOT_READY.
- Frontend tests: step 8, CURRENT/NOT_READY/STALE presentation, optional missing sections, separated labels, synthetic caveat, hidden raw provenance, and ambiguous mutation GET recovery with one POST.

## TEST EXECUTION

DEFERRED — FINAL INTEGRATION GATE.

No Gradle test, pytest, Vitest, production build, Docker, browser, or provider command was run.

## STATIC CHECKS

- `backend\\gradlew.bat compileJava`: one pre-compile wrapper download attempt was sandbox-denied; the approved retry completed successfully (`BUILD SUCCESSFUL`).
- `npm exec eslint -- src/features/final-report/FinalReportPage.jsx src/features/final-report/FinalReportPage.test.jsx src/features/final-report/finalReportApi.js`: PASS (one execution).
- `git diff --check`: PASS before final handoff; repeated only as the mandated final Git check after documentation completion.

## FILES CHANGED

- Final Report backend API/service/composer/domain/repository and exact-source repository selectors.
- `backend/src/main/resources/db/migration/V40__final_report_authority_alignment.sql`.
- Final Report backend tests and existing composer/launch-readiness test expectations.
- Final Report frontend page/API/tests.
- This result and the matching user-verification document.

## REMAINING RISKS

- Deferred tests, migration-chain execution, production build, runtime integration, PDF print layout, and browser visual verification remain for the Final Integration Gate.
- Legacy snapshots deliberately remain historical because exact lineage cannot be reconstructed safely.

## CONTINUATION

READY FOR FINAL INTEGRATION GATE after final static checks remain clean.
