# R3B-PRE Contract Result — Concept Provider Failure Reconciliation

## Outcome

The Concept Provider failure contract is reconciled. `PROVIDER_FAILURE` is removed from the canonical Concept Slot state registry and provider failures are defined as Concept Attempt execution errors. D-009 is the governing ADR for R3B.

## Files changed

- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_IMPLEMENTATION_PLAN_v1.0.md`
- `docs/rebuild/R0_R7_CODEX_EXECUTION_PROMPTS_v1.0.md`
- `docs/rebuild/decisions/DECISION_LOG.md`
- `docs/rebuild/progress/R3A_RESULT.md`
- `docs/rebuild/progress/R3B_PRE_CONTRACT_RESULT.md`
- `docs/rebuild/verification/R3B_PRE_CONTRACT_USER_VERIFICATION.md`

## Contracts aligned

- Canonical Attempt errors are the eight classifications named in D-009.
- Transient provider failure keeps the current Slot execution state for one retry, then moves to `REPLACING`.
- Permanent provider failure terminates Slot and Run as `FAILED` with `retryable=false`.
- Schema invalid permits one `REPAIR` Attempt and then moves to `REPLACING`.
- Provider failure is excluded from Slot persistence, progress status, and query state.

## Checks actually run

- `git diff --check` — run after all document edits; no errors reported.
- Targeted contract search for `PROVIDER_FAILURE` — used to verify remaining occurrences describe its prohibition or Attempt error classification, not a Slot state.

## Checks intentionally omitted

No compile, tests, database, Docker, provider, frontend, or browser checks were run because this execution unit is documentation-only.

## Remaining risks

R3B production code does not yet persist Attempt error classification or implement these transitions. That implementation remains the next separately authorized stage.

## Exact continuation point

After accepting `R3B_PRE_CONTRACT_USER_VERIFICATION.md`, restart R3B from its preflight. Implement Attempt error persistence and Worker transitions against D-009 without changing the R3A Slot enum or V8 Slot-state constraint. Do not begin R3B in this execution.
