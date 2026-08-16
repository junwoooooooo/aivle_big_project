# R5 Integrated User Verification

## Execution

Run `docs/rebuild/verification/R5A_USER_VERIFICATION.md` followed by `docs/rebuild/verification/R5B_USER_VERIFICATION.md`. Also run:

```powershell
git diff --check
docker compose -f compose.yaml -f compose.e2e.yaml ps
```

Success requires every R5A and R5B automated, migration, API, auth, browser, responsive, and accessibility gate to pass. Targeted tests alone are not integrated acceptance.

## Database and rebuild

No reset is required from a valid V10/V11 database. Flyway V11 and V12 must both be successful. Rebuild `backend` and `frontend`; do not rebuild the AI server solely for R5.

## End-to-end acceptance

Verify the complete flow: selected Snapshot → Market Handoff → authenticated evidence-backed result → current/stale handling → all proposal decisions → deterministic applied preview → immutable finalized Snapshot → identical finalized input to BM·financial and Persona shells → truthful `NOT_CONNECTED`/Run status and stale handling. Confirm external results never alter the finalized Snapshot and all probability disclaimers remain visible.

## Failure evidence

Collect the last 30 minutes of backend/frontend/PostgreSQL logs, request IDs, the involved Snapshot/Handoff/Run/proposal/decision identifiers and hashes, Flyway history, failed constraints, and browser console/network evidence. Exclude credentials, tokens, internal keys, prompts, Provider bodies, and unnecessary raw content.

## Continuation condition

Proceed only after the full R5 flow passes together. Stop after reporting R5 acceptance; do not begin R6 automatically.
