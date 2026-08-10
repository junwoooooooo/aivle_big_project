# R6A User Verification — Marketing Content Backend and AI

Run commands from `C:\Users\seewo\Desktop\big_proj_01\new_3` unless a command changes directory explicitly.

## 1. Fast contract checks

```powershell
Set-Location backend
.\gradlew.bat test --tests com.aivle.backend.pipeline.marketing.MarketingContentContractsTests
Set-Location ..\ai
.\.venv\Scripts\python.exe -m pytest -q tests/test_marketing_content_contract.py
Set-Location ..
git diff --check
```

Success criteria: Gradle reports `BUILD SUCCESSFUL`, pytest reports `2 passed`, and `git diff --check` prints no errors.

## 2. Database and service startup

DB initialization/reset is **not required** for an existing Flyway-managed development database. V13 creates isolated `pipeline_marketing_contents`, `pipeline_marketing_content_revisions`, and `pipeline_marketing_assets` tables and preserves the legacy V1 marketing tables. Back up or reset only if the database has a failed/partially applied V13 entry.

The changed runtime services are `backend` and `ai-server`; rebuild those images. `postgres` must be running but does not require an image rebuild. `frontend` does not need rebuilding for R6A.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d postgres
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build ai-server backend
docker compose -f compose.yaml -f compose.e2e.yaml ps
docker compose -f compose.yaml -f compose.e2e.yaml logs --since=10m postgres backend ai-server
```

Success criteria: all three services are healthy/running, Flyway applies V13 exactly once, Hibernate reports no schema mismatch, and the AI internal execution endpoint starts without import errors.

## 3. Provider schema smoke (user-owned; requires configured Provider credentials)

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml exec ai-server python -m app.tools.marketing_content_provider_smoke
```

Success criteria: the command returns one `marketing-content-result-v1` object with exactly the closed fields, the requested `SOCIAL_POST` type, no prohibited claim, and the required disclosure applied. A missing credential/configuration is not an R6A schema success; correct the Provider environment and rerun.

## 4. API lifecycle

Use an authenticated user who owns a project with a current finalized planning snapshot. Replace `<TOKEN>`, `<PROJECT_ID>`, and `<PLANNING_SNAPSHOT_ID>`.

```powershell
$headers = @{
  Authorization = 'Bearer <TOKEN>'
  'Content-Type' = 'application/json'
  'Idempotency-Key' = 'r6a-create-001'
  'X-Correlation-Id' = 'r6a-correlation-001'
}
$body = @{
  contract = 'marketing-content-request-v1'
  planningSnapshotId = '<PLANNING_SNAPSHOT_ID>'
  contentType = 'SOCIAL_POST'
  channel = 'Instagram'
  purpose = 'launch awareness'
  tone = 'clear and friendly'
  length = 'SHORT'
  requiredPhrases = @()
  excludedPhrases = @()
  additionalInstruction = $null
} | ConvertTo-Json -Depth 8
$created = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v3/projects/<PROJECT_ID>/marketing-contents' -Headers $headers -Body $body
$created | ConvertTo-Json -Depth 12
```

Then exercise:

```powershell
Invoke-RestMethod -Headers @{Authorization='Bearer <TOKEN>'} -Uri 'http://localhost:8080/api/v3/projects/<PROJECT_ID>/marketing-contents'
Invoke-RestMethod -Headers @{Authorization='Bearer <TOKEN>'} -Uri 'http://localhost:8080/api/v3/projects/<PROJECT_ID>/marketing-contents/<CONTENT_ID>'
Invoke-RestMethod -Method Post -Headers @{Authorization='Bearer <TOKEN>'} -Uri 'http://localhost:8080/api/v3/projects/<PROJECT_ID>/marketing-contents/<CONTENT_ID>/finalize'
```

For `PATCH`, send the latest closed result object as `result` and one of `TONE_EDITED`, `SHORTENED`, `LEGAL_NOTICE_APPLIED`, or `USER_EDITED` as `revisionType`. For regenerate, add a new `Idempotency-Key` and `X-Correlation-Id` and POST to `/<CONTENT_ID>/regenerate`.

Success criteria:

- create returns `QUEUED`, then GET reaches `COMPLETED` with a `GENERATED`/`AI` revision;
- a user PATCH adds a distinct `USER` revision and never overwrites the generated revision;
- finalize adds a `FINALIZED`/`SYSTEM` revision and prevents further edit/regeneration;
- regeneration queues a new TaskRun and preserves earlier revisions;
- using an old planning snapshot ID is rejected, and changing/finalizing planning later makes the prior content read as `STALE`;
- source JSON contains exactly the 13 allowed source fields and no Persona, Panel, Market Response, feasibility, raw legal record, prompt, provider payload, or secret.

## 5. Browser checks

R6A has no new frontend route or page. In browser developer tools or an API client, verify the six `/api/v3/.../marketing-contents` calls and the project Job Event/SSE feed. Confirm the visible event order is queued → started → source prepared → copy generating → legal checking → completed, or a terminal failed event. Confirm the legacy Marketing Workspace route/controller remains unavailable.

## 6. Failure logs to collect

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs --since=20m backend ai-server postgres > R6A_services.log
docker compose -f compose.yaml -f compose.e2e.yaml exec postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 10;"'
```

Also capture the failing request's `X-Request-Id`, `X-Correlation-Id`, content ID, TaskRun ID, Job Event sequence, HTTP status/error code, and sanitized response body. Do not collect prompts, Provider bodies, authorization headers, secrets, or full user/legal input.

## 7. Next-stage condition

Proceed only when V13 is applied, backend and AI start cleanly, all six endpoint behaviors pass, generated/user/finalized revision origins are distinct, stale detection works, Job Events reach one terminal state and replay correctly, and the Provider smoke returns the closed schema. Do not start the next stage automatically.
