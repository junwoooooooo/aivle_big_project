# R2B User Verification — Idea Brief Domain and API

## Preconditions

- Repository root: `C:\Users\seewo\Desktop\big_proj_01\new_3`
- Expected branch: `rebuild/new-pipeline-v1`
- Java 17 and the repository Gradle wrapper are available.
- PostgreSQL is available with a disposable or backed-up development database.
- Prepare two authenticated users and a project owned by the first user. Record `<OWNER_TOKEN>`, `<OTHER_TOKEN>`, and `<PROJECT_ID>`.

## Commands to run

From `backend`, run the focused R2B tests:

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldInvariantTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefSnapshotTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefControllerTests"
```

Expected time: about 10–60 seconds after Gradle dependencies are cached.

Success: Gradle reports `BUILD SUCCESSFUL`; all three selected test classes pass.

Run the user-owned compile/full backend gates:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

Success: both commands report `BUILD SUCCESSFUL` with no compilation or test failures.

Run the targeted PostgreSQL baseline/migration test if its Testcontainers prerequisites are available:

```powershell
.\gradlew.bat postgresTest --tests "com.aivle.backend.postgres.PostgreSqlBaselineMigrationTests"
```

Success: PostgreSQL starts, Flyway applies through `V7__idea_brief_domain.sql`, and the selected migration test passes without checksum, DDL, foreign-key, or constraint errors.

## Database verification

Database initialization required: **Flyway migration required; destructive reset not required.**

- Existing development DB: start the updated backend once and confirm Flyway applies V7 after V6.
- Fresh development DB: Flyway should apply V1 through V7 in order.
- Do not manually create the R2B tables and do not delete existing data to apply this additive migration.

After migration, verify these tables exist:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'idea_briefs', 'idea_brief_fields', 'idea_questions',
    'idea_answers', 'idea_brief_attachments'
  )
ORDER BY table_name;
```

Success: all five table names are returned.

## API verification

Start the required infrastructure and backend using the repository's normal local profile. The examples below assume the backend is at `http://localhost:8080`.

Create/derive a Draft as the owner:

```powershell
$headers = @{
  Authorization = "Bearer <OWNER_TOKEN>"
  "Idempotency-Key" = "r2b-derive-001"
  "X-Request-Id" = "r2b-manual-001"
}
$body = @{
  overview = "지역 소상공인의 재고 폐기를 줄이는 서비스"
  fields = @(
    @{ fieldKey = "problem"; value = "재고 폐기"; decisionState = "LOCKED" },
    @{ fieldKey = "targetCustomers"; value = "동네 식당"; decisionState = "PREFERRED" }
  )
  attachmentFileIds = @()
} | ConvertTo-Json -Depth 5
$derive = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v3/projects/<PROJECT_ID>/idea-brief/derive" -Headers $headers -ContentType "application/json" -Body $body
$derive.data | ConvertTo-Json -Depth 8
```

Success:

- HTTP status is 202.
- `briefId` is non-empty, `status` is `DERIVING`, and `activeJobId` is non-empty.
- A matching `task_runs` row exists with type `IDEA_BRIEF_DERIVATION` and state `QUEUED`.
- No AI provider request is made and no Worker consumes the task in R2B.
- Response includes fields, questions, readiness, confirmedSnapshotId, and updatedAt members.

Replay the exact same request with the same `Idempotency-Key`.

Success: the same `briefId` and `activeJobId` are returned and no duplicate TaskRun is created.

Change the payload while reusing `r2b-derive-001`.

Success: HTTP 409 with `IDEMPOTENCY_CONFLICT`.

Fetch as the owner:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/v3/projects/<PROJECT_ID>/idea-brief" -Headers @{ Authorization = "Bearer <OWNER_TOKEN>" }
```

Success: the current owner-scoped Idea Brief is returned.

Repeat the GET and any command using `<OTHER_TOKEN>`.

Success: the foreign user receives the repository's safe not-found/access response and cannot infer or mutate the owner's fields, questions, attachments, task ID, or snapshot ID.

## Confirmed snapshot verification

R2B does not fabricate R2C AI questions/results. Use a controlled test fixture or database setup to place a Draft in `READY_FOR_REVIEW` with zero unanswered questions, then call confirm with a new key:

```powershell
$confirmHeaders = @{
  Authorization = "Bearer <OWNER_TOKEN>"
  "Idempotency-Key" = "r2b-confirm-001"
}
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v3/projects/<PROJECT_ID>/idea-brief/confirm" -Headers $confirmHeaders -ContentType "application/json" -Body '{"expectedVersion":null}'
```

Success:

- Status is `CONFIRMED`.
- `confirmedSnapshotId` equals `briefId`.
- `snapshot_hash` begins with `sha256:` and remains unchanged.
- Replaying the same confirm returns the same snapshot.
- A field patch with a new Idempotency-Key creates a higher-sequence `DRAFT` whose `parent_brief_id` and `confirmed_snapshot_id` reference the confirmed row.
- The original confirmed row and its `idea_brief_fields` values are unchanged.
- Confirm with unanswered questions, a non-ready status, or a stale `expectedVersion` is rejected.

## Browser checks

Open `/app/projects/<PROJECT_ID>/idea` while signed in and confirm:

- The R2A form remains the only active Idea UI; no Journey or Conversation screen appears.
- R2B does not yet make the R2A page perform live derive/patch/answer/confirm calls.
- No unexpected background provider call or concept-generation navigation occurs.

Use browser developer tools or an API client to inspect R2B responses and verify internal stack traces, prompt/provider bodies, authorization values, and full attachment contents are not exposed.

## Docker rebuild

- Required image rebuild if using baked Compose images: **`backend` only**.
- Database image rebuild: **No**. The existing `postgres` service must be running so Flyway can apply V7.
- `frontend`, `ai-server`, `minio`, and `minio-init` do not require rebuilding for R2B.

From the repository root when a baked backend image is used:

```powershell
docker compose build backend
docker compose up -d postgres backend
```

## Logs to collect on failure

Collect:

```powershell
docker compose logs --tail=300 backend
docker compose logs --tail=200 postgres
```

Also collect:

- Output from the focused tests, `compileJava`, full backend test, and targeted postgresTest.
- Flyway schema-history rows for V6 and V7 and the exact migration error.
- Request method/path, safe request body, Idempotency-Key, X-Request-Id, HTTP status, and safe response body.
- Relevant `idea_briefs`, field/question/answer counts, and TaskRun state/ID with user-entered sensitive content redacted.
- Browser console/network errors if the active Idea page regresses.

Never collect bearer tokens, cookies, secrets, prompt/provider bodies, raw file contents, or stack traces in user-visible logs.

## Next-stage condition

R2C may begin only after focused tests, compile/full backend tests, V7 PostgreSQL migration, ownership isolation, idempotent derive replay/conflict behavior, queued TaskRun creation, confirm immutability, and post-confirm Draft forking pass. The accepted R2C continuation point is the `IDEA_BRIEF_DERIVATION` Worker and result-adoption path; R2B domain invariants and snapshot rows must not be replaced by Conversation entities.
