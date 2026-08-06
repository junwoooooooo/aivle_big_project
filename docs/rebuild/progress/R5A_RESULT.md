# R5A Result — Market Result Intake, Competitor Evidence and Planning Change Proposals

## Outcome

R5A now accepts external market-analysis results through a dedicated internal-service endpoint, validates the immutable Handoff Snapshot and canonical result hash, preserves result/proposal evidence, exposes JWT-protected project reads and decisions, and renders the result as understandable planning changes. No market research or generation algorithm was implemented.

When no result exists, `/market` remains truthfully `Not Connected`. A result whose `inputSnapshotId` is no longer current is returned and displayed as `STALE` without overwriting its stored status or history. Local/test/e2e profiles provide fixture import and a deterministic development Stub endpoint; production/profile `postgres` alone does not register those endpoints.

## Files changed

- Contract/config: `docs/rebuild/contracts/market-analysis-result-v1.schema.json`, `backend/src/main/resources/application.yaml`, `compose.yaml`
- Database: `backend/src/main/resources/db/migration/V11__market_result_and_planning_proposals.sql`
- Backend integration API/application/domain/repositories under `backend/src/main/java/com/aivle/backend/pipeline/integration/**`
- Backend security routing: `backend/src/main/java/com/aivle/backend/auth/SecurityConfiguration.java`
- Backend targeted tests under `backend/src/test/java/com/aivle/backend/pipeline/integration/**`
- Frontend market result API, hook, model, result page, proposal card, styles, and card test under `frontEnd/src/features/market-integration/**`

## Contracts implemented

- `market-analysis-result-v1` envelope with `moduleRunId`, `inputSnapshotId`, status, reference, summary, competitors, proposals, completion time, and canonical SHA-256 hash.
- Evidence-backed competitor fields, HTTPS official/source references, and explicit verification status; `UNVERIFIED` name-only competitors are rejected.
- Meaningful proposal title, affected fields, before/after values, reason, evidence, impact areas, and decision state.
- User decisions `ADOPT`, `PARTIALLY_ADOPT`, and `REJECT`; partial adoption requires a user-edited value and other decisions reject one.
- Internal result intake: `POST /api/v3/internal/market-results` with `X-Internal-Api-Key`; it is not a user JWT endpoint.
- User JWT/project-ownership endpoints: result read and proposal decision under `/api/v3/projects/{projectId}`.
- Local/test/e2e-only fixture import and Stub result endpoints.
- Immutable Handoff Snapshot matching and effective `STALE` projection against the current selected Snapshot.
- UI sections: market summary, target-customer implications, competitors, pricing/channel implications, planning changes, and analysis Snapshot.

## Checks actually run

- `git diff --check`: passed before documentation finalization and rerun after it below.
- Market result schema JSON parse: passed.
- Backend `compileJava`: passed (`BUILD SUCCESSFUL`, 3 seconds on final run).
- Frontend Change Card targeted test: passed, 1 file / 1 test.
- Targeted frontend ESLint for all changed market-integration JS/JSX: passed.
- Backend targeted test command was attempted. Main source compilation passed, but Gradle stopped in global `compileTestJava` because the pre-existing `ProjectModuleStatusServiceTests` constructs `ProjectModuleStatusService` with an obsolete one-argument constructor. Therefore the new schema, stale Snapshot, and proposal decision tests did not execute and are not claimed as passed.

## Checks intentionally omitted

Backend full tests, AI full tests, full `postgresTest`, Testcontainers, frontend baseline, frontend production build, Docker/Compose rebuild, real Provider smoke, and browser/manual testing were not run under Fast Mode.

## Remaining risks

- Flyway V11 and Hibernate validation still require PostgreSQL verification.
- Internal callback behavior still needs an environment-supplied `MARKET_MODULE_INTERNAL_API_KEY` and real module integration verification.
- Backend R5A tests remain blocked by the unrelated existing test-source compile failure described above.
- Responsive layout, keyboard/screen-reader behavior, long Korean content, external links, and all three decision paths require browser acceptance.
- R5A records proposal decisions but does not create a finalized planning Snapshot; that belongs to the separately requested later execution unit.

## Exact continuation point

Run `docs/rebuild/verification/R5A_USER_VERIFICATION.md`. Resolve the existing `ProjectModuleStatusServiceTests` constructor mismatch before treating backend targeted tests as green. If every R5A gate passes, stop and request the next stage separately; do not begin final planning or R5B automatically.
