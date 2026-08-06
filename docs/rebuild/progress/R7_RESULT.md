# R7 Result — Stabilization from User Verification Evidence

## Status

R7B fixed the repository failures demonstrated by the supplied R7A verification log. Final product
acceptance is **not yet confirmed** because the user has not rerun the corrected backend/frontend
gates and did not provide browser, responsive, accessibility, Network Response, screenshot, or real
Provider Smoke evidence.

## Starting state

- Branch: `rebuild/new-pipeline-v1`
- HEAD: `6b586985bac38bf2c723b1d4ef0db3cf2514d56a`
- Worktree: clean at R7B start
- R7A changes were already committed before this run; R7B did not commit or push.

## Evidence classification and fixes

### Compile

The user’s backend gate stopped in `compileTestJava` because tests still referenced deleted health,
marketing, persona adapters and deleted Legacy TaskType constants.

- Deleted five tests that exclusively exercised removed clients/controllers.
- Kept `AiServerClientConfigurationTests` and retained its common property-binding test.
- Converted common TaskRun, concurrency, canonical hashing and Internal AI transport test fixtures to
  new-pipeline TaskTypes.
- Removed only the test for the deleted Legacy legal auto-retry branch.

### Frontend Runtime

`AccountSettingsPages.jsx` imported the deleted feature-local route helper. It now imports
`app/routing/projectRoutes.js`, the active new-shell route contract.

### Frontend baseline

The baseline contained 12 resolved/deleted failures. Those entries were removed; the six unresolved
Auth failure entries reported as still active by the baseline remain.

### Docker Configuration

The verification document incorrectly named service `ai`. The Compose service is `ai-server`; build
and log commands were corrected. Docker daemon unavailability was environmental and later resolved
in the supplied log. The supplied `docker compose ps` showed all five runtime services healthy.

## Checks run by Codex

- `compileTestJava` first run: found the second Legacy TaskType fixture error group.
- `compileTestJava` permitted retry: failed only because a PowerShell write inserted UTF-8 BOM.
- BOM removed from the four affected Java files; byte inspection confirms `BOM=False`.
- No third Gradle run was made because the stage allows at most one retry.
- Static Legacy TaskType/client search in backend tests: zero matches.
- Active Legacy surface/task search: zero matches.
- Removed frontend route/import search: zero matches.
- Frontend route target exists and `test-debt-baseline.json` parses with six Auth entries.
- Targeted ESLint for `AccountSettingsPages.jsx`: passed.
- Final `git diff --check`: passed.

## Checks intentionally not run

Backend full tests/postgresTest, AI full tests, frontend baseline/build, Docker rebuild/E2E, browser
manual testing, Provider Smoke, mobile and accessibility checks were not run by Codex.

## Exact continuation point

Run the gates in `verification/FINAL_USER_ACCEPTANCE.md`. R7 can be declared complete only after the
corrected backend compile/test, frontend baseline/build, clean DB migration, Browser/Network,
Provider, mobile, and accessibility evidence is attached and Green.
