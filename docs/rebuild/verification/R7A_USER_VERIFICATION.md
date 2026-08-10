# R7A User Verification

Run from repository root unless a command changes directory.

## Commands and success criteria

```powershell
git status --short
git diff --check
cd backend
.\gradlew.bat clean test postgresTest --no-daemon
cd ..\ai
.\.venv\Scripts\python.exe -m pytest
cd ..\frontEnd
npm run lint
npm run test:baseline
npm run build
cd ..
docker compose config
docker compose down -v
docker compose build backend ai-server frontend
docker compose up -d
docker compose ps
```

Success means no diff whitespace errors; backend unit/integration/PostgreSQL tests pass; AI tests
pass; frontend lint/baseline/build pass; Compose config resolves; all rebuilt services become healthy;
and Flyway applies only `V1__new_pipeline_baseline.sql` to a new PostgreSQL database.

After startup, run static exposure checks:

```powershell
rg -n "LegacyPipelineSurface|app\.legacy-pipeline|ProjectStage" backend/src/main ai/app frontEnd/src
rg -n "features/(journey|conversational-idea|concept-workboard|feasibility|legal-review|personas|report|structured-plan|validation|financial|marketing-workspace)|projectWorkflowModel|/journey" frontEnd/src
rg -n "IDEA_INTERPRETATION|IDEA_CONVERSATION_TURN|QUICK_ASSESSMENT|DETAILED_ANALYSIS|PERSONA_CARD_GENERATION|PERSONA_INTERVIEW|INTERVIEW_SYNTHESIS|MARKETING_COMPARISON|FINAL_REPORT_GENERATION" backend/src/main ai/app
```

Success means all three commands return no active-code matches.

## Browser checks

- Signup/login and project create/list/detail work.
- Project shell exposes only overview, Idea Brief, Concept Factory, comparison/selection, market,
  planning/business module, marketing, and settings.
- Direct requests to former Journey/document/feasibility/persona/validation/report URLs show Not Found
  or the new shell fallback and never render a legacy page.
- Admin project list/detail show common project fields and new module/run state only.
- Admin settings contain registration and maintenance controls only.
- Idea Brief → Concept Factory → selection → market handoff → planning → marketing navigation loads.

## Database reset

Required. R7A is a clean-baseline cutover and does not support upgrading a database that contains the
old Flyway history. `docker compose down -v` removes local Compose data volumes and is destructive;
export anything needed before running it.

## Services requiring rebuild

Rebuild `backend`, `ai`, and `frontend`. PostgreSQL must start with a new volume. Rebuild MinIO only
if its image/config changed locally; its stored objects should also be considered disposable for this reset.

## Logs to collect on failure

```powershell
docker compose logs --no-color postgres backend ai-server frontend > r7a-compose.log
cd backend
.\gradlew.bat test postgresTest --stacktrace --info > ..\r7a-gradle.log
cd ..\ai
.\.venv\Scripts\python.exe -m pytest -vv > ..\r7a-ai-pytest.log
```

Also collect the failing request/response, `X-Request-Id`, `X-Correlation-Id`, taskRunId/attemptId,
browser console output, and the first Flyway SQL error with statement/table name.

## Condition to proceed to R7B

Proceed only after clean-DB Flyway startup, all commands above, service health, browser exposure
checks, and static searches pass. If any gate fails, keep R7A open and attach the listed logs; do not
repair by restoring a Legacy controller/route/task type.
