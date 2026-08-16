# Final Runtime Acceptance Plan

## 1. Purpose and authority

This is the single execution plan for final acceptance of the new six-stage pipeline. It does not
replace the governing product contracts under `docs/rebuild`; it turns them into an ordered runtime
gate. Previous RESULT documents prove only what was implemented or locally checked at that time.

Acceptance is valid only when every gate below is executed against the same branch, HEAD, clean
database, provider configuration, and evidence set. Job Events are signals; the Idea Brief, Concept,
Selection, Module, Planning, and Marketing Query APIs are the state authority.

No gate may restore or exercise the legacy Journey, conversational workspace, legacy persona or
interview workspace, feasibility module, or legacy marketing workspace. Market, BM/financial, and
Persona algorithms remain external; this repository validates only immutable Snapshot/Handoff/Result
contracts and their shells.

## 2. Strict execution and stop rule

Run gates in order. At the first failed command or unmet success criterion:

1. stop the current gate immediately;
2. do not mark later gates passed and do not continue with a partially healthy stack;
3. record the exact gate/check ID from the checklist;
4. collect only the redacted evidence in section 13;
5. correct the cause, recreate the affected state if necessary, and restart at the failed gate;
6. if source, environment, provider model, database, or branch/HEAD changes, repeat all earlier gates
   whose evidence can be invalidated.

Do not change TaskRun or domain states directly in PostgreSQL. Do not substitute mock, fixture, or
manually edited frontend state for a provider/runtime result.

## 3. Prerequisites and evidence workspace

Required locally:

- PowerShell, Git, Docker Desktop with Compose, JDK 17, Node/npm, and the repository Gradle wrapper;
- `ai/.venv` with repository Python dependencies;
- `.env` populated from `.env.example` with real non-production provider credentials;
- `AI_FIXTURE_MODE=false` and `AI_CONCEPT_TEST_FAILURE_INJECTION=false` for success-path acceptance;
- a valid `MOLEG_API_KEY`, AI provider/model configuration, JWT secret, PostgreSQL password, MinIO
  password, and matching internal service token;
- two test users for ownership checks;
- permission to destroy the Compose volumes used by this repository.

Create a local evidence directory outside the repository. Raw logs may contain operational data and
must not be attached or shared until reviewed and redacted.

```powershell
Set-Location C:\Users\seewo\Desktop\big_proj_01\new_3
$AcceptanceId = Get-Date -Format 'yyyyMMdd-HHmmss'
$EvidenceRoot = Join-Path $env:TEMP "new-pipeline-acceptance-$AcceptanceId"
New-Item -ItemType Directory -Path $EvidenceRoot | Out-Null
git branch --show-current | Set-Content (Join-Path $EvidenceRoot 'branch.txt')
git rev-parse HEAD | Set-Content (Join-Path $EvidenceRoot 'head.txt')
```

Never copy these values into evidence: Authorization headers, JWTs, API keys, passwords, prompts,
provider request/response bodies, raw user input, attachment contents, full legal text, or stack
traces returned to the browser. Use synthetic, non-sensitive browser input.

## 4. Gate 1 — Static contract

### Commands

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --check

rg -n "LegacyPipelineSurface|app\.legacy-pipeline|ProjectStage|VITE_CONVERSATIONAL_VALIDATION_WORKSPACE" backend/src/main ai/app frontEnd/src frontEnd/Dockerfile
rg -n "^\s*(import|export\s+.*\s+from|from)\s+.*(journey|conversation|persona-workspace|interview-workspace|market-response|feasibility|marketing-workspace)" backend/src/main ai/app frontEnd/src

node scripts/verify-pipeline-cutover.mjs
Push-Location frontEnd
npm.cmd run test:run -- src/app/routing/AppRouter.cutover.test.js
Pop-Location

docker compose config --quiet
npx.cmd --yes @redocly/cli@1.34.5 lint docs/api/openapi.yaml

