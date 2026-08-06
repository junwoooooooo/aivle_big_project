# R6A Result — Selective Marketing Content Backend and AI Port

## Outcome

R6A implements the selective marketing-content generation vertical slice without exposing or importing the legacy Marketing Workspace, Persona evaluation, Panel Interview, Market Response, feasibility, or legacy LegalReview modules.

The new backend accepts only `FinalizedPlanningSnapshot` plus `MarketingContentRequest`, creates a closed Marketing Source Snapshot, queues `MARKETING_CONTENT_GENERATION` through TaskRun, publishes the specified `job.marketing.*` events, persists generated/user/finalized revisions separately, links immutable artifact references, reports stale content when the current finalized planning source changes, and exposes the six `/api/v3/projects/{projectId}/marketing-contents` endpoints.

The new AI task validates a closed input DTO, generates against a closed result DTO, uses the finalized source/legal controls only, and includes the requested provider schema smoke entry point. The AIdev assets were inspected read-only with `git show`; the legacy banner upload and Persona/experiment-dependent structures were not copied.

## Contracts implemented

- Request contract: `marketing-content-request-v1`; the stage-specified `BLOG_INTRO` replaces the earlier `BLOG` enum value.
- Source fields: `conceptName`, `targetSegment`, `problem`, `valueProposition`, `positioning`, `keyFeatures`, `pricing`, `channels`, `competitorDifferentiators`, `allowedClaims`, `prohibitedClaims`, `requiredDisclosures`, `sourceSnapshotHash`.
- Closed result contract: `marketing-content-result-v1`, with a closed legal-review summary and artifact-reference list.
- Content types: `SOCIAL_POST`, `AD_COPY`, `LANDING_PAGE`, `BLOG_INTRO`, `EMAIL`, `BANNER`, `POSTER`, `IMAGE_BRIEF`.
- Revision labels: `GENERATED`, `TONE_EDITED`, `SHORTENED`, `LEGAL_NOTICE_APPLIED`, `USER_EDITED`, `FINALIZED`; origin is separately persisted as `AI`, `USER`, or `SYSTEM`.
- Events: `job.marketing.queued`, `started`, `source_prepared`, `copy_generating`, `legal_checking`, `completed`, `failed`.
- Physical tables use `pipeline_marketing_*` names so the R6 implementation can coexist safely with V1 legacy tables until R7 cleanup.

## Files changed

- Backend task integration: `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java`.
- Backend package: all files under `backend/src/main/java/com/aivle/backend/pipeline/marketing/` (API, application, domain, repository, worker).
- Migration: `backend/src/main/resources/db/migration/V13__marketing_content_pipeline.sql`.
- Backend targeted test: `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingContentContractsTests.java`.
- AI internal routing: `ai/app/api/executions.py`.
- AI package: all files under `ai/app/tasks/marketing_content/`, including `prompts/`.
- Provider smoke: `ai/app/tools/marketing_content_provider_smoke.py`.
- AI targeted test: `ai/tests/test_marketing_content_contract.py`.
- Schemas: `docs/rebuild/contracts/marketing-content-request-v1.schema.json`, `docs/rebuild/contracts/marketing-content-result-v1.schema.json`.
- Stage artifacts: this file and `docs/rebuild/verification/R6A_USER_VERIFICATION.md`.

## Checks actually run

- `python -m compileall -q app tests/test_marketing_content_contract.py` — passed.
- `backend\\gradlew.bat compileJava` — passed (`BUILD SUCCESSFUL`). The first sandboxed attempt could not download Gradle because network access was denied; the approved retry passed.
- `backend\\gradlew.bat test --tests com.aivle.backend.pipeline.marketing.MarketingContentContractsTests` — passed (`BUILD SUCCESSFUL`): source snapshot, closed backend result, and revision/task lifecycle tests.
- `ai\\.venv\\Scripts\\python.exe -m pytest -q tests/test_marketing_content_contract.py` — passed, `2 passed`: request and AI result closed-schema tests. The first attempt used the system Python, which had no pytest; the repository virtual environment retry passed.
- Final `backend\\gradlew.bat compileJava` — passed (`BUILD SUCCESSFUL`).
- Final changed-file Python `compileall` and both marketing JSON schema parses — passed (`SCHEMAS_OK`).
- Final `git diff --check` — passed with no whitespace errors; Git emitted only an informational LF-to-CRLF working-copy warning for `ai/app/api/executions.py`.

## Checks intentionally omitted

- Backend/AI full suites, full `postgresTest`, Testcontainers, frontend baseline/build, Docker rebuild, browser/manual flow, real Provider smoke, and actual migration application were not run under Fast Mode.
- No commit, push, branch switch, merge, or cherry-pick was performed.

## Remaining risks

- V13 has not been applied to PostgreSQL in this run; Flyway and Hibernate validation must be checked by the user.
- The six HTTP endpoints, authentication/ownership, TaskRun worker recovery, SSE replay, and Provider-backed completion have not been exercised end-to-end.
- Finalized planning snapshots created from older heterogeneous concept shapes may produce `null` for fields absent from that immutable source; the AI input remains closed and does not query another database to fill them.
- R6A adds no frontend surface. Browser verification is limited to API/event inspection until the separately requested frontend stage.

## Exact continuation point

Run `docs/rebuild/verification/R6A_USER_VERIFICATION.md`. Accept R6A only after V13, backend/AI startup, API lifecycle, stale behavior, revision-origin, event sequence, and optional real Provider smoke pass. Stop afterward; request the next R6 substage separately.
