# R7 Result - Stabilization from User Verification Evidence

## Status

R7B fixed only failures demonstrated by the supplied R7A verification logs. The corrected Project
API and frontend auth/project tests are now Green in the user's rerun. The backend unit suite reached
`postgresTest`, where 12 failures were all stale pre-baseline migration or deleted Legacy-table test
contracts; those contracts are now consolidated around the single V1 baseline, and the directly
related PostgreSQL tests pass. Final product acceptance is **not confirmed** until the user reruns
the full `postgresTest` task and supplies browser, responsive, accessibility, Network Response,
screenshot, and real Provider Smoke evidence.

## Repository state

- Branch: `rebuild/new-pipeline-v1`
- Current HEAD: `5e3c13babe45bd3b9376cbe85aee2ee80c87a32b`
- Worktree: dirty only with the uncommitted R7B changes; no reset, clean, revert, stash, commit, or
  push was performed.

## Evidence classification and fixes

### Compile and application context

- Deleted tests and fixtures that exclusively referenced removed Legacy clients, controllers, and
  TaskType constants.
- Preserved common TaskRun, JobEvent, Internal AI transport, concurrency, and hashing tests by using
  active new-pipeline TaskTypes.
- Added common UTC `TimeConfiguration` because R7A removed the Legacy job configuration that had
  incidentally owned the shared `Clock` bean.
- The latest user rerun confirmed both production and test Java compilation pass.

### Migration

- Updated two migration contract tests from deleted V6/V8 filenames to
  `V1__new_pipeline_baseline.sql` and its actual final table and constraint names.
- Replaced `PostgreSqlBaselineMigrationTests` V1-V6 upgrade-chain scenarios with one clean-schema
  contract for exactly one V1 migration, all 37 retained tables, and explicit absence of nine Legacy
  tables.
- Deleted `PostgreSqlConstraintTests`; all five methods exclusively exercised removed document,
  structured-plan, and analysis-job tables and constraints.
- Updated `PostgreSqlContainerSmokeTests` from Flyway `5/5` to the final `1/1` contract.
- The two retained migration/smoke classes pass together in a targeted PostgreSQL run. The complete
  `postgresTest` task still requires a user rerun.

### Backend contract

- The latest remaining backend failure expected the removed Legacy `data.stage = DOCUMENT` field.
- `ProjectApiIntegrationTests` now asserts that `data.stage` does not exist and retains the common
  `data.status = DRAFT` assertion. No Legacy response field was restored.

### Frontend runtime and baseline

- `AccountSettingsPages.jsx` uses the active new-shell route helper.
- Stale tests now use `/app/projects/:id/idea`, the Project Module navigation, and active shell copy.
- The latest baseline's only failure queried the project-create link before the authenticated home
  route finished loading. The test now follows `Projects` and waits for the create link.
- The permitted targeted rerun reached the created project's overview and exposed two legitimate
  `Project Overview` headings. The final assertion is scoped to the stable
  `project-overview-title` page-heading id.
- The user's latest rerun confirms both `AuthProjectFlow` tests pass and the full frontend baseline
  reports 129 passed, 6 explicitly allowed failures, and 0 unexpected failures.
- No Legacy route alias or UI was restored.

### Docker configuration

- Verification commands use the actual Compose service name `ai-server`.
- The latest user evidence confirms Compose config, backend/AI/frontend image builds, startup, and
  health for backend, frontend, AI server, PostgreSQL, and Redis.

## Files changed by R7B

- `backend/src/main/java/com/aivle/backend/config/TimeConfiguration.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactorySqlContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/NewPipelineFoundationMigrationTests.java`
- `backend/src/test/java/com/aivle/backend/project/ProjectApiIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlContainerSmokeTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlConstraintTests.java` (deleted)
- `frontEnd/src/test/App.test.jsx`
- `frontEnd/src/test/AuthProjectFlow.test.jsx`
- `docs/rebuild/progress/R7_RESULT.md`
- `docs/rebuild/verification/FINAL_USER_ACCEPTANCE.md`
- `docs/rebuild/FINAL_CUTOVER_REPORT.md`

Earlier R7B evidence-based deletions and test-fixture conversions remain part of the current branch
history and are documented in the supplied verification record.

## Checks actually run by Codex

- Targeted backend application-context and final-baseline contract tests: passed, `BUILD SUCCESSFUL`.
- Targeted ESLint for changed frontend test files, including the final assertion edit: passed.
- Targeted `AuthProjectFlow.test.jsx` reproduction: failed at the premature create-link query.
- One permitted rerun after fixing navigation: reached the new overview and failed only at an
  ambiguous same-name heading query. The query was then scoped to `project-overview-title`; no third
  run was made because the stage permits one retry.
- Active Legacy source searches and removed frontend path/import searches: zero matches after the
  final edits.
- Final `git diff --check`: passed.
- Final `compileTestJava` attempt did not start compilation: Gradle Wrapper attempted to download
  Gradle 9.5.1 and the sandbox denied network access (`Permission denied: getsockopt`). The user's
  later run demonstrated Java production/test compilation.
- Targeted PostgreSQL verification for `PostgreSqlBaselineMigrationTests` and
  `PostgreSqlContainerSmokeTests`: `BUILD SUCCESSFUL` in 36 seconds after the Gradle distribution
  became available. This included `compileTestJava`.

## User evidence received

- Backend: the corrected `ProjectApiIntegrationTests` passed; the subsequent production/test compile
  and unit-test task passed before `postgresTest` reported 12 stale migration/Legacy-test failures.
- AI: 25 tests passed with one deprecation warning.
- Frontend: corrected `AuthProjectFlow` passed 2/2; lint passed; baseline passed with 129 tests plus
  6 explicitly allowed failures and 0 unexpected failures; production build passed with 245 modules
  and a non-blocking chunk warning.
- Docker: config, three image builds, startup, and all five service health checks passed.
- Static Legacy searches: zero active-source matches.

## Checks intentionally not run by Codex

Backend full tests/postgresTest, AI full tests, frontend baseline/build, Docker rebuild/E2E, browser
manual testing, Provider Smoke, mobile, and accessibility checks were not run by Codex. User-run
checks are reported as user evidence only.

## Remaining risks and exact continuation point

Rerun the full backend `postgresTest` task as documented in `verification/FINAL_USER_ACCEPTANCE.md`.
R7 can be declared complete only after that gate and Browser/Network, Provider, mobile, and
accessibility evidence are attached and Green. Do not proceed to deployment with exposed
credentials; rotate every secret included in shared verification output.
