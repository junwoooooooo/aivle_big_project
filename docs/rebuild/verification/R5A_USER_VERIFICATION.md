# R5A User Verification

## 1. Commands to run

From the repository root in PowerShell:

```powershell
git diff --check

Set-Location backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.integration.MarketResultSchemaTests" --tests "com.aivle.backend.pipeline.integration.SnapshotStalenessTests" --tests "com.aivle.backend.pipeline.integration.PlanningChangeProposalTests"

Set-Location ..\frontEnd
npm.cmd run test:run -- src/features/market-integration/components/PlanningChangeCard.test.jsx
npx.cmd eslint src/features/market-integration/api/marketIntegrationApi.js src/features/market-integration/hooks/useMarketIntegration.js src/features/market-integration/model/marketResultModel.js src/features/market-integration/components/PlanningChangeCard.jsx src/features/market-integration/components/PlanningChangeCard.test.jsx src/features/market-integration/pages/MarketIntegrationPage.jsx
```

Success criteria:

- `git diff --check` prints nothing.
- Java compilation succeeds.
- All three backend R5A test classes execute and pass. If Gradle reports the existing `ProjectModuleStatusServiceTests` constructor mismatch, fix that separate baseline issue first and rerun; do not count the R5A tests as passed before they execute.
- The Change Card reports 1 passing test and ESLint exits with code 0.

## 2. Database initialization and Docker rebuild

- Database reset: not required for an existing valid V10 database. Flyway must apply V11 in place.
- Services requiring image rebuild: `backend` and `frontend` only.
- No AI server or external market module image rebuild is required for R5A.

When Docker verification is desired, use the existing Compose stack and rebuild only the changed application services:

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml ps
docker compose -f compose.yaml -f compose.e2e.yaml exec postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "select version, success from flyway_schema_history where version = '11';"
```

Success criteria: backend/frontend are healthy, Flyway V11 has `success = true`, and Hibernate schema validation does not fail. Confirm `module_results`, `planning_change_proposals`, and `NEEDS_INPUT` in the `module_runs` status constraint.

## 3. Stub and API verification

Use an e2e/dev-header-auth project owned by the selected user. In `/market`, first prepare a Market Handoff. Then create a local/e2e Stub result:

```powershell
$projectId = 1
$ownerUserId = 1
$headers = @{ "X-User-Id" = "$ownerUserId"; "X-User-Role" = "USER"; "X-Request-Id" = "r5a-stub-check" }
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v3/projects/$projectId/market-results/fixture/stub" -Headers $headers
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v3/projects/$projectId/market-result" -Headers $headers
```

Success criteria:

- Stub creation is available only with `local`, `test`, or `e2e`; it is absent with production-like `postgres` alone.
- The returned `moduleRunId` and `inputSnapshotId` match the prepared Run/Handoff.
- A repeated identical intake is idempotent; a different hash for the same Run is rejected.
- A mismatched `inputSnapshotId`, invalid result hash, name-only/unverified competitor, missing source reference, generic proposal title, or invalid decision is rejected.
- `POST /api/v3/internal/market-results` rejects missing/wrong `X-Internal-Api-Key`. It must not accept a user JWT as a substitute. Do not print or collect the real key.
- Project result read and proposal decision endpoints require user authentication and project ownership.

## 4. Browser checks

Open `http://localhost:3000/projects/<projectId>/market` and verify:

1. Before importing/generating a result, `Not Connected` remains visible and no fake completion appears.
2. After the Stub, the screen shows market summary, target-customer implications, competitors, pricing/channel implications, planning change proposals, and the analysis Snapshot.
3. Every competitor shows product/company, official URL, description, price evidence, features, target customer, research/source evidence, and verification status.
4. Proposal cards use meaningful titles rather than v1/v2/v3, and show current/proposed values, reason, evidence, and impact.
5. `채택` and `거절` persist their states. `부분 채택` requires and preserves the user's edited value.
6. After selecting a different concept/Snapshot, the old result remains visible as `STALE` and is clearly reference-only.
7. Verify keyboard focus, link safety, screen-reader labels, 390×844, 768×1024, 1280+, 200% zoom, reduced motion, and long Korean content.

## 5. Logs to collect on failure

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs --since 30m backend frontend postgres
```

Collect the request ID, project ID, Handoff ID, Module Run ID, input Snapshot ID/hash, result hash, proposal ID/action, Flyway V11 row, failed database constraint, backend safe error, and browser console/network evidence. Do not collect JWTs, internal API keys, authorization headers, prompts, Provider bodies, or unnecessary raw business content.

## 6. Next-stage condition

Proceed only after schema, stale Snapshot, proposal decision, Change Card, V11 migration, auth separation, Not Connected behavior, responsive/accessibility, and all three user decisions pass. Stop after R5A acceptance; do not automatically implement finalized planning or start the next stage.
