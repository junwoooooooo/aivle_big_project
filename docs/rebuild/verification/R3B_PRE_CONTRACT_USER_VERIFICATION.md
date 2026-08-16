# R3B-PRE Contract User Verification

## Commands

```powershell
git diff --check
rg -n "Slot:.*PROVIDER_FAILURE|PROVIDER_FAILURE.*Slot" docs/rebuild
rg -n "SCHEMA_INVALID|TRANSIENT_PROVIDER_FAILURE|PERMANENT_PROVIDER_FAILURE|ORIGIN_INVALID|LEGAL_REDESIGN_REQUIRED|LEGAL_REJECTED|INSUFFICIENT_INFORMATION|INTERNAL_EXECUTION_ERROR" docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md docs/rebuild/decisions/DECISION_LOG.md
git diff -- backend/src ai backend/src/main/resources/db/migration
```

## Success criteria

- `git diff --check` reports no whitespace errors.
- No canonical Slot registry contains `PROVIDER_FAILURE`; search matches may remain only where documents explicitly prohibit it.
- All eight Attempt error classifications appear consistently in the Data/API contract, Async Standard, and D-009.
- Production code, AI code, migrations, and tests have no diff.

## Browser check

None required. This stage changes documentation only and does not alter a user-visible route or runtime response.

## DB initialization and Docker rebuild

- DB initialization: not required.
- Docker rebuild: no services.

## Failure evidence

Capture `git status --short`, the complete `git diff --check` output, and the matching file/line from the two `rg` commands. No runtime logs are expected.

## Next-stage condition

R3B may start only after the searches confirm that `PROVIDER_FAILURE` is not a Slot state, D-009 is accepted, and the worktree contains only the intended documentation changes. R3B must then persist provider failures as Attempt error classification and apply the bounded transitions from D-009.
