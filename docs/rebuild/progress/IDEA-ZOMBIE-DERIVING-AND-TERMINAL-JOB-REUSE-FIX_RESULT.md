# IDEA-ZOMBIE-DERIVING-AND-TERMINAL-JOB-REUSE-FIX Result

Date: 2026-08-07

## Outcome

Idea execution identity now includes the user command idempotency key instead of deriving execution identity from canonical content. A new reanalyze command creates a new TaskRun even when the input hash matches terminal history. `NEEDS_INPUT` result adoption aligns Idea Brief, TaskAttempt, TaskRun, and JobEvent. Terminal JobEvent history rejects every subsequent publish. A `DERIVING` brief pointing to a terminal TaskRun is exposed as recoverable and can be reconnected without changing old history.

## Files changed

- Shared TaskRun create/adoption domain and service.
- Shared JobEvent terminal publish guard.
- Idea API response, service execution identity/reconciliation, commit, worker, and targeted tests.
- Idea Intake terminal-job recovery and failure-category UI/tests.
- Product Spec, Async Execution standard, this result, and user verification.

## Contracts implemented

- `TaskRunService.CreateResult` distinguishes newly created and replayed executions.
- Idea command replay returns its existing active execution; a new command key produces a new execution after terminal history while `SAME_INPUT_ACTIVE` remains enforced.
- A replayed terminal TaskRun is never passed to `startDeriving` or `startFinalSynthesis`.
- `adoptNeedsInput` preserves the validated TaskResult and sets Attempt/Run to `NEEDS_INPUT`, including `finalResultId` and finish time.
- Worker publishes `NEEDS_INPUT` only after aligned adoption and `COMPLETED` for `READY_FOR_REVIEW`.
- JobEvent publisher rejects a follow-up event after terminal history with `TERMINAL_JOB_EVENT_IMMUTABLE`.
- GET Idea Brief returns `executionStateConsistent` and `recoveryRequired`; terminal active pointers never remain an ordinary RUNNING response.
- Frontend also detects a locally terminal job that remains attached after refresh, classifies it as `STATE_RECONCILIATION_REQUIRED`, and requires a different job ID from reanalyze.
- Failure categories are `DERIVATION_FAILURE`, `INTERACTION_FAILURE`, and `STATE_RECONCILIATION_REQUIRED`.

## Checks actually run

- Backend `compileJava` — passed.
- Idea canonicalization/zombie repository integration tests — passed.
- TaskRun service integration tests — passed.
- JobEvent publisher integration tests — passed.
- Idea worker and commit unit tests — passed.
- Frontend Idea Intake Vitest — 5 files, 12 tests passed.
- Targeted Frontend ESLint — passed.
- `git diff --check` — passed.

## Checks intentionally omitted

- Full Backend suite, full `postgresTest`, full Frontend build, Docker E2E.

## Remaining risks

- The confirmed poisoned PostgreSQL row and historical invalid event sequence were simulated with real H2 repositories; the existing browser project remains the mandatory runtime recovery gate.
- Existing historical events are intentionally immutable and are not repaired or deleted.

## Exact continuation point

Reload the existing stuck project and execute every step in `docs/rebuild/verification/IDEA-ZOMBIE-DERIVING-AND-TERMINAL-JOB-REUSE-FIX_USER_VERIFICATION.md`. Do not start another feature unit until the old job remains immutable and the new job completes with aligned domain/TaskRun/Event state.
