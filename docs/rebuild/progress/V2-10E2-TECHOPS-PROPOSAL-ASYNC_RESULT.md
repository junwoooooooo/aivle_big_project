# V2-10E2 — TechOps Proposal Async Result

## Outcome

Implementation complete. Runtime acceptance remains pending under the Local Fast Execution Profile.

TechOps initial missing-proposal generation and `REJECT_AND_REQUEST_ALTERNATIVE` no longer wait for a provider inside the HTTP transaction. Preparation is persisted first, actual `TECH_OPS_PROPOSAL` TaskRuns are claimed by a worker, and late results cannot overwrite direct user edits or a finalized Snapshot.

## Files changed

Backend:

- `backend/src/main/java/com/aivle/backend/pipeline/techops/api/TechOpsApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/api/TechOpsController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsProposalCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsProposalGateway.java` — removed
- `backend/src/main/java/com/aivle/backend/pipeline/techops/domain/TechOpsInputPreparation.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/worker/TechOpsProposalWorker.java`
- `backend/src/main/resources/db/migration/V4__v2_10e2_techops_proposal_async.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsControllerAsyncTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsServiceAsyncTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsProposalCompletionServiceTests.java`

AI:

- `ai/app/api/executions.py`

Frontend:

- `frontEnd/src/features/tech-ops/api/techOpsApi.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.test.jsx`
- `frontEnd/src/features/tech-ops/pages/TechOpsPage.jsx`
- `frontEnd/src/features/tech-ops/pages/TechOpsPage.test.jsx`

Governing documents:

- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`

## Contracts implemented

- `initialize()` stores the preparation before any provider work and queues at most one initial batch `TECH_OPS_PROPOSAL` TaskRun when one or more of the three proposal values are missing.
- Duplicate initialize for the same preparation returns the existing Query state and does not create another TaskRun.
- Initial provider result validates and fills all three proposal types in one completion transaction without overwriting already-present proposal values.
- `REJECT_AND_REQUEST_ALTERNATIVE` returns `202` with TaskRun/job identity and preserves the current proposal until worker success.
- Alternative input captures preparation ID, field key, current/next proposal versions, rejected proposal, Market Seed Snapshot ID/hash, expected preparation revision, and command key.
- Successful alternative commit stores only the requested field as `proposalVersion + 1`, `AI_HYPOTHESIS`, `PROPOSED`, `finalValue=null`; canonical and normalized semantic duplicates are rejected.
- Query exposes `proposalGenerationStatus`, `activeProposalTaskRunId`, `safeError`, and per-field `pendingAlternativeTaskRunId`.
- Direct `EDIT_AND_ACCEPT` remains provider-free and clears pending ownership, so a late worker becomes stale instead of overwriting the user value.
- Worker validates preparation revision, field proposal version, active task, source Snapshot ID/hash, and absence of a finalized TechOps Snapshot before commit.
- Technical failure preserves editable preparation state, publishes a safe failure, and permits direct input or a new-key retry with a new TaskRun ID.
- The synthetic `TaskRunWorkerContext(projectId=0, ownerId=0)` gateway was removed. Only actual claimed TaskRun context invokes the AI provider.
- Frontend generates command keys, restores pending/failure state from Query, follows SSE/polling, and refreshes Query at terminal.

## Checks actually run

- `backend\\gradlew.bat compileJava` — success.
- `backend\\gradlew.bat testClasses` — success.
- Targeted Backend tests — 13 passed, 0 failed:
  - `TechOpsControllerAsyncTests` 1
  - `TechOpsServiceAsyncTests` 3
  - `TechOpsProposalCompletionServiceTests` 4
  - `TechOpsPreparationContractsTests` 3
  - `TechOpsV2ContractTests` 2
- Targeted AI tests — 2 passed, 1 FastAPI dependency deprecation warning:
  - `test_internal_task_type_alignment.py`
  - `test_tech_ops_proposal.py`
- Targeted Frontend Vitest — 7 passed, 0 failed:
  - `useTechOps.test.jsx`
  - `TechOpsPage.test.jsx`
  - `techOpsModel.test.js`
- Targeted ESLint for changed TechOps frontend files — success, no findings.
- `git diff --check` — success; only Git line-ending warnings may be printed for pre-existing AI working-copy settings.

## Checks intentionally omitted

- Backend full regression and postgres/Testcontainers suites
- AI full suite
- Frontend full baseline and production build
- Docker/browser/real provider smoke
- manual accessibility/mobile acceptance

These checks are intentionally deferred by `LOCAL_FAST_EXECUTION_PROFILE.md`.

## Remaining risks

- Real Flyway migration, worker recovery after lease expiry, SSE fallback, and Job Center transitions need runtime verification.
- Real provider batch output and semantic-quality behavior were not smoke-tested.
- Same-key retry of an already-terminal failed command returns that immutable execution; the UI creates a new command key for a user retry as required.
- Full-suite interactions with external TechOps handoff were not run.

## Exact continuation point

Next Unit is `V2-10E3 — FINANCE LAZY ESTIMATE + ASYNC HARDENING`. Start from `FinancialService.initialize()`, `populateEstimates(...)`, `FinanceEstimateGateway`, Finance API/UI, and existing financial preparation tests. Do not implement F/G during E3.
