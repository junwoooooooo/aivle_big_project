# R1B Result — Backend Pipeline Foundation, Module Status and Legacy API Surface Deactivation

## Outcome

R1B backend foundation and API-surface cutover is implemented on branch `rebuild/new-pipeline-v1` at starting HEAD `a363be69ceaa4569274aeb9ef6ddb6819a002f4f`.

The backend now exposes an authenticated, ownership-scoped module status query at `GET /api/v3/projects/{projectId}/modules`. It returns six independent module states without reading `project.stage`.

All 21 legacy product controllers found in the R0 audit scope are retained in source but are disabled by default through the shared `@LegacyPipelineSurface` condition. They are registered only when `app.legacy-pipeline.enabled=true` is explicitly supplied. Authentication, project, file infrastructure, TaskRun, JobEvent/SSE, audit, and admin controllers were not conditioned or removed.

The existing R1A frontend changes and pre-existing `.gitignore` modification were preserved unchanged. No Migration, AI, frontend, dependency, service, entity, or legacy controller implementation was deleted.

## Files changed

Created under `backend/src/main/java/com/aivle/backend/pipeline/module/`:

- `PipelineModuleType.java`
- `PipelineModuleStatus.java`
- `ProjectModuleStatusResponse.java`
- `ProjectModuleStatusService.java`
- `ProjectModuleStatusController.java`
- `LegacyPipelineSurface.java`

Created tests:

- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/LegacyPipelineSurfaceConditionTests.java`

Updated configuration:

- `backend/src/main/resources/application.yaml`

Conditioned legacy controllers:

- Document: `DocumentController`, `StructuredPlanController`
- Legacy analysis: `LegalReviewController`, `FeasibilityAssessmentController`, `FinancialAnalysisController`, `FinancialAnalysisSourceController`
- Journey: `JourneyController`, `ConceptJourneyController`, `LegalPrecheckController`, `PersonaJourneyController`, `MarketingReportJourneyController`, `IdeaWorkspaceController`, `RegulatoryBoundaryController`, `ConceptExplorationController`
- Validation: `PersonaValidationController`, `PanelInterviewController`, `MarketResponseController`
- Persona: `BaselinePersonaController`, `ProjectPersonaCatalogController`, `PersonaRecommendationController`
- Legacy marketing: `MarketingContentController`

Created stage documents:

- `docs/rebuild/progress/R1B_RESULT.md`
- `docs/rebuild/verification/R1B_USER_VERIFICATION.md`

## Contracts implemented

- Module types: `IDEA`, `CONCEPT_FACTORY`, `CONCEPT_SELECTION`, `MARKET_ANALYSIS`, `BUSINESS_PERSONA_TEST`, `MARKETING`.
- Module statuses: `NOT_READY`, `READY`, `QUEUED`, `RUNNING`, `NEEDS_INPUT`, `COMPLETED`, `FAILED`, `STALE`, `NOT_CONNECTED`.
- Every module response includes `projectId`, `module`, `status`, `statusLabelKey`, `requiredInputs`, `nextAction`, nullable `activeRunId`, nullable `sourceSnapshotId`, and nullable `updatedAt`.
- Responses use the existing `ApiResponse` success envelope and `X-Request-Id` metadata convention.
- The service verifies project ownership through `findByIdAndOwnerIdAndDeletedAtIsNull`; missing or foreign-owned projects use the existing `PROJECT_NOT_FOUND` response path.
- Safe R1B defaults are derived only from basic project information:
  - `IDEA`: `READY` when a description exists, otherwise `NEEDS_INPUT`.
  - `CONCEPT_FACTORY`: `NOT_READY`.
  - `CONCEPT_SELECTION`: `NOT_READY`.
  - `MARKET_ANALYSIS`: `NOT_CONNECTED`.
  - `BUSINESS_PERSONA_TEST`: `NOT_CONNECTED`.
  - `MARKETING`: `NOT_READY`.
- No pipeline class imports or reads `ProjectStage`, and the existing stage field remains unchanged.
- Default configuration is `app.legacy-pipeline.enabled=false`, optionally sourced from `LEGACY_PIPELINE_ENABLED`. No UI or administrative setting was added.
- No module execution endpoint or future-stage persistence behavior was implemented.

## Checks actually run

- Root `AGENTS.md`, branch, HEAD, and dirty-worktree preflight.
- Required R1B governing documents and relevant Manifest paths.
- Audit-scope controller inventory comparison: 21 `@RestController` files and 21 conditioned files, with no omissions.
- New pipeline source search confirming no `ProjectStage`, `getStage()`, or `project.stage` dependency.
- Targeted Gradle test invocation:
  - Initial sandboxed attempt could not download Gradle 9.5.1 because network access was denied; tests did not start.
  - The single permitted re-run with network approval completed.
  - `ProjectModuleStatusServiceTests`: 2 tests, 0 failures, 0 errors.
  - `LegacyPipelineSurfaceConditionTests`: 1 test, 0 failures, 0 errors.
- The targeted Gradle execution compiled the changed Java sources successfully, providing the changed-file Java syntax check.
- `git diff --check`: recorded after final document creation.

## Checks intentionally omitted

- Full backend test suite and standalone full compile task.
- Full `postgresTest` and Testcontainers suites.
- Backend integration test suite and OpenAPI endpoint comparison.
- Frontend tests, lint, baseline, and production build.
- AI tests and provider smoke.
- Docker Compose build/start and runtime HTTP smoke.
- Database reset or Migration checks.
- Browser testing.
- Commit and push.

## Remaining risks

- Runtime MockMvc and Docker verification of the real controller registry was intentionally deferred; the property condition itself is covered by a lightweight application-context test.
- Existing legacy integration tests that expect conditioned controllers will need explicit legacy opt-in if they are intentionally retained before R7. Default-runtime failure of those old API tests is expected after this cutover.
- Module status is currently computed and not persisted. Run and snapshot IDs remain null until their assigned implementation stages.
- The IDEA default uses only the current project description as a minimal readiness signal. The durable Idea Brief contract begins in R2.
- `LEGACY_PIPELINE_ENABLED=true` is an explicit compatibility escape hatch only; no UI or ordinary runtime configuration should enable it.
- Backend controller deactivation does not physically delete legacy services, entities, or routes from source; deletion remains R7 work.

## Exact continuation point

Stop after R1B. After the user completes `R1B_USER_VERIFICATION.md` and accepts the new endpoint and legacy API deactivation, the next separately authorized execution may begin at R1C preflight. Do not begin Migration or actual module behavior as part of this result.
