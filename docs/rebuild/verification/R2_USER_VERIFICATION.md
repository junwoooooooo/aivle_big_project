# R2 Integrated User Verification

## 1. Backend compile and targeted tests

From `backend`:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.*"
.\gradlew.bat postgresTest --tests "com.aivle.backend.postgres.PostgreSqlBaselineMigrationTests"
```

Success: compile and tests report `BUILD SUCCESSFUL`; Flyway applies through V7; Idea Brief tables, constraints, repositories, ownership, idempotency, immutable confirmation, Worker failure boundary, and TaskRun linkage pass.

## 2. AI targeted tests and schema gate

From `ai`:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_idea_brief_schema.py tests/test_internal_task_type_alignment.py -q
```

Success: all tests pass. Provider schema objects have non-empty typed properties, `additionalProperties: false`, no provider DTO `Any`, strict integers, and bounded enums/arrays.

## 3. Frontend lint, build, and focused tests

From `frontEnd`:

```powershell
npm.cmd run test:run -- src/features/idea-intake/model/ideaIntakeModel.test.js src/features/idea-intake/components/QuestionCard.test.jsx src/features/idea-intake/hooks/useIdeaIntake.test.jsx
npm.cmd run lint
npm.cmd run build
```

Success: focused tests, full lint, and Vite production build pass without unresolved imports or warnings promoted to errors.

## 4. Database initialization

- Destructive reset: **No**.
- Required migration: Flyway V7 must be applied after V6.
- Verify `idea_briefs`, `idea_brief_fields`, `idea_questions`, `idea_answers`, and `idea_brief_attachments` exist.
- Verify a confirmed snapshot cannot be overwritten and a later edit creates a higher-sequence Draft referencing it.

## 5. Docker rebuild

For baked images, rebuild exactly:

```powershell
docker compose build backend ai-server frontend
docker compose up -d postgres backend ai-server frontend
```

No PostgreSQL/MinIO image rebuild is required. Confirm backend, AI health, Flyway, and frontend startup logs are clean.

## 6. Actual Provider smoke

From `ai`, with real provider configuration:

```powershell
.\.venv\Scripts\python.exe -m app.tools.idea_brief_provider_smoke
```

Success: safe `SUCCEEDED` output; strict schema accepted; readiness returned; no raw provider body or secret printed.

## 7. Browser end-to-end

Use an owned project at `/app/projects/<PROJECT_ID>/idea`:

- Empty overview produces linked Error Summary.
- Valid input sends `POST .../derive`, returns `DERIVING` and `activeJobId`.
- Timeline receives, in increasing sequence, expected events:
  - `job.idea.queued`
  - `job.idea.started`
  - `job.idea.extracting`
  - `job.idea.questions.preparing`
  - `job.idea.brief.preparing`
  - terminal `job.idea.completed` or `job.idea.failed`
- AI returns 2–4 Question Cards when input is incomplete; no chat bubbles appear.
- Answers persist through `POST .../answers`; terminal query opens Review.
- Field edits persist through `PATCH .../fields`.
- Confirm uses `POST .../confirm`, returns `CONFIRMED`, and preserves immutable snapshot/hash.
- Refresh during Running restores Brief, activeJobId, event replay, and SSE connection.
- Refresh at Needs Input restores questions and prior answers.
- Disable SSE: bounded reconnect transitions to polling with the last sequence and no duplicate events.
- Force provider/schema/network failure: Brief and TaskRun terminate as Failed; safe failed event appears; retry is possible without a stuck RUNNING state.
- A second user cannot read events, Brief, task ID, questions, or snapshot.
- No legacy Journey, conversational workspace, or Conversation API request appears.

Repeat at 1280+, 768×1024, and 390×844; verify keyboard-only flow, visible focus, sticky action, aria-live, 200% zoom, and reduced motion.

## 8. Expected API state

`GET /api/v3/projects/<PROJECT_ID>/idea-brief` must always return the current canonical view with `briefId`, status, fields, questions, readiness, `activeJobId`, `confirmedSnapshotId`, and `updatedAt`. Events are signals only; after every terminal event the frontend must re-query this endpoint.

## 9. Failure logs

```powershell
docker compose logs --tail=300 backend
docker compose logs --tail=300 ai-server
docker compose logs --tail=200 frontend
docker compose logs --tail=200 postgres
```

Collect request/event IDs, event sequences, safe status/error codes, TaskRun/Brief states, Flyway output, browser console and redacted Network entries. Exclude authorization, keys, cookies, raw prompts/provider bodies, full user input, stack traces from user-visible logs, and attachment contents.

## 10. R3 gate

R3 may start only when backend compile/tests, Idea Brief PostgreSQL target, AI schema tests, actual Provider smoke, frontend lint/build, Docker services, complete browser flow, refresh restoration, SSE-to-poll fallback, ownership, immutable confirmation, failure terminalization, and legacy absence all pass. The only accepted R3 input is the confirmed Idea Brief snapshot ID/hash; Conversation data must not be used.
