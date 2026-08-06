# R4B User Verification — Selection Snapshot and Market Integration Shell

## 1. Commands to run

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests"
.\gradlew.bat compileJava
cd ..\frontEnd
npm.cmd test -- --run src/features/concept-selection/components/SelectionConfirmation.test.jsx
npm.cmd exec eslint -- src/features/concept-selection src/features/market-integration src/app/routing/AppRouter.jsx
npm.cmd run build
```

Success criteria:

- The three backend Snapshot/idempotency/Schema contract tests pass.
- Backend compilation and frontend targeted test/lint pass.
- The frontend production build has no unresolved route, JSX, hook, or CSS errors.

Codex ran the targeted tests, targeted lint, and Java compilation. Codex did not run the frontend production build.

## 2. PostgreSQL and API verification

```powershell
cd ..
docker compose up -d postgres
docker compose build backend frontend
docker compose up -d backend frontend
docker compose logs backend --since=10m
```

Success criteria:

- Flyway V10 applies exactly once.
- Hibernate validates all four new tables and constraints.
- Repeating the same selection request while it remains current returns the same selection/Snapshot.
- Changing the concept or reason creates a new selection and incremented Snapshot with a parent reference; the previous row remains queryable internally and is no longer current.
- Repeating the same Handoff input returns the same Handoff and Module Run.
- A Handoff for a new current Snapshot creates a new Run without overwriting the old Run.
- The market input JSON contains exactly the eight Schema properties and has contract `selected-concept-market-input-v1`.
- The Run remains `NOT_CONNECTED`; no result or completion is fabricated.

Suggested requests, using the environment's normal authenticated session or approved local test headers:

```text
POST /api/v3/projects/{projectId}/concept-selections
{"conceptId":"{publishedConceptId}","selectionReason":"선택 판단 근거"}

GET /api/v3/projects/{projectId}/concept-selections/current

POST /api/v3/projects/{projectId}/module-handoffs
{"module":"MARKET_ANALYSIS","inputSnapshotId":"{snapshotId}","requestedOperation":"START_MARKET_ANALYSIS"}

GET /api/v3/projects/{projectId}/module-runs
GET /api/v3/projects/{projectId}/module-runs/{runId}
```

## 3. Browser verification

1. Open the compare page, choose 2–5 concepts, mark one preferred candidate, and enter a selection reason.
2. Confirm selection; verify the current Selection and market-navigation action appear.
3. Confirm again with identical input and verify no duplicate current Selection/Snapshot is created.
4. Change the preferred concept or reason and confirm a new immutable Snapshot is created.
5. Open `/app/projects/{projectId}/market` with no Selection: the page opens and only the Handoff action is unavailable; navigation back to selection is shown.
6. With a Selection, verify selected concept, Snapshot ID/hash, selection reason, readiness, external state, and delivery manifest.
7. Prepare the market Handoff twice and verify the same Handoff/Run is shown.
8. Confirm the visible state says `시장분석 모듈 연결 준비 중`/`연결 준비 중`, never completed.
9. After creating a new Selection, verify the prior Run remains in history and is shown as `STALE`/previous-selection input.
10. Verify keyboard-only operation, focus indication, screen-reader order, 390×844 and 768×1024 layouts, 200% zoom, reduced motion, and long hash/reason wrapping.

## 4. Database initialization

V10 is required. Existing databases should be migrated by Flyway; a destructive reset is not required. A fresh local database will receive V1–V10 normally.

Do not manually seed Selection/Handoff rows. Create a confirmed Idea Brief and completed five-concept R3 run, then use the public R4 APIs so ownership, hashes, Snapshot sequence, and idempotency constraints are exercised.

## 5. Docker services to rebuild

- Rebuild: `backend`, `frontend`.
- No R4B-specific rebuild: `ai-server`, `postgres` image.
- PostgreSQL must be running so V10 can apply; do not wipe its volume unless a separate clean-database test is intended.

## 6. Failure logs to collect

```powershell
docker compose ps
docker compose logs postgres --since=30m
docker compose logs backend --since=30m
docker compose logs frontend --since=30m
```

Collect Flyway history, SQL constraint name, project/concept/selection/Snapshot/Handoff/Run identifiers, request ID, safe error code, input Snapshot ID/hash, effective status, and browser console/network failure. Redact selection reason/business text when not needed.

Never collect authentication headers, tokens, secrets, prompts, Provider bodies, attachment contents, or legal source text.

## 7. Next-stage condition

R4B is accepted only after V10, targeted/build gates, Selection/Snapshot immutability and idempotency, exact Schema fixture, Handoff idempotency, Run preservation/staleness, `NOT_CONNECTED` truthfulness, open-page/action-gate behavior, responsive layout, and accessibility all pass. Do not automatically continue.
