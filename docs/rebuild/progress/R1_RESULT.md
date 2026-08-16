# R1 Result — Active Surface, Backend Foundation, and Database Integration

## Integrated outcome

R1A, R1B, and R1C establish the new-pipeline boundary end to end:

- R1A replaces the active Journey UI with an eight-route, six-stage project shell for desktop and mobile. Direct legacy project routes no longer render legacy pages.
- R1B adds the ownership-scoped `GET /api/v3/projects/{projectId}/modules` contract and disables 21 audited legacy controllers by default with `app.legacy-pipeline.enabled=false`.
- R1C adds the additive V6 pipeline foundation schema, connects the shell to the module-status API with contained error handling, and records final baseline squash deferral to R7.

Authentication, `ProjectContext`, settings overlay/direct settings route, projects/files, TaskRun/JobEvent/audit, and admin surfaces remain. Legacy source and tables are retained for R7 removal; no new feature was added to legacy Journey code.

## Principal files and contracts

- Frontend shell/routing/status: `frontEnd/src/app/project-shell/`, `frontEnd/src/app/routing/`, `frontEnd/src/app/module-status/`.
- Backend module contract: `backend/src/main/java/com/aivle/backend/pipeline/module/`.
- Pipeline schema: `backend/src/main/resources/db/migration/V6__new_pipeline_foundation.sql`.
- Legacy API condition: shared `@LegacyPipelineSurface` plus default-off application property.
- Decision: D-008 in `docs/rebuild/decisions/DECISION_LOG.md`.

Detailed changed-file inventories and substage checks are in `R1A_RESULT.md`, `R1B_RESULT.md`, and `R1C_RESULT.md`.

## Checks actually run across R1

- Branch/HEAD/dirty-state and relevant governing-document preflight for each substage.
- R1A module route/model targeted tests passed; targeted frontend ESLint passed.
- R1B module status service tests passed (2 tests); legacy condition test passed (1 test); targeted Gradle compilation succeeded.
- R1C SQL/order/destructive-statement static review passed; final targeted frontend ESLint passed.
- R1C static migration test compiled but failed a stale assertion; the assertion was fixed and intentionally not run a third time under the re-run limit.
- Final `git diff --check` passed.

## Checks intentionally omitted

No full frontend lint/build/baseline, full backend/AI tests, full PostgreSQL/Testcontainers suite, Docker rebuild/start, provider smoke, browser manual test, commit, or push was run by Codex.

## Remaining risks

- The R1C corrected migration test and actual PostgreSQL V1–V6 migration remain unverified at runtime.
- Browser behavior, responsive/accessibility details, redirects, and live frontend/backend integration remain user gates.
- The default-off legacy-controller registry requires Docker HTTP confirmation across representative endpoints.
- Module states remain safe computed defaults and are not yet driven by durable runs.
- Legacy entities/tables remain in runtime compatibility scope until R7, when the clean baseline can be generated.

## Exact continuation point

Run `docs/rebuild/verification/R1_USER_VERIFICATION.md` once as the integrated R1 acceptance gate. Resolve any failure within R1 scope. After all criteria pass, stop and request separate R2 authorization; do not continue automatically.
