# R3B User Verification

## Commands and success criteria

### AI targeted contracts

```powershell
cd ai
.\.venv\Scripts\python.exe -m pytest -q tests/test_concept_factory_schema.py tests/test_internal_task_type_alignment.py
```

Success: closed-schema lint, forbidden-field checks, official-Evidence refusal, and Java/Python task registry alignment all pass.

### Backend targeted contracts

```powershell
cd ..\backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*"
```

Success: compilation and Concept Factory tests pass, including mixed Slot failure isolation and redesign/replacement caps.

### PostgreSQL and Docker

```powershell
cd ..
docker compose up -d postgres
docker compose build ai-server backend
docker compose up -d ai-server backend
docker compose logs backend --since=10m
```

Success: Flyway applies V9, Hibernate validates, both services become healthy, and no TaskType or schema mismatch appears.

### Actual Provider smoke

```powershell
docker compose exec ai-server python -m app.tools.concept_factory_provider_smoke
```

Success: candidate, legal review, and redesign all return strict schemas; legal Evidence references resolve only to supplied official Evidence indexes. Output must not contain provider-created evidence IDs, legal source text, final user confirmation state, or traces.

## API, Event, and browser/network checks

1. Create a run from a confirmed Idea Brief and confirm HTTP 202, five Slots, and non-null `activeJobId`.
2. Observe SSE/replay or the job event query and confirm safe events such as `job.concept.run.queued`, slot started/generated/legal-validation, redesign/replacement where applicable, eligible, and terminal completed/failed/needs-input.
3. Verify Event params contain only bounded slot/count metadata and never prompts, Provider bodies, user raw input, legal text, secrets, stack traces, or raw rejection detail.
4. Confirm `GET /api/v3/projects/{projectId}/concepts` exposes nothing before `COMPLETED` and exactly five eligible concepts afterward.
5. Refresh during processing and confirm the current run, active job, Slot states, and event replay remain queryable. R3B does not add a new workboard UI, so use the existing shell/network inspector or authenticated API client.
6. Force one Slot to fail and verify other healthy Slots retain their committed state; the parent reaches a terminal state and never remains RUNNING.
7. Force `NEEDS_FACTS` and verify Run/Slot become `NEEDS_INPUT` without publishing draft detail.
8. Confirm user-facing copy says `공식 근거 기반 법률 구현 가능성 검토` and never `완벽한 법률검토`.

## DB initialization

- A normal V8 database migrates forward with V9; full reset is not required.
- Reset only a disposable environment if local schema drift prevents Flyway from applying V9.

## Docker rebuild scope

- Rebuild: `ai-server`, `backend`.
- No frontend rebuild is required for R3B backend/AI verification.
- PostgreSQL is started but not rebuilt.

## Failure logs to collect

```powershell
docker compose ps
docker compose logs ai-server --since=20m
docker compose logs backend --since=20m
docker compose logs postgres --since=20m
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*" --stacktrace
```

Also collect request/correlation ID, project ID, Concept Factory run ID, TaskRun ID, Slot number, Attempt phase/error classification, safe technical code, and Flyway version 9 history. Never collect authorization headers, secrets, Provider raw bodies, prompts, user raw input, or legal source text.

## Next-stage condition

Proceed only when AI targeted tests pass, V9 applies cleanly, Provider smoke proves strict schemas and official Evidence reference integrity, mixed Slot failures are isolated, bounded loops terminate, all TaskRuns reach terminal state, events are safe/replayable, and only five completed eligible concepts are public.
