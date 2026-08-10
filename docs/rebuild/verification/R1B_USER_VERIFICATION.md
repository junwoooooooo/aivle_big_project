# R1B User Verification — Backend Pipeline Foundation and API Cutover

## Preconditions

- Repository root: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Expected branch: `rebuild/new-pipeline-v1`
- Java 17 is available.
- For HTTP checks, prepare a normal user account, its password, and an owned project ID as `<PROJECT_ID>`.
- Ensure `LEGACY_PIPELINE_ENABLED` is unset or `false`. Do not enable it during cutover verification.

## Backend compile

Run from `backend`:

```powershell
.\gradlew.bat compileJava
```

Success: Gradle reports `BUILD SUCCESSFUL` with no Java compilation error. This standalone compile was not run by Codex.

## Module Status targeted test

Run from `backend`:

```powershell
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests
```

Success: 2 tests pass. Verify the report at:

`backend\build\reports\tests\test\index.html`

The test must confirm six ordered modules, safe default states, nullable run/snapshot timestamps, and ownership-scoped repository lookup.

## Legacy controller condition test

Run from `backend`:

```powershell
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.LegacyPipelineSurfaceConditionTests
```

Success: 1 test passes, proving the legacy condition creates no bean when the property is absent and creates it only with `app.legacy-pipeline.enabled=true`.

## Docker rebuild and start

R1B changes backend source only. Database initialization is **not required** because no Migration or schema file changed.

Rebuild required service: **backend only**.

From the repository root, with the repository's required environment secrets already configured:

```powershell
docker compose build backend
docker compose up -d backend frontend
docker compose ps
```

Success: `backend` becomes healthy and `frontend` is reachable. PostgreSQL, MinIO, and AI dependencies may start because `backend` declares them, but their images do not require an R1B rebuild.

## Authentication setup for HTTP checks

The Compose frontend exposes the reverse-proxied application at `http://localhost:3000` by default. In PowerShell:

```powershell
$loginBody = @{
  username = '<USERNAME>'
  password = '<PASSWORD>'
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:3000/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body $loginBody

$apiHeaders = @{
  Authorization = "Bearer $($login.data.tokens.accessToken)"
  'X-Request-Id' = 'r1b-user-verification'
}

$projectId = '<PROJECT_ID>'
```

Success: `$login.data.tokens.accessToken` is non-empty. Do not print or store the token in shared logs.

## New module status endpoint

```powershell
$modules = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:3000/api/v3/projects/$projectId/modules" `
  -Headers $apiHeaders

$modules | ConvertTo-Json -Depth 8
```

Success:

- HTTP status is 200 and `success` is `true`.
- `data` contains exactly six entries in this order: `IDEA`, `CONCEPT_FACTORY`, `CONCEPT_SELECTION`, `MARKET_ANALYSIS`, `BUSINESS_PERSONA_TEST`, `MARKETING`.
- Every entry contains `projectId`, `module`, `status`, `statusLabelKey`, `requiredInputs`, and `nextAction`.
- `activeRunId`, `sourceSnapshotId`, and `updatedAt` are present with null values until later stages connect durable runs and snapshots.
- `IDEA` is `READY` when the project description exists, otherwise `NEEDS_INPUT`.
- Market and business/persona modules are `NOT_CONNECTED`; no execution is started.
- Repeating the request does not modify the project or its legacy `stage`.
- Requesting another user's project returns 404 with `PROJECT_NOT_FOUND` rather than exposing ownership information.

## Legacy API deactivation after Docker start

Use representative URLs from every conditioned controller group:

```powershell
$legacyUrls = @(
  "/api/v1/projects/$projectId/documents",
  "/api/v1/projects/$projectId/structured-plans/latest",
  "/api/v1/projects/$projectId/legal-reviews",
  "/api/v1/projects/$projectId/feasibility-assessments",
  "/api/v1/projects/$projectId/financial-analyses",
  "/api/v2/projects/$projectId/ideas",
  "/api/v2/projects/$projectId/legal-prechecks",
  "/api/v2/projects/$projectId/concept-generations/current",
  "/api/v2/projects/$projectId/persona-studies/current",
  "/api/v2/projects/$projectId/marketing-workspace",
  "/api/v1/projects/$projectId/validation-personas",
  "/api/v1/projects/$projectId/panel-interviews",
  "/api/v1/projects/$projectId/market-responses",
  "/api/v1/projects/$projectId/persona-recommendations",
  "/api/v1/personas/catalog",
  "/api/v1/projects/$projectId/marketing-contents"
)

foreach ($legacyUrl in $legacyUrls) {
  try {
    $legacyResponse = Invoke-WebRequest `
      -Method Get `
      -Uri "http://localhost:3000$legacyUrl" `
      -Headers $apiHeaders
    Write-Host "$legacyUrl -> $($legacyResponse.StatusCode)"
  } catch {
    Write-Host "$legacyUrl -> $($_.Exception.Response.StatusCode.value__)"
  }
}
```

Success: every legacy URL returns 404 and none returns legacy data or accepts work. A 401 means authentication setup is invalid and is not proof of controller deactivation. A 405 can indicate a still-registered mapping and must be investigated rather than accepted.

Also verify retained surfaces still respond normally:

- `/api/v1/projects`
- `/api/v2/jobs/<JOB_ID>/events?after=0` for an owned job
- required file infrastructure endpoints
- admin endpoints when using an admin token
- `/actuator/health`

## Browser checks

R1B adds no new browser UI. With the R1A frontend running:

- Sign in and open an owned project.
- Confirm authentication and project loading still work.
- Confirm browser Network requests do not call legacy Journey, Persona, Interview, Validation, document-analysis, or marketing-workspace APIs.
- If the frontend is later connected to the new endpoint, confirm the six module statuses match the JSON contract above. That frontend wiring is not part of R1B.

## Logs to collect on failure

```powershell
docker compose ps
docker compose logs --tail=300 backend
docker compose logs --tail=100 frontend
```

Also collect:

- The exact Gradle command and failing test report XML from `backend\build\test-results\test`.
- The failing URL, HTTP method, response status, sanitized response body, and `X-Request-Id`.
- The active value or absence of `LEGACY_PIPELINE_ENABLED` without printing unrelated secrets.
- Backend startup condition-evaluation errors and controller mapping conflicts.

Never collect JWT values, passwords, provider keys, authorization headers, or full sensitive request bodies.

## Next-stage condition

R1C may begin only after backend compile succeeds, both named targeted test classes pass, the authenticated `/api/v3/projects/{id}/modules` response satisfies the six-module contract, foreign-project access is hidden as 404, every representative legacy API returns 404, and retained auth/project/file/TaskRun/JobEvent/audit/admin surfaces remain available. Do not proceed if `LEGACY_PIPELINE_ENABLED=true` is required for ordinary runtime operation.
