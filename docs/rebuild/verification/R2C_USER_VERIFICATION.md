# R2C User Verification

## Commands

Backend, from `backend`:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefDerivationWorkerTests"
```

AI, from `ai`:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py tests/test_internal_task_type_alignment.py -q
```

Frontend, from `frontEnd`:

```powershell
npm.cmd run test:run -- src/features/idea-intake/hooks/useIdeaIntake.test.jsx
npm.cmd run lint
npm.cmd run build
```

Success: every command exits 0; Gradle/Vite report successful completion and all selected tests pass.

## Provider smoke

Configure `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, optional `AI_BASE_URL`, and timeout settings, then from `ai` run:

```powershell
.\.venv\Scripts\python.exe -m app.tools.idea_brief_provider_smoke
```

Success: exits 0 and prints only a safe `SUCCEEDED` envelope with readiness. The returned provider document passes the strict schema without repair, extra properties, string numbers, or unknown enums.

## Browser and async checks

At `/app/projects/<PROJECT_ID>/idea`:

1. Enter an overview and optional fields; start AI organization.
2. Confirm Network shows one 202 derive request with an Idempotency-Key.
3. Confirm Job Timeline shows queued/running stages and SSE is primary.
4. Interrupt SSE and confirm bounded retries followed by `/api/v2/jobs/<JOB_ID>/events?after=<SEQUENCE>` polling.
5. On Needs Input, answer 2–4 Question Cards and verify Brief Review reloads from GET.
6. Edit fields and confirm; verify status `CONFIRMED` and immutable snapshot ID.
7. Refresh during Running and after Needs Input; current Brief, questions, activeJobId, replay cursor, and state must recover without duplicate TaskRuns.
8. Force a safe provider failure; status must become Failed, Timeline must receive `job.idea.failed`, and no RUNNING Brief/TaskRun may remain stuck.

Also verify keyboard operation, mobile sticky action, 200% zoom, aria-live, no legacy Journey/chat UI, and no secrets/raw prompt/provider body in events or responses.

## Database and Docker

- DB initialization: no destructive reset. Existing DB must have Flyway V7 from R2B; start backend so Flyway applies it if pending.
- Rebuild baked services: `backend`, `ai-server`, and `frontend`.
- Do not rebuild the PostgreSQL or MinIO images.

```powershell
docker compose build backend ai-server frontend
docker compose up -d postgres backend ai-server frontend
```

## Failure logs

```powershell
docker compose logs --tail=300 backend
docker compose logs --tail=300 ai-server
docker compose logs --tail=200 frontend
docker compose logs --tail=200 postgres
```

Collect safe TaskRun state, job/event ID and sequence, Brief status, request ID, HTTP status, and redacted response. Never collect tokens, raw prompts, provider bodies, full user input, or attachment contents.

## Next-stage condition

R2 integration must pass the full `R2_USER_VERIFICATION.md`; Provider smoke, PostgreSQL commit, SSE/poll recovery, refresh restoration, confirm immutability, and terminal failure behavior are mandatory before R3.
