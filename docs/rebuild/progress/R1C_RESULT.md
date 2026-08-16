# R1C Result — New Pipeline Database Foundation and R1 Integration Completion

## Outcome

R1C is implemented on branch `rebuild/new-pipeline-v1` at starting HEAD `a363be69ceaa4569274aeb9ef6ddb6819a002f4f`.

An additive Flyway V6 migration establishes the four new-pipeline foundation tables without removing or rewriting legacy migrations or tables. The R1A project shell now loads the authenticated R1B module-status endpoint and contains API failure within the module-status area, leaving project navigation and settings usable.

Decision D-008 records why a clean baseline squash is deferred to R7: legacy JPA entities still participate in entity scanning and the PostgreSQL runtime validates their tables. The user-facing frontend and API surfaces are already separated from those legacy internals.

## Files changed

Created:

- `backend/src/main/resources/db/migration/V6__new_pipeline_foundation.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/module/NewPipelineFoundationMigrationTests.java`
- `frontEnd/src/app/module-status/projectModuleApi.js`
- `frontEnd/src/app/module-status/useProjectModuleStatuses.js`
- `docs/rebuild/progress/R1C_RESULT.md`
- `docs/rebuild/verification/R1C_USER_VERIFICATION.md`
- `docs/rebuild/progress/R1_RESULT.md`
- `docs/rebuild/verification/R1_USER_VERIFICATION.md`

Updated:

- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- `frontEnd/src/app/module-status/projectModuleModel.js`
- `frontEnd/src/app/module-status/projectModuleModel.test.js`
- `frontEnd/src/app/project-shell/ProjectLayout.jsx`
- `frontEnd/src/app/project-shell/ProjectModulePages.jsx`
- `frontEnd/src/app/project-shell/project-shell.css`
- `docs/rebuild/decisions/DECISION_LOG.md`

The existing R1A/R1B changes and unrelated pre-existing `.gitignore` modification were preserved.

## Contracts implemented

- Additive tables: `pipeline_module_runs`, `module_handoffs`, `module_results`, and `planning_snapshots`.
- Each durable run/result/snapshot is project-scoped and carries status, snapshot provenance, timestamps, and version or sequence fields appropriate to the record.
- FKs reuse `users`, `projects`, `stored_files`, and `task_runs`; the existing `job_events` and `audit_events` contracts remain unchanged.
- Composite project-scoped FKs prevent cross-project run, result, handoff, and parent-snapshot relationships.
- Module and status check constraints align with the R1B enums, including `NOT_CONNECTED` when no external module run exists.
- Existing V1–V5 migrations were not edited, reordered, or replaced. No data-conversion migration was added.
- The frontend maps all six API module names to R1A routes and preserves safe shell defaults for missing or unknown response items.
- API loading and failure affect only the status presentation. Failure shows a retry action; navigation, content, and project settings remain available.
- No module execution behavior, repository implementation, new JPA entity, or future-stage feature was added.

## Checks actually run

- Root `AGENTS.md`, branch, HEAD, dirty-worktree preflight, required R1C documents, and relevant manifest paths.
- Static SQL review: V1 through V6 filename order, all four table declarations, retained-table FKs, status/module checks, composite ownership constraints, and absence of destructive statements.
- SQL delimiter review: balanced parentheses in V6.
- Targeted changed-file frontend ESLint. The first invocation found one hook state-in-effect issue; after correction, the single permitted re-run passed with no findings.
- Targeted Gradle static migration test attempt:
  - The sandboxed attempt did not start because Gradle distribution download required network access.
  - The approved re-run compiled main and test Java, then failed one stale string assertion after the task-run FK was strengthened to `(id, project_id)`.
  - The assertion was corrected to the final composite FK contract. It was not run a third time because the stage permits at most one re-run.
- `PostgreSqlBaselineMigrationTests` expectations were statically updated from V5 to V6, including a V5-to-V6 upgrade case; the Testcontainers test was not executed.
- `git diff --check` after final edits.

## Checks intentionally omitted

- Full backend tests, standalone backend compile, full `postgresTest`, and all Testcontainers execution.
- Frontend full lint, baseline, full suite, and production build.
- Docker Compose build/start, real PostgreSQL migration, provider smoke, and browser/manual accessibility checks.
- Backend/AI full tests, commit, and push.

## Remaining risks

- The corrected `NewPipelineFoundationMigrationTests` assertion has not been re-run after its final edit; user verification must run the named test.
- V6 has not been applied to a real PostgreSQL instance. Composite FK/index compatibility and Flyway validation require the clean-schema gate.
- The frontend status integration has not been exercised against a live authenticated backend or in a browser.
- R1B currently computes safe module states; V6 tables are foundation only and are not yet read by that service.
- Existing databases do not require reset for this additive migration, but a disposable clean database is still required to validate the complete V1–V6 chain.
- Legacy entities and tables remain until R7; final clean baseline generation is intentionally deferred.

## Exact continuation point

Stop after R1C. Run the one-time R1 gate in `docs/rebuild/verification/R1_USER_VERIFICATION.md`. Only after every gate passes and the user separately authorizes the next stage should work resume at the R2 preflight. Do not begin R2 automatically.
