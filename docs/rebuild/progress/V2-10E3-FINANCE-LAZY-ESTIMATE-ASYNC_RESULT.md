# V2-10E3 — Finance Lazy Estimate + Async Hardening Result

## Outcome

IMPLEMENTATION COMPLETE. RUNTIME ACCEPTANCE PENDING.

Finance preparation initialization is now provider-free. An estimate is generated only after the user requests one field, through an actual claimed `FINANCE_ESTIMATE` TaskRun. Synchronous accept/edit decisions and deterministic CAC remain outside the provider boundary.

## Files changed

Backend:

- `backend/src/main/java/com/aivle/backend/pipeline/finance/api/FinancialApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/api/FinancialController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinanceEstimateGateway.java` — removed
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialEstimateCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialInputSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialPreparationFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/worker/FinancialEstimateWorker.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialControllerAsyncTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialEstimateCompletionServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialPreparationContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialServiceAsyncTests.java`

AI:

- `ai/app/api/executions.py`

Frontend:

- `frontEnd/src/features/finance/api/financeApi.js`
- `frontEnd/src/features/finance/hooks/useFinance.js`
- `frontEnd/src/features/finance/hooks/useFinance.test.jsx`
- `frontEnd/src/features/finance/pages/FinancePage.jsx`
- `frontEnd/src/features/finance/pages/FinancePage.test.jsx`

Contracts and progress:

- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- `docs/rebuild/progress/V2-10E-LONG-RUNNING-ACTION-ASYNC-HARDENING_RESULT.md`

## Contracts implemented

- `initialize()` now performs only TechOps inheritance, open-field creation, explanations, and examples; it creates no estimate TaskRun and makes no provider call.
- Each eligible non-read-only field except `newCustomerCount` exposes lazy assistance state: `estimateStatus`, `activeTaskRunId`, `proposalValue`, `proposalVersion`, and `safeError`.
- `POST .../assistance/{fieldKey}/generate` requires `Idempotency-Key`, queues `FINANCE_ESTIMATE`, and returns `202` with TaskRun/job identity.
- Task input freezes preparation ID, field, next version, rejected proposal for alternatives, source TechOps Snapshot ID/hash, expected preparation revision, and current TechOps/financial context.
- Only the scheduled worker invokes the AI service with a claimed scalar context; the synthetic HTTP gateway was removed.
- Completion validates result shape and field identity, rejects canonical/semantic duplicate alternatives, and commits only a proposal. It never writes the final financial field.
- Worker commit rechecks active field task, preparation revision, source Snapshot ID/hash, field mutability, and absence of a finalized snapshot. Direct edits/decisions therefore beat late results.
- `ACCEPT` commits `AI_ESTIMATE + ACCEPTED`; `EDIT_AND_ACCEPT` commits `USER_INPUT + USER_EDITED_ACCEPTED`; both remain synchronous `200` operations.
- `REQUEST_ALTERNATIVE` queues a new TaskRun and returns `202`, retaining the current proposal until a distinct version 2 result succeeds.
- Technical failure leaves the financial field and prior proposal unchanged, records only a safe failure state, and permits direct input or a new-key retry.
- Unaccepted `PROPOSED` assistance values are stripped from the immutable financial snapshot.
- CAC remains the server calculation `(totalMarketingCost + totalSalesCost) / newCustomerCount` and is never an AI estimate.
- The Finance page exposes `추천 없음`, `추천 생성 중`, `AI 추천`, `채택됨`, and `추천 생성 실패`, follows the active TaskRun through job events, and restores Query state after terminal events.
- The aggregate V2-10E result retains its initial PARTIAL history and now records “초기 PARTIAL 이후 E1/E2/E3로 분할하여 완료” with implementation complete/runtime pending status.

## Checks actually run

- `backend\gradlew.bat compileJava` — success.
- `backend\gradlew.bat testClasses` — success.
- Targeted Backend tests — 14 passed, 0 failed:
  - `FinancialPreparationContractsTests` 4
  - `FinancialV2ContractTests` 2
  - `FinancialControllerAsyncTests` 1
  - `FinancialServiceAsyncTests` 3
  - `FinancialEstimateCompletionServiceTests` 4
- Targeted AI tests — 2 passed, 1 FastAPI dependency deprecation warning:
  - `test_finance_estimate.py`
  - `test_internal_task_type_alignment.py`
- Targeted Frontend Vitest — 5 passed, 0 failed:
  - `financeModel.test.js`
  - `FinancePage.test.jsx`
  - `useFinance.test.jsx`
- Targeted ESLint for changed Finance frontend files — success, no findings.
- `git diff --check` — success; only existing working-copy line-ending warnings were printed.

## Checks intentionally omitted

- Backend full regression and postgres/Testcontainers suites
- AI full suite
- Frontend full suite and production build
- Docker/browser/real-provider smoke
- Manual accessibility/mobile acceptance

These are intentionally deferred by `LOCAL_FAST_EXECUTION_PROFILE.md`.

## Remaining risks

- Real Flyway/runtime persistence, worker lease recovery, SSE fallback, and Job Center transitions still require user runtime verification.
- Real provider estimate quality and version-2 distinctness were not smoke-tested.
- Query follows one active field stream at a time; a terminal refresh discovers the next active field if several fields were queued concurrently.
- Same-key replay of a terminal command returns immutable execution history; UI retries intentionally create a fresh command key.

## Exact continuation point

Next Unit is `V2-10F — TECHOPS EVIDENCE REAL FILE UPLOAD WIRING`. Start by comparing the current TechOps evidence API/domain/frontend against the upload/storage subsystem and the F directive. Do not begin G until F implementation, targeted validation, RESULT, and USER_VERIFICATION are complete.