$JsonFiles = Get-ChildItem ai,backend/src/main/resources,docs/api,docs/rebuild -Recurse -File -Filter *.json
foreach ($File in $JsonFiles) {
  $null = Get-Content -LiteralPath $File.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
}
```

The first two `rg` commands use exit code 1 to mean zero matches. Review any match; test names or
explicit redirect compatibility may be acceptable only when they do not import or render a legacy
surface. Do not suppress an active import.

### Success criteria

- branch is `rebuild/new-pipeline-v1` and HEAD is recorded;
- worktree changes, if any, are reviewed and belong to the acceptance candidate;
- `git diff --check`, JSON parsing, Compose parsing, and OpenAPI 3.1 lint pass;
- legacy active imports are zero;
- route test proves Idea, Concept, Selection, Market, external shell, and Marketing direct routes;
- static limit check reports 5 eligible / 15 inspected / 2 replacement rounds.

### Stop here if

Any active legacy import/route exists, a route points to a Placeholder for an implemented module,
configuration does not resolve, or JSON/YAML/OpenAPI parsing fails.

## 5. Gate 2 — Backend compilation, contracts, PostgreSQL

Run each batch separately so the failing boundary is unambiguous.

```powershell
Push-Location backend
.\gradlew.bat compileJava --no-daemon

.\gradlew.bat test --no-daemon `
  --tests 'com.aivle.backend.config.AsyncExecutionConfigurationTests' `
  --tests 'com.aivle.backend.jobevent.JobEventStreamServiceTests' `
  --tests 'com.aivle.backend.jobevent.JobEventControllerTests' `
  --tests 'com.aivle.backend.jobevent.JobEventApiIntegrationTests' `
  --tests 'com.aivle.backend.taskrun.api.ProjectJobControllerTests' `
  --tests 'com.aivle.backend.taskrun.service.ProjectJobQueryServiceTests' `
  --tests 'com.aivle.backend.taskrun.TaskRunServiceIntegrationTests' `
  --tests 'com.aivle.backend.pipeline.module.NewPipelineFoundationMigrationTests'

.\gradlew.bat test --no-daemon `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefFieldCatalogTests' `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefReadinessTests' `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefCanonicalizationIntegrationTests' `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefDerivationCommitServiceTests' `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefSnapshotTests'

.\gradlew.bat test --no-daemon `
  --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryFiveSlotTests' `
  --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryLimitTests' `
  --tests 'com.aivle.backend.pipeline.concept.ConceptFactoryStateMachineTests' `
  --tests 'com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests' `
  --tests 'com.aivle.backend.pipeline.legal.LegalEvidenceHardeningTests'

.\gradlew.bat test --no-daemon `
  --tests 'com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests' `
  --tests 'com.aivle.backend.pipeline.integration.MarketResultSchemaTests' `
  --tests 'com.aivle.backend.pipeline.integration.PlanningChangeProposalTests' `
  --tests 'com.aivle.backend.pipeline.integration.SnapshotStalenessTests' `
  --tests 'com.aivle.backend.pipeline.planning.PlanningPatchAndSnapshotTests' `
  --tests 'com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests' `
  --tests 'com.aivle.backend.pipeline.marketing.MarketingContentContractsTests'

.\gradlew.bat test --no-daemon `
  --tests 'com.aivle.backend.pipeline.idea.IdeaBriefDerivationWorkerTests' `
  --tests 'com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests' `
  --tests 'com.aivle.backend.pipeline.marketing.worker.MarketingContentWorkerTests' `
  --tests 'com.aivle.backend.taskrun.TaskRunDomainTests'

.\gradlew.bat postgresTest --no-daemon `
  --tests 'com.aivle.backend.postgres.PostgreSqlBaselineMigrationTests'
