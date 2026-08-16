# IDEA-READINESS-DEADLOCK-AND-JOB-CENTER-ACTIONABILITY-FIX Result

Date: 2026-08-07

## Outcome

The Browser E2E-confirmed `RECOVERY -> REANALYZE -> RECOVERY` deadlock contract was removed. An Idea Brief now enters `READY_FOR_REVIEW` whenever unanswered questions and required missing fields are both zero, including when blocking contradictions remain. Backend readiness is the final confirm authority, and provider readiness remains advisory. Job Center preserves immutable raw `NEEDS_INPUT` TaskRun history while exposing only the current unresolved input request as actionable.

## Files changed

- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- Backend Idea Brief readiness calculator, derivation commit, service, domain invariant, and targeted tests.
- Backend Project Job view, repository, query projection, and targeted tests.
- AI Idea Brief final-synthesis normalization and schema/service test.
- Frontend Idea Intake state routing and tests.
- Frontend Job Center projection consumption, resolved notice handling, labels, and tests.
- This result and the matching user-verification document.

## Contracts implemented

- Business-level `NEEDS_INPUT` requires at least one unanswered question or required missing field; the domain transition rejects an empty requirement.
- `READY_FOR_REVIEW` is independent from `readyForConfirm`. Contradictions do not block Review entry but do keep `readyForConfirm=false`.
- Backend `readyForConfirm` requires required missing 0, unanswered 0, blocking contradiction 0, and current assessment hash. Provider readiness status is not a hard gate.
- Derivation commit chooses `NEEDS_INPUT` only from actionable question/missing counts; otherwise it commits `READY_FOR_REVIEW` and a successful TaskRun result.
- `FINAL_SYNTHESIS` remains question-free and normalizes provider `NEEDS_INPUT` to `READY_FOR_REVIEW` when required missing fields are empty.
- Frontend empty `NEEDS_INPUT` routes to Review. Explicit `recoveryRequired` and terminal-active-execution inconsistency retain Recovery precedence.
- Project Job responses add `rawStatus`, `actionable`, and `presentationStatus` without mutating TaskRun history.
- Only the latest Idea Brief job whose Domain remains `NEEDS_INPUT` is actionable. Older resolved raw `NEEDS_INPUT` history appears in recent jobs as `RESOLVED_INPUT` / `입력 반영 완료`.
- Refresh changes a stale selected-job `NEEDS_INPUT` notice to `RESOLVED_INPUT` once the server projection reports `actionable=false`.

## Checks actually run

- Backend `gradlew.bat compileJava` — passed (`BUILD SUCCESSFUL`).
- Backend targeted Idea readiness, derivation commit, field invariant, Idea service/canonicalization integration, Project Job query, and Project Job controller tests — passed (`BUILD SUCCESSFUL`).
- AI `.venv\\Scripts\\python.exe -m pytest tests/test_idea_brief_schema.py -q` — 6 passed.
- Frontend targeted Idea Intake/Review and Job Center Vitest — 4 files, 15 tests passed.
- Targeted Frontend ESLint — passed.
- `git diff --check` — passed (line-ending warnings only).

## Checks intentionally omitted

- Full Backend suite and full `postgresTest`.
- Full Frontend suite and production build.
- Docker E2E, browser automation, and live provider smoke.

## Remaining risks

- The clean-project browser flow remains the acceptance gate for real provider contradiction output and the full answer/patch/final-synthesis sequence.
- Job actionability normalization is intentionally limited to Idea Brief derivation; Concept Factory and other module behavior was not changed.
- Historical TaskRun and JobEvent rows remain immutable by design and may still show raw `NEEDS_INPUT` when inspected outside the Project Job projection.

## Exact continuation point

Run `docs/rebuild/verification/IDEA-READINESS-DEADLOCK-AND-JOB-CENTER-ACTIONABILITY-FIX_USER_VERIFICATION.md` on a clean project. Stop and report failure if an empty business `NEEDS_INPUT` enters Recovery, an answered historical input request remains active, or the Review/Confirm gates diverge from the documented conditions.
