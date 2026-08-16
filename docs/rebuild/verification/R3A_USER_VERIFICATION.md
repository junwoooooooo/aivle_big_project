# R3A User Verification

## Preconditions

- Branch is `rebuild/new-pipeline-v1`.
- A user-owned project has a current `CONFIRMED` Idea Brief with a non-null `sha256:` snapshot hash.
- Commands run from repository root unless a command changes directory.

## Commands and success criteria

### Backend compile and targeted unit contracts

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*"
```

Success: both commands report `BUILD SUCCESSFUL`; state-machine, five-slot, iteration-limit, and SQL-contract tests pass.

### PostgreSQL/Flyway targeted verification

Do not run the full `postgresTest` merely for R3A. Start the repository PostgreSQL and backend so Flyway applies V8:

```powershell
cd ..
docker compose up -d postgres
docker compose build backend
docker compose up -d backend
docker compose logs backend --since=10m
```

Success: backend startup completes, Flyway applies `V8__concept_factory_domain.sql`, and there are no migration, Hibernate mapping, or composite foreign-key errors.

Optional SQL inspection:

```powershell
docker compose exec postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "\dt concept*"
docker compose exec postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "\dt legal*"
```

Success: all nine R3A tables exist, including `concept_factory_runs`, `concept_slots`, `concepts`, `legal_context_packs`, and legal assessment/evidence tables.

## API and browser/network verification

With an authenticated session or bearer token, verify:

1. `POST /api/v3/projects/{projectId}/concept-factory-runs` with `{"ideaBriefSnapshotId":"<confirmed-id>"}` returns HTTP 202 and `QUEUED`.
2. `GET /api/v3/projects/{projectId}/concept-factory-runs/current` returns the same run and source snapshot hash.
3. `GET /api/v3/projects/{projectId}/concept-factory-runs/{runId}/slots` returns exactly five slots numbered 1–5 with the five variation focuses and `QUEUED` status.
4. Creating from a draft, stale, missing, or another project's Idea Brief is rejected without disclosing the foreign project.
5. A second create while the current run is active is rejected.
6. `GET /api/v3/projects/{projectId}/concepts` returns an empty `concepts` array before the run is `COMPLETED`; no draft title, summary, or legal detail appears.
7. `POST .../{runId}/retry` is rejected unless the run is `FAILED` or `NEEDS_INPUT`.
8. Confirm that no legacy Journey, Concept Workboard, legal-review route, or controller becomes newly visible. R3A intentionally adds no frontend workboard.

## DB initialization

- A full DB reset is not required for a normal V7 database; Flyway should migrate it forward with V8.
- Use a disposable DB reset only if local schema drift or a previously hand-created R3 table causes V8 to fail. Preserve required local data before resetting.

## Docker rebuild scope

- Required for container verification: `backend` only.
- No `ai-server` or `frontend` rebuild is required for R3A.
- PostgreSQL is started, not rebuilt.

## Failure logs to collect

```powershell
docker compose ps
docker compose logs backend --since=20m
docker compose logs postgres --since=20m
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*" --stacktrace
```

Also capture the failing HTTP method/path, status, response `code`, request ID, project ID, run ID, Idea Brief snapshot ID, and the Flyway schema-history row for version 8. Do not capture authorization headers or provider/user raw content.

## R3B progression gate

Proceed to R3B only when compile and targeted tests pass, V8 applies cleanly to PostgreSQL, create returns exactly five isolated slots, non-confirmed/foreign snapshots are rejected, draft concepts remain hidden, and retry guards behave as specified. R3B must begin with AI task/worker/Job Event integration and must not replace these R3A domain invariants.