Pop-Location
```

### Success criteria

- every command reports `BUILD SUCCESSFUL`;
- scheduling context contains a multi-thread `taskScheduler` and all three workers plus heartbeat;
- Idea canonicalization/readiness/snapshot tests pass;
- Concept bounded replacement, provider classification, successful-slot preservation, NEEDS_INPUT,
  permanent failure, and parent TaskRun terminalization pass;
- Marketing lifecycle and failure terminalization pass;
- PostgreSQL baseline applies successfully through Testcontainers.

### Stop here if

Compilation fails, a worker path can leave a claim RUNNING, a terminal TaskRun/domain state diverges,
or the PostgreSQL baseline test fails. A Docker/Testcontainers environment failure is not a product
pass; fix the environment and rerun this gate.

## 6. Gate 3 — AI schemas, task alignment, real provider smoke

Provider smoke is mandatory and must use real provider calls with fixture mode disabled.

```powershell
Get-Content .env -Encoding UTF8 | ForEach-Object {
  $Line = $_.Trim()
  if ($Line -and -not $Line.StartsWith('#')) {
    $Pair = $Line -split '=', 2
    if ($Pair.Count -eq 2) {
      [Environment]::SetEnvironmentVariable($Pair[0].Trim(), $Pair[1].Trim().Trim('"').Trim("'"), 'Process')
    }
  }
}
if ($env:AI_FIXTURE_MODE -ne 'false') { throw 'AI_FIXTURE_MODE must be false' }
if (-not $env:AI_API_KEY -or -not $env:AI_MODEL -or -not $env:MOLEG_API_KEY) { throw 'Provider and MOLEG configuration is incomplete' }

Push-Location ai
.\.venv\Scripts\python.exe -m compileall -q app tests
.\.venv\Scripts\python.exe -m pytest -q `
  tests/test_internal_task_type_alignment.py `
  tests/test_idea_brief_schema.py `
  tests/test_concept_factory_schema.py `
  tests/test_concept_legal_evidence.py `
  tests/test_legal_source_contract.py `
  tests/test_regulatory_boundary_contract.py `
  tests/test_marketing_content_contract.py

.\.venv\Scripts\python.exe -m app.tools.idea_brief_provider_smoke
.\.venv\Scripts\python.exe -m app.tools.concept_factory_provider_smoke
.\.venv\Scripts\python.exe -m app.tools.marketing_content_provider_smoke
Pop-Location
```

### Success criteria

- compileall and all named pytest files exit 0;
- exactly five provider task types align with the internal execution registry;
- strict schemas reject extra/untyped fields and legal findings require valid evidence references;
- Idea smoke prints only status/readiness metadata;
- Concept smoke uses official evidence, returns at least one evidence item, and completes redesign;
- Marketing smoke prints only contract/boolean/count metadata;
- no smoke output contains a secret, prompt, complete generated body, provider body, or full law text.

### Stop here if

Any smoke is skipped, fixture-backed, schema-invalid, missing official evidence, or leaks prohibited
data. A provider quota/network/configuration failure must be recorded as such and rerun; it is not an
acceptance pass.

## 7. Gate 4 — Frontend lint, tests, production build

```powershell
Push-Location frontEnd
npm.cmd run lint
npm.cmd run test:run -- `
  src/test/App.test.jsx `
  src/app/routing/AppRouter.cutover.test.js `
  src/app/module-status/projectModuleModel.test.js `
  src/shared/async-events/authenticatedSseClient.test.js `
  src/shared/async-events/jobEventsReducer.test.js `
  src/shared/async-events/useJobEvents.test.jsx `
  src/shared/async-events/JobTimeline.test.jsx `
  src/shared/async-events/jobEventMessages.test.js `
  src/features/job-center/useProjectJobs.test.jsx `
  src/features/job-center/JobCenter.test.jsx `
  src/features/idea-intake/hooks/useIdeaIntake.test.jsx `
  src/features/idea-intake/components/IdeaBriefReview.test.jsx `
  src/features/concept-factory/model/conceptFactoryModel.test.js `
  src/features/concept-factory/components/ConceptReveal.test.jsx `
  src/features/concept-selection/components/ConceptComparisonView.test.jsx `
  src/features/business-persona-integration/pages/BusinessPersonaIntegrationPage.test.jsx `
  src/features/marketing-content/hooks/useMarketingContent.test.jsx `
  src/features/marketing-content/hooks/useMarketingGeneration.test.jsx `
  src/features/marketing-content/components/MarketingCopyEditor.test.jsx
