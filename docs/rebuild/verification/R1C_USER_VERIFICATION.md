# R1C User Verification — Database Foundation and Frontend/API Integration

## Preconditions

- Repository: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Branch: `rebuild/new-pipeline-v1`
- Java 17 and Node/npm are available.
- Existing frontend dependencies are sufficient; **no new npm package was added**. Run `npm.cmd install` only if `frontEnd\node_modules` is absent or the lockfile environment has not been installed.

## Targeted checks

From `backend`:

```powershell
.\gradlew.bat test --tests com.aivle.backend.pipeline.module.NewPipelineFoundationMigrationTests
.\gradlew.bat compileJava
```

Success: the named static migration test passes and compile reports `BUILD SUCCESSFUL`.

From `frontEnd`:

```powershell
npm.cmd run test:run -- src/app/module-status/projectModuleModel.test.js
```

Success: the model tests pass, including API enum/route normalization and safe handling of unknown modules.

## Clean PostgreSQL migration check

This is a Testcontainers/PostgreSQL gate and was not run by Codex:

```powershell
cd backend
.\gradlew.bat postgresTest --tests com.aivle.backend.postgres.PostgreSqlBaselineMigrationTests
```

Success: V1–V6 apply and validate on an empty schema, V1–V5 upgrade counts are correct, and the V5-to-V6 case creates all four pipeline tables.

## Docker and API check

R1C requires rebuilding **backend and frontend**. PostgreSQL, MinIO, and AI images need not be rebuilt, although Compose starts dependencies.

```powershell
cd C:\Users\seewo\Desktop\big_proj_01\new_3
docker compose build backend frontend
docker compose up -d
docker compose ps
```

Success: backend and frontend are healthy and `http://localhost:3000` is reachable. With an authenticated owned project, the browser Network panel shows `GET /api/v3/projects/{id}/modules` returning six entries.

If that request fails, the project title, module navigation, main content, and settings must remain usable. Only the status area may show an error, and Retry must issue a new request.

## Database initialization and table check

An existing database does **not** require reset because V6 is additive. For clean-chain verification, use the Testcontainers command above or a disposable isolated Compose project; do not erase the normal project volume merely to verify R1C.

After Compose starts, using the configured database name/user:

```powershell
docker compose exec postgres psql -U aivle -d aivle -c "select version, success from flyway_schema_history order by installed_rank;"
docker compose exec postgres psql -U aivle -d aivle -c "\dt pipeline_module_runs"
docker compose exec postgres psql -U aivle -d aivle -c "\dt module_handoffs"
docker compose exec postgres psql -U aivle -d aivle -c "\dt module_results"
docker compose exec postgres psql -U aivle -d aivle -c "\dt planning_snapshots"
```

Success: Flyway V6 is successful and each table exists. Substitute `POSTGRES_USER`/`POSTGRES_DB` if overridden.

## Browser checks

- Open each of the eight routes listed in `R1_USER_VERIFICATION.md` for an owned project.
- Confirm live module labels/statuses agree with the `/api/v3` response.
- Simulate an API failure using browser request blocking or by stopping backend; confirm the shell survives and Retry is visible.
- Restore backend and confirm Retry clears the local status error.
- On desktop and mobile widths, verify keyboard navigation, `aria-current`, selector, previous/next links, and settings access.

## Logs to collect on failure

```powershell
docker compose ps
docker compose logs --tail=300 backend
docker compose logs --tail=150 frontend
docker compose logs --tail=200 postgres
```

Also collect the exact Gradle/npm command, sanitized HTTP status/body and `X-Request-Id`, browser console/Network errors, Flyway failure version, and test report XML under `backend\build\test-results`. Do not collect passwords, JWTs, provider keys, or authorization headers.

## Next-stage condition

R1C is accepted only when the targeted static migration test, backend compile, model test, clean PostgreSQL V1–V6 test, live status fetch/error containment, and four-table inspection all pass. Then complete the consolidated R1 gate; do not start R2 automatically.
