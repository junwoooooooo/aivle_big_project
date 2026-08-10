# R1 User Verification — One-Time Integrated Gate

## Preconditions and installation

- Repository: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Branch: `rebuild/new-pipeline-v1`
- Java 17, Docker Compose, Node, and npm are available.
- No frontend dependency was added in R1. Run `npm.cmd install` only when `frontEnd\node_modules` is absent or not installed from the current lockfile.
- Keep `LEGACY_PIPELINE_ENABLED` unset or `false`.

## Local compile and test gate

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd
npm.cmd run lint
npm.cmd run build
npm.cmd run test:run -- src/app/module-status/projectModuleModel.test.js

cd ..\backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.LegacyPipelineSurfaceConditionTests
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.NewPipelineFoundationMigrationTests
.\gradlew.bat postgresTest --tests com.aivle.backend.postgres.PostgreSqlBaselineMigrationTests
```

Success: every command reports success. The PostgreSQL test must validate clean V1–V6 and upgrades, including V5-to-V6. Codex did not run the full lint, production build, or PostgreSQL/Testcontainers gate.

## Docker Compose gate

R1 changes require rebuilding **backend and frontend**. Database initialization is automatic through Flyway; an existing database does not require reset. Use a disposable clean DB/Testcontainers run for the clean-chain check instead of deleting the normal volume.

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3
docker compose build backend frontend
docker compose up -d
docker compose ps
```

Success: all required services become healthy, the frontend is available at `http://localhost:3000`, and backend startup reports successful Flyway V6 validation/migration.

## New eight-route browser gate

For an authenticated owned `<PROJECT_ID>`, open:

1. `/app/projects/<PROJECT_ID>/overview`
2. `/app/projects/<PROJECT_ID>/idea`
3. `/app/projects/<PROJECT_ID>/concepts`
4. `/app/projects/<PROJECT_ID>/concepts/compare`
5. `/app/projects/<PROJECT_ID>/market`
6. `/app/projects/<PROJECT_ID>/business-persona-test`
7. `/app/projects/<PROJECT_ID>/marketing`
8. `/app/projects/<PROJECT_ID>/settings`

Also open `/app/projects/<PROJECT_ID>` and confirm redirect to `/overview`.

Success: all pages are non-empty; title, status text/color, required input, and safe action render; no execution starts; the Network panel shows one authenticated `/api/v3/projects/<PROJECT_ID>/modules` request with six module entries.

## Legacy route cutover gate

Directly open representative old routes:

- `/app/projects/<PROJECT_ID>/legal`
- `/app/projects/<PROJECT_ID>/journey/concept`
- `/app/projects/<PROJECT_ID>/journey/concept-analysis`
- `/app/projects/<PROJECT_ID>/journey/concept-selection`
- `/app/projects/<PROJECT_ID>/journey/persona`
- `/app/projects/<PROJECT_ID>/journey/interview`
- `/app/projects/<PROJECT_ID>/journey/marketing`
- `/app/projects/<PROJECT_ID>/journey/final-report`
- `/app/projects/<PROJECT_ID>/plan`
- `/app/projects/<PROJECT_ID>/review`
- `/app/projects/<PROJECT_ID>/validate`
- `/app/projects/<PROJECT_ID>/report`

Success: each redirects to a new canonical route or Not Found and never renders Journey stepper, Plan/Review/Validate/Report, standalone legal stage, conversational workspace, Persona, Interview, or Final Report navigation/text.

## Desktop and mobile navigation gate

Desktop success:

- Fixed left navigation shows the six modules plus overview/settings in specified order.
- Current route has `aria-current`, keyboard focus is visible, and title sizes/hierarchy are clear.
- Project title, module title/status, task-center entry, and main content remain available.

Mobile success:

- Layout is single-column and does not show a compressed desktop sidebar.
- Current-step selector, previous/next navigation, and settings access work by touch and keyboard.
- No horizontal overflow or hidden primary action appears at common phone widths.

Block the modules request or stop backend temporarily. Success: only module status displays an error/retry state; shell navigation, project content, and settings do not crash. Restore backend and Retry successfully reloads states.

## API and legacy-controller gate

Authenticate using a normal owned project as described in `R1B_USER_VERIFICATION.md`, then verify:

- `GET /api/v3/projects/<PROJECT_ID>/modules` returns 200, the `ApiResponse` envelope, six ordered modules, required fields, and no `project.stage` dependency.
- Another user's project returns 404 `PROJECT_NOT_FOUND`.
- Representative document, Journey, validation, persona, financial/legal/feasibility, and marketing-workspace legacy URLs listed in `R1B_USER_VERIFICATION.md` return 404 by default.
- A 401 is not proof of deactivation; a 405 must be investigated as a potentially registered mapping.
- Auth, projects, files, TaskRun/JobEvent/audit, and admin APIs remain available as applicable.

## Database table gate

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "select version, success from flyway_schema_history order by installed_rank;"
docker compose exec postgres psql -U aivle -d aivle -c "\dt pipeline_module_runs"
docker compose exec postgres psql -U aivle -d aivle -c "\dt module_handoffs"
docker compose exec postgres psql -U aivle -d aivle -c "\dt module_results"
docker compose exec postgres psql -U aivle -d aivle -c "\dt planning_snapshots"
```

Success: V6 is recorded successful and all four tables exist. Substitute overridden `POSTGRES_USER`/`POSTGRES_DB` values. Existing legacy tables are expected to remain until R7.

## Logs to collect on failure

```powershell
docker compose ps
docker compose logs --tail=300 backend
docker compose logs --tail=200 frontend
docker compose logs --tail=200 postgres
```

Collect the exact failing command, Gradle/npm report, Flyway version/error, sanitized URL/status/body and `X-Request-Id`, browser console/Network output, viewport size, and screenshot. Never collect JWTs, passwords, provider keys, authorization headers, or sensitive payloads.

## R1 acceptance and next-stage condition

R1 passes only when all local commands, clean PostgreSQL migration, Docker health, eight routes, legacy UI/API non-exposure, desktop/mobile navigation, API-error containment, ownership behavior, retained infrastructure, and four-table checks succeed. Record evidence, stop, and authorize R2 separately. Do not proceed to R2 on any unresolved failure.