npm.cmd run build
Pop-Location
```

### Success criteria

Lint, every named targeted test, and the production Vite build exit 0. Tests confirm route access,
server-first job restoration, Event dedupe/replay/fallback, terminal Query refresh, five-concept
reveal, external shell boundaries, Marketing refresh/revision behavior, and no fabricated progress.

### Stop here if

Lint/test/build fails or the built route graph exposes a legacy/Placeholder screen for an implemented
module.

## 8. Gate 5 — Destructive clean database and Docker startup

This is destructive. Confirm the Compose volumes contain no data that must be retained. The command
removes PostgreSQL and MinIO data for this Compose project.

```powershell
docker compose down -v
docker compose build backend ai-server frontend
docker compose up -d
docker compose ps
```

Wait until `postgres`, `minio`, `ai-server`, `backend`, and `frontend` are healthy and `minio-init`
has completed successfully. Then verify Flyway:

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT installed_rank,version,description,script,success FROM flyway_schema_history ORDER BY installed_rank;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT COUNT(*) AS application_table_count FROM information_schema.tables WHERE table_schema=''public'' AND table_name<>''flyway_schema_history'';"'
```

### Success criteria

- all required services are healthy;
- Flyway history contains exactly one successful migration row;
- version is `1` and script is `V1__new_pipeline_baseline.sql`;
- startup logs contain no Hibernate validation, migration, task-type, or schema mismatch;
- no legacy migration or legacy application table is applied.

### Stop here if

Any service is unhealthy, Flyway contains more than the single V1 baseline, or backend startup shows
a schema/contract mismatch. Do not continue by editing the database.

## 9. Gate 6 — Browser end-to-end success path

Use synthetic data and browser DevTools with Preserve log enabled. Record only IDs and redacted
request metadata. First complete signup/login and project creation against the plain Compose stack.

### 9.1 Account, project, and Idea Brief

1. Sign up test owner A, log out, and log in again.
2. Create a project and record `projectId`.
3. Open `/app/projects/<PROJECT_ID>/idea` directly.
4. Enter a synthetic idea, submit AI organization, and observe real Job Events.
5. Refresh while DERIVING and verify the same job/brief restores.
6. Answer follow-up questions; confirm answers update canonical target fields.
7. Verify summary, provenance, missing fields, contradictions, readiness, and editable decision state.
8. Confirm only when readiness permits; record the immutable Idea Brief snapshot ID/hash.

Success: the Brief leaves DERIVING, bounded clarification never exceeds round 2, refresh preserves AI
metadata, and confirmation creates an immutable snapshot.

### 9.2 Five concepts, legal evidence, compare and select

1. Start concept generation from the confirmed Brief.
2. Verify exactly five Slot cards and real per-Slot events: generated, origin validation, legal
   validation, optional redesign/replacement, and terminal status.
3. Refresh while running and verify the same run/job restores.
4. Verify candidate details remain hidden until all five eligible concepts are ready.
5. Confirm five concepts appear together; rejected/internal drafts never appear as eligible.
6. Open each legal report and verify reviewed activity, controls, qualifications/partners,
   disclosures, prohibited variants, unknown facts, law name, article, effective date, official link,
   expert-review recommendation, and precheck limitation.
7. Confirm no full legal text is exposed in the page or Event stream.
8. Compare 2–5 concepts, select one, and record the SelectedConceptSnapshot ID/hash.

Success: exactly five distinct eligible concepts are published together, every material legal finding
has an official Evidence reference, and selection creates an immutable snapshot.

### 9.3 Market Handoff, development result, planning finalization

