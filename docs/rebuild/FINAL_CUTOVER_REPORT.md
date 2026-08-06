# Final Cutover Report

## Decision

Repository stabilization fixes demonstrated by R7A/R7B verification evidence are implemented, but
the final cutover decision is **PENDING USER ACCEPTANCE**.

## Implemented cutover state

- New pipeline source remains the only active product surface.
- Deleted Legacy APIs, routes, entities, AI tasks, and UI were not restored.
- Common TaskRun, JobEvent, Internal AI transport, providers, and active AI tasks remain preserved.
- Common UTC Clock ownership is independent of deleted Legacy job code.
- Migration contract tests inspect the single final V1 baseline.
- Frontend route tests use the active new-shell routes, labels, and Project Overview.
- Project API tests assert that the removed Legacy `stage` response field stays absent.
- The auth/project flow follows the active Projects route and scopes its overview assertion to the
  actual page heading.
- PostgreSQL tests now verify exactly one clean V1 migration, all 37 retained tables, and explicit
  absence of Legacy document/plan/analysis tables. Pre-baseline upgrade tests and their Legacy-only
  constraint fixture were removed.

## Evidence currently Green

- User: corrected Project API test passed; backend/test compilation and the unit suite passed before
  the PostgreSQL task exposed stale migration tests; AI suite passed 25 tests.
- User: corrected auth/project flow passed 2/2; frontend lint and production build passed; baseline
  passed with 129 tests, 6 explicitly allowed failures, and 0 unexpected failures.
- User: Compose config, backend/AI/frontend builds, startup, and all five service health checks passed.
- User and Codex: active Legacy surface searches returned no source matches.
- Codex: targeted application-context and final-baseline contract tests passed; changed-test ESLint,
  final static import/route checks, and `git diff --check` passed.
- Codex: after consolidating the PostgreSQL contracts, targeted final-baseline migration and
  container smoke tests passed together, `BUILD SUCCESSFUL` in 36 seconds.

## Evidence requiring rerun

- Full backend `postgresTest` after the baseline-test consolidation.
- Clean DB Flyway history and backend migration logs.

## Evidence not supplied

- Six-route/manual browser acceptance, screenshots, and Network responses.
- Idea Brief through marketing end-to-end evidence.
- TaskRun/JobEvent/SSE refresh recovery.
- Real Provider Smoke for active AI tasks.
- Mobile, keyboard, 200% zoom, reduced-motion, and core accessibility checks.
- External module handoff/result integration beyond container health.

## Release condition

Do not label R7 or production cutover complete until every blocking item in
`verification/FINAL_USER_ACCEPTANCE.md` is Green. Docker does not require rebuilding for the final
test-only assertion changes. Rotate all credentials exposed in shared verification output before any
deployment or further configuration sharing. No commit or push was performed.
