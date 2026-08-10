# R4B Result — Concept Selection Snapshot, Market Handoff and Integration Shell

## Outcome

R4B now promotes an R4A preferred candidate through explicit user confirmation into an immutable `SelectedConceptSnapshot`, prepares a schema-aligned and idempotent market-analysis Handoff, stores a durable Module Run, and exposes `/market` as a non-blocking Integration Shell.

No market-analysis algorithm, external database access, Provider adapter, or false completion state was added. In the current unconnected environment a prepared Run is durably represented as `NOT_CONNECTED`.

## Files changed

- Backend selection domain under `backend/src/main/java/com/aivle/backend/pipeline/selection/`
  - Selection and immutable Snapshot entities, repositories, hashing/fingerprint policies, service, API models, and controller.
- Backend integration domain under `backend/src/main/java/com/aivle/backend/pipeline/integration/`
  - Handoff/Run entities, repositories, idempotency policy, schema DTO, service, and controller.
- `backend/src/main/resources/db/migration/V10__concept_selection_and_module_handoff.sql`
  - Adds `concept_selections`, `selected_concept_snapshots`, `module_handoffs`, and `module_runs` with ownership, hash, sequence, current-selection, idempotency, and status constraints.
- `backend/src/main/java/com/aivle/backend/pipeline/concept/repository/ConceptRepository.java`
  - Adds project-scoped published-concept lookup for selection validation.
- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
  - Adds safe prerequisite/stale-input errors.
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
  - Reports current Selection Snapshot and effective market Run state through the project shell.
- `backend/src/test/java/com/aivle/backend/pipeline/selection/SelectionAndHandoffContractTests.java`
  - Covers canonical Snapshot hashing, Selection/Handoff idempotency keys, and the provided market-input Schema fixture.
- Existing `frontEnd/src/features/concept-selection/`
  - Adds explicit selection-reason confirmation and authoritative selection creation while retaining the R4A local draft.
- New `frontEnd/src/features/market-integration/`
  - Adds current Selection/Run reads, idempotent Handoff preparation, `NOT_CONNECTED` UI, input manifest, and preserved Run history.
- `frontEnd/src/app/routing/AppRouter.jsx`
  - Connects `/app/projects/:projectId/market` to the new Integration Shell.
- R4B and integrated R4 progress/verification documents.

## Contracts implemented

- `POST /api/v3/projects/{projectId}/concept-selections`
- `GET /api/v3/projects/{projectId}/concept-selections/current`
- `POST /api/v3/projects/{projectId}/module-handoffs`
- `GET /api/v3/projects/{projectId}/module-runs`
- `GET /api/v3/projects/{projectId}/module-runs/{runId}`
- Selection accepts only a published concept from the current completed Concept Factory run.
- Snapshot body contains full concept planning, legal Assessment, required controls/partners/disclosures, prohibited variants, Evidence references, Snapshot ID/hash, sequence/parent, selection time, and selection reason.
- Snapshot hashes use recursively key-sorted canonical JSON and SHA-256.
- `selected-concept-market-input-v1` DTO has exactly the properties allowed by the provided JSON Schema.
- Handoff idempotency key is `module + inputSnapshotHash + requestedOperation`; identical preparation returns the existing Handoff and Run.
- Selection changes create new immutable Selection/Snapshot rows. Existing Module Runs are not overwritten; when their input differs from the current Snapshot, query responses expose effective `STALE` while preserving the stored Run.
- Module state registry supports `NOT_CONNECTED`, `READY`, `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, and `STALE`.
- `/market` opens without a Selection and gates only the Handoff action.

## Checks actually run

- Backend targeted contract test:
  - `./gradlew.bat test --tests "com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests"`
  - Initial sandbox attempt could not download Gradle due denied network access.
  - The permitted retry completed successfully: 3 tests passed and Java/test compilation succeeded.
- Frontend Selection targeted test:
  - `npm.cmd test -- --run src/features/concept-selection/components/SelectionConfirmation.test.jsx`
  - Result: 1 file passed, 1 test passed.
- Targeted frontend syntax/lint:
  - `npm.cmd exec eslint -- src/features/concept-selection src/features/market-integration src/app/routing/AppRouter.jsx`
  - Result: passed with no errors.
- Final backend changed-file syntax compile:
  - `./gradlew.bat compileJava`
  - Sandbox wrapper download was unavailable; permitted cached-Gradle retry passed in 2 seconds.
- `git diff --check`
  - Final result is recorded in the completion report after documentation creation.

## Checks intentionally omitted

- Backend full tests, AI tests, full `postgresTest`, Testcontainers, frontend baseline, frontend production build, Docker Compose rebuild, actual Provider smoke, and browser/manual accessibility testing were not run.
- V10 was not applied to a live PostgreSQL database by Codex.
- No external callback, market result, SSE, or actual market execution was tested because the external module is intentionally absent.

## Remaining risks

- V10 PostgreSQL/Flyway application and Hibernate validation require the user database gate.
- Concurrent transaction behavior is protected by a project row lock and partial current-selection index but has not been exercised against PostgreSQL.
- Maximum-size Snapshot JSON, long Korean selection reasons, and responsive UI require browser verification.
- Future external callbacks must add authentication/signature, timestamp, replay, project/handoff matching, and safe event publication before any status can advance beyond the current shell.
- `NOT_CONNECTED` is an intentional terminal presentation for this stage, not market-analysis completion.

## Exact continuation point

Run `docs/rebuild/verification/R4B_USER_VERIFICATION.md`, followed by the integrated `docs/rebuild/verification/R4_USER_VERIFICATION.md`. After every gate passes, stop. A later separately requested stage may connect a real external module or proceed to R5 planning revision, but it must consume the immutable Snapshot/Handoff contracts and must not retrofit an internal market algorithm.