Create the Handoff from the Market page and verify its input snapshot matches the selected concept.
The local result stub is intentionally profile-limited. After the plain-stack health/Flyway evidence
has been captured, recreate only for browser integration with the E2E overlay:

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d --force-recreate backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml ps backend frontend
```

With owner A's bearer token and project ID from the browser:

```powershell
$ProjectId = '<OWNED_PROJECT_ID>'
$Token = '<OWNER_A_BEARER_TOKEN>'
$Headers = @{ Authorization = "Bearer $Token"; 'X-Request-Id' = [guid]::NewGuid().ToString() }
Invoke-RestMethod -Method Post -Headers $Headers -Uri "http://localhost:3000/api/v3/projects/$ProjectId/market-results/fixture/stub"
```

The stub is contract verification only and must never be described as real market analysis. Refresh
the Market page, review every proposal, choose adopt/partially adopt/reject as appropriate, and create
the FinalizedPlanningSnapshot. Record its ID/hash and verify later changes create new immutable state
rather than mutating it.

### 9.4 External BM/financial + Persona shell

Open `/business-persona-test` directly. Verify the page remains accessible, uses only the finalized
planning snapshot for Handoffs, displays `NOT_CONNECTED` until an external module responds, and does
not claim actual customer probability or implement external algorithms.

### 9.5 Marketing lifecycle

1. Open `/marketing` directly; BM/financial and Persona results must not be prerequisites.
2. Create Marketing content from the current FinalizedPlanningSnapshot.
3. Verify real queued/started/source prepared/copy generating/legal checking Events.
4. Refresh during QUEUED/RUNNING and verify Detail/Event replay restores the same `activeJobId`.
5. Verify Copy, HTML/CSS Preview, and Image Brief text; do not expect PNG/JPEG/banner artifacts.
6. Edit copy, save a user revision, regenerate a new AI revision, and inspect meaningful revision
   labels/history.
7. Verify prohibited claims block save/finalization and required disclosures remain present.
8. Finalize the chosen revision and download it.
9. Open the downloaded `.txt` and verify UTF-8 Korean, CTA, hashtags, Image Brief, and disclosures.

Success: content/TaskRun states agree, refresh uses durable server state, revisions are immutable,
finalization is persisted, and download is truthful to the text-only asset scope.

## 10. Gate 7 — Async, replay, restart, and terminal invariants

Perform these checks with separate disposable jobs so one fault does not invalidate another.

### Refresh and transport

- refresh during Idea, Concept, and Marketing; each restores from the Domain Query API;
- block only `/api/v2/jobs/<JOB_ID>/events` SSE in DevTools;
- after the 45-second inactivity watchdog and bounded retries, verify
  `/events?after=<sequence>` polling with exponential backoff;
- hide the tab and verify polling slows; receive a new Event and verify backoff resets;
- remove blocking and use `연결 재시도`;
- request replay explicitly:

```powershell
curl.exe -N --max-time 100 -H "Accept: text/event-stream" -H "Authorization: Bearer <TOKEN>" -H "Last-Event-ID: <LAST_SEQUENCE>" "http://localhost:3000/api/v2/jobs/<JOB_ID>/events"
```

Success: no duplicate sequence is rendered, terminal Event causes a Domain Query refresh, and 401/403
stops rather than falling back indefinitely.

### Queued restart recovery

Queue a fresh job, stop backend before it claims if possible, and restart:

```powershell
docker compose stop backend
docker compose start backend
docker compose ps backend
```

Success: the normal worker claim path increments `attempt_count`; no startup-only state mutation is
used, and the job reaches a terminal/domain-consistent state.

### Retryable dependency failure

Start a disposable Concept run, stop `ai-server` during a provider call, then restore it:

```powershell
docker compose stop ai-server
docker compose start ai-server
docker compose ps ai-server backend
```

Success: failure is classified retryable where the contract permits, retry/replacement stays within
bounds, and the parent never remains RUNNING after the path is exhausted.

### Permanent provider failure

Use a disposable acceptance environment only. Preserve the real key in memory without printing it,
recreate `ai-server` with an intentionally invalid credential, run one disposable job, then restore
the real key and recreate the service:

```powershell
$RealProviderKey = $env:AI_API_KEY
$env:AI_API_KEY = 'intentionally-invalid-for-acceptance'
docker compose up -d --force-recreate ai-server
# Start one disposable provider-backed job in the browser and wait for terminal failure.
$env:AI_API_KEY = $RealProviderKey
docker compose up -d --force-recreate ai-server
Remove-Variable RealProviderKey
```

Do not capture the environment or key. Success: the disposable job terminates FAILED with a safe
permanent/configuration classification, retryable false, matching domain state, and no raw provider
response. If the provider classifies this condition differently, record the observed safe code and
stop; do not relabel it manually.

### NEEDS_INPUT and no-stuck audit

Use an intentionally incomplete synthetic Idea or legally ambiguous Concept input to produce a
natural `NEEDS_INPUT`; do not edit DB state. Then audit:

```powershell
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,task_type,state,attempt_count,retryable,current_attempt_id,last_error_code,updated_at FROM task_runs ORDER BY created_at;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,task_run_id,attempt_number,state,retryable,normalized_error_code,normalized_error_reason,heartbeat_at,finished_at FROM task_attempts ORDER BY created_at;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT job_id,sequence,COUNT(*) FROM job_events GROUP BY job_id,sequence HAVING COUNT(*)>1;"'
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,task_type,state,updated_at FROM task_runs WHERE state=''RUNNING'' AND updated_at < CURRENT_TIMESTAMP - INTERVAL ''10 minutes'';"'
```

Success: NEEDS_INPUT is terminal/non-claimable, duplicate query returns zero rows, all disposable jobs
end consistently, and the stale RUNNING query returns zero rows after configured deadlines/recovery.

## 11. Gate 8 — Responsive UI and accessibility

Run the complete success path at these viewport conditions, not only the landing page:

- desktop width 1280px or greater;
- tablet width 768px;
- mobile 390×844;
- browser zoom 200% at a desktop viewport;
- OS/browser reduced-motion enabled.

For each, verify no clipped primary action, horizontal loss of content, inaccessible Job Center,
unreachable legal Evidence, or obscured sticky Marketing action. Complete keyboard-only navigation
through auth, project creation, Idea questions/review, Concept cards, comparison/selection, Market
proposals, and Marketing editing/download. Verify logical focus order, visible focus, focus return for
dialogs/sheets, no keyboard trap, meaningful labels, and focus moves to actionable error summaries.

With a screen reader or accessibility tree, verify `aria-live` announcements for Idea state, Slot
progress, Job Timeline, Job Center terminal notice, and Marketing completion are useful and not
repeated by duplicate Events. Reduced motion must remove nonessential animation without hiding state.

Stop if any required action or state is unavailable at a listed viewport/input mode.

## 12. Gate 9 — Security and data minimization

Create owner B and attempt owner A's Project, Module, Job, Event, Idea, Concept, Selection, Planning,
and Marketing Query APIs. Expected result is 404 or the established ownership-denied response with
no resource metadata. Test both SSE and polling ownership.

```powershell
$OwnerAProjectId = '<OWNER_A_PROJECT_ID>'
$OwnerAJobId = '<OWNER_A_JOB_ID>'
$OwnerBToken = '<OWNER_B_BEARER_TOKEN>'
$OwnerBHeaders = @{ Authorization = "Bearer $OwnerBToken" }
$ProtectedUris = @(
  "http://localhost:3000/api/v1/projects/$OwnerAProjectId",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/modules",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/active-jobs",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/idea-brief",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/concept-factory-runs/current",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/concepts",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/concept-selections/current",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/module-runs",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/planning/current",
  "http://localhost:3000/api/v3/projects/$OwnerAProjectId/marketing-contents",
  "http://localhost:3000/api/v2/jobs/$OwnerAJobId/events?after=0"
)
foreach ($Uri in $ProtectedUris) {
  try {
    $Response = Invoke-WebRequest -UseBasicParsing -ErrorAction Stop -Headers $OwnerBHeaders -Uri $Uri
    $Status = [int]$Response.StatusCode
    $Length = $Response.RawContentLength
  } catch {
    $Status = [int]$_.Exception.Response.StatusCode
    $Length = 0
  }
  [pscustomobject]@{ Uri = $Uri; Status = $Status; Length = $Length }
}
curl.exe -sS -o NUL -w "%{http_code}`n" --max-time 10 -H "Accept: text/event-stream" -H "Authorization: Bearer $OwnerBToken" "http://localhost:3000/api/v2/jobs/$OwnerAJobId/events"
```

Every result must be the established ownership-denied status and must not contain owner A's metadata.
Do not save or share the token-containing command history as evidence.

Inspect browser Network responses, Job Event JSON/SSE, downloadable content, and redacted service
logs. Confirm none exposes:

- Authorization/JWT, provider/API/internal keys, passwords, or secrets;
- prompts, provider request/response bodies, stack traces, or internal exception messages;
- complete raw user input or attachment contents in Job Events;
- full legal text in Job Events or user report;
- evidence indices outside the supplied pack or unofficial/homepage-only Evidence as implementable.

Also verify fixture endpoints are absent on the plain production-like `postgres` profile and present
only after the explicit local/test/e2e overlay. Stop on any ownership bypass or prohibited disclosure.

## 13. Failure evidence and redaction procedure

### Required identifiers

For every failure record:

- gate/check ID, UTC/local timestamp, branch and HEAD;
- request route/method/status plus `requestId` (body redacted);
- `correlationId`, `taskRunId`, `taskAttemptId`, project ID, subject type/ID;
- safe Job Event sequence and message/technical codes;
- TaskRun/attempt state and retryable flag;
- authoritative domain state and source snapshot ID/hash;
- viewport/browser and exact reproduction step;
- first failing command and exit code.

### Collection commands

```powershell
docker compose ps --all | Out-File (Join-Path $EvidenceRoot 'compose-ps.txt')
docker compose logs --no-color --since=30m backend | Out-File (Join-Path $EvidenceRoot 'backend.raw.log')
docker compose logs --no-color --since=30m ai-server | Out-File (Join-Path $EvidenceRoot 'ai-server.raw.log')
docker compose logs --no-color --since=30m postgres | Out-File (Join-Path $EvidenceRoot 'postgres.raw.log')
docker compose logs --no-color --since=30m frontend | Out-File (Join-Path $EvidenceRoot 'frontend.raw.log')

docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,project_id,task_type,subject_type,subject_id,state,correlation_id,retryable,attempt_count,current_attempt_id,last_error_code,started_at,finished_at,updated_at FROM task_runs WHERE id=''<TASK_RUN_ID>'';"' | Out-File (Join-Path $EvidenceRoot 'task-run.txt')
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,task_run_id,attempt_number,state,retryable,normalized_error_code,normalized_error_reason,claimed_at,heartbeat_at,finished_at FROM task_attempts WHERE task_run_id=''<TASK_RUN_ID>'' ORDER BY attempt_number;"' | Out-File (Join-Path $EvidenceRoot 'task-attempts.txt')
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT job_id,task_run_id,sequence,event_type,status,message_key,technical_code,occurred_at FROM job_events WHERE job_id=''<JOB_ID>'' ORDER BY sequence;"' | Out-File (Join-Path $EvidenceRoot 'safe-events.txt')
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,project_id,status,active_task_run_id,confirmed_snapshot_id,snapshot_hash,readiness_score,clarification_round,updated_at FROM idea_briefs WHERE project_id=<PROJECT_ID> ORDER BY brief_sequence;"' | Out-File (Join-Path $EvidenceRoot 'idea-domain.txt')
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,project_id,status,source_idea_brief_snapshot_id,source_snapshot_hash,replacement_rounds,inspected_candidate_count,updated_at FROM concept_factory_runs WHERE project_id=<PROJECT_ID> ORDER BY created_at;"' | Out-File (Join-Path $EvidenceRoot 'concept-domain.txt')
docker compose exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "SELECT id,project_id,planning_snapshot_id,source_snapshot_hash,status,task_run_id,current_revision_number,finalized_revision_number,updated_at FROM pipeline_marketing_contents WHERE project_id=<PROJECT_ID> ORDER BY created_at;"' | Out-File (Join-Path $EvidenceRoot 'marketing-domain.txt')
```

Use only the domain query relevant to the failing module and replace placeholders before execution;
the queries intentionally exclude raw JSON/text payload columns. Export a HAR only after removing
cookies, Authorization, request bodies, response
bodies containing user content, and query values that are secrets.

Before sharing any file, search it and manually review every hit:

```powershell
$SensitivePattern = 'Authorization|Bearer\s+|api[_-]?key|token|password|prompt|provider.?body|input_snapshot_json|result_json|bounded_official_text'
Get-ChildItem $EvidenceRoot -File | Select-String -Pattern $SensitivePattern -CaseSensitive:$false
```

Keep raw logs local and access-restricted. Create explicitly named `.redacted` copies; never overwrite
raw evidence in a way that hides what was removed. Do not put acceptance evidence or credentials in
Git.

## 14. Final acceptance decision

Acceptance is `PASS` only when every checklist item is checked, all failure records are resolved and
rerun, the same candidate HEAD is recorded, and the final no-stuck/security audits pass. Record:

- branch and HEAD;
- clean DB startup time and single Flyway V1 row;
- provider/model identifiers without credentials;
- project, snapshot, run, TaskRun, and final content IDs;
- browser/viewport matrix;
- evidence directory location;
- approver and timestamp.

Any unchecked or “not run” mandatory item yields `NOT ACCEPTED`, never a conditional success.
