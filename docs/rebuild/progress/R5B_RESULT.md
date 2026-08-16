# R5B Result — Finalized Planning Snapshot and Business/Persona Integration Shell

## Outcome

R5B adds the new `pipeline/planning` domain, deterministic proposal application, immutable revision/finalized Snapshot persistence, the four planning APIs, and a real `/business-persona-test` external-module Shell. BM·financial and Persona modules both consume the same `finalized-planning-snapshot-v1` body and hash. No analysis algorithm was added, and external results cannot mutate planning.

The planning UI presents the selected original, market proposals, a meaning-labelled applied preview, final planning, and previous planning. One finalized Snapshot is allowed per source selection Snapshot; another market round requires an explicitly new Run/input Snapshot.

## Files changed

- New backend planning domain, API, application policy, and repositories: `backend/src/main/java/com/aivle/backend/pipeline/planning/**`
- External integration and project module status extensions under `backend/src/main/java/com/aivle/backend/pipeline/integration/**` and `pipeline/module/**`
- Flyway V12: `backend/src/main/resources/db/migration/V12__finalized_planning_and_external_shell.sql`
- Finalized Snapshot schema: `docs/rebuild/contracts/finalized-planning-snapshot-v1.schema.json`
- Planning targeted tests and updated module-status test under `backend/src/test/java/com/aivle/backend/pipeline/**`
- New frontend planning revision and business/persona integration features under `frontEnd/src/features/planning-revision/**` and `frontEnd/src/features/business-persona-integration/**`
- New project-shell route and market planning-panel integration.

## Contracts implemented

- Tables: existing immutable `planning_change_proposals`, new `planning_change_decisions`, `planning_snapshots`, and `finalized_planning_snapshots`; V12 backfills prior R5A decisions.
- APIs: `GET planning/current`, `GET planning/change-proposals`, `POST planning/change-proposals/{proposalId}/decisions`, and `POST planning/finalize`.
- Exact `ADOPT`, user-edited `PARTIALLY_ADOPT`, and `REJECT` application without AI reinterpretation.
- Finalized Snapshot fields for final concept, target, value proposition, features, channels, pricing/revenue hypothesis, operating structure, legal controls/disclosures/claims/prohibited expressions, decisions, hash, and time.
- Internal sequence/parent/hash chains with meaning-based labels rather than primary v1/v2/v3 names.
- `BUSINESS_FINANCIAL` and `PERSONA_RESPONSE_TEST` Handoffs use the identical current Finalized Planning Snapshot.
- External Run statuses remain truthful; absent adapters are `NOT_CONNECTED`, and a newer finalized Snapshot makes older runs effectively `STALE`.
- BM, financial, and Persona response UI areas explicitly state that outputs are not real market probability and cannot auto-change planning.

## Checks actually run

- Backend targeted tests passed: deterministic Patch, stable Snapshot hash, meaningful label, and updated project module status (`BUILD SUCCESSFUL`, 33 seconds).
- Backend final `compileJava` passed (`BUILD SUCCESSFUL`, 3 seconds). A deprecation note from the current Jackson API usage remains informational.
- External Shell Component Test passed: 1 file / 1 test.
- Targeted ESLint for all changed/new R5B frontend files passed.
- Finalized Snapshot schema JSON parse passed.
- `git diff --check` passed before result documentation and is rerun after it.

## Checks intentionally omitted

Backend/AI full suites, full `postgresTest`, Testcontainers, frontend baseline and production build, Docker rebuild, Provider/external-module execution, and browser/manual testing were intentionally omitted under Fast Mode.

## Remaining risks

- Flyway V12 constraint changes, decision backfill, Hibernate validation, and PostgreSQL behavior need user verification.
- The generic deterministic field patch supports dotted paths and exact proposal values; real external proposals must use planning field paths agreed by contract.
- External module callbacks/results beyond the truthful Handoff/Run Shell are future integration work and cannot be treated as completed analysis.
- Responsive, accessibility, long-content, history, stale, and all decision/finalize paths need browser acceptance.

## Exact continuation point

Run `docs/rebuild/verification/R5B_USER_VERIFICATION.md`, then the integrated `docs/rebuild/verification/R5_USER_VERIFICATION.md`. Stop if any V12, deterministic application, immutable hash, auth, stale, or truthful Shell gate fails. Do not begin R6 automatically.
