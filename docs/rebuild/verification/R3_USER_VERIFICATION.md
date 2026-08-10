# R3 Integrated User Verification

## 1. Backend and AI targeted gates

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*"
cd ..\ai
.\.venv\Scripts\python.exe -m pytest -q tests/test_concept_factory_schema.py tests/test_internal_task_type_alignment.py
```

Success: domain bounds, mixed Slot failure, Worker, SQL contract, closed Provider schemas, forbidden fields, Evidence refusal, and registry alignment pass.

## 2. Related PostgreSQL gate

```powershell
cd ..
docker compose up -d postgres
docker compose build backend
docker compose up -d backend
docker compose logs backend --since=10m
```

Success: V8 and V9 apply cleanly, Hibernate validates, project/snapshot constraints hold, and one run persists exactly five Slots plus bounded Attempts, shared context, Evidence, assessments, and links.

## 3. Frontend lint, test, and build

```powershell
cd frontEnd
npm.cmd test -- --run src/features/concept-factory/model/conceptFactoryModel.test.js src/features/concept-factory/components/ConceptSlotCard.test.jsx
npm.cmd exec eslint -- src/features/concept-factory src/features/job-center src/app/routing/AppRouter.jsx src/app/layouts/AppShell.jsx
npm.cmd run build
```

Success: targeted tests/lint and production build pass. Codex did not run the production build.

## 4. Provider smoke and Docker

```powershell
cd ..
docker compose build ai-server backend frontend
docker compose up -d postgres ai-server backend frontend
docker compose exec ai-server python -m app.tools.concept_factory_provider_smoke
docker compose ps
```

Success: all services are healthy; Candidate, Legal Review, and Redesign strict schemas work against the configured Provider; official Evidence indexes resolve only to server-supplied references.

## 5. End-to-end five-Slot execution

Verify in the browser:

1. Confirm an Idea Brief and start Concept Factory.
2. Five fixed variation Slots appear and progress independently.
3. Force one transient failure and confirm one same-Slot retry; exhaust it and confirm replacement.
4. Force schema invalid and confirm one Repair then replacement.
5. Force `REDESIGNABLE` and confirm one redesign maximum.
6. Force `REJECTED` and confirm safe rejection plus replacement; total replacement rounds never exceed two and inspected candidates never exceed fifteen.
7. Force one isolated Slot failure and confirm other eligible Slot commits remain preserved; parent reaches terminal failure rather than RUNNING.
8. Force `NEEDS_FACTS` and verify Needs Input without any draft detail leak.
9. Navigate away, return, and refresh during processing; run/job/Slots/counters restore.
10. Break SSE, verify reconnect with Last-Event-ID replay, then bounded Polling fallback. Events remain deduplicated and sequence ordered.
11. Before completion, no concept detail is visible. After all gates pass, all five concepts appear simultaneously.
12. Confirm every concept shares the snapshot hash, is non-stale/non-duplicate, and has `IMPLEMENTABLE` or `IMPLEMENTABLE_WITH_CONTROLS` status.
13. Confirm legal controls, qualifications/partners, disclosures, prohibited variants, remaining facts, and official Evidence are visible without legal source text or unsafe Provider content.

## 6. Accessibility and responsive gate

Verify 390×844, 768×1024, and 1280+, keyboard only, screen reader, 200% zoom, reduced motion, terminal alert announcement, Slot accessible labels, and Timeline/Evidence accordions.

## 7. Logs on failure

```powershell
docker compose ps
docker compose logs postgres --since=30m
docker compose logs ai-server --since=30m
docker compose logs backend --since=30m
docker compose logs frontend --since=30m
```

Collect correlation/project/run/job/Slot/Attempt identifiers, safe error classification, event sequence/Last-Event-ID, snapshot hashes, reveal-gate reason, and Flyway history. Never collect secrets, authorization headers, raw Provider bodies, prompts, user raw input, or legal text.

## R4 Gate

R4 may start only after all targeted, PostgreSQL, build, Provider, Docker, five-Slot, isolation, refresh, SSE, simultaneous reveal, legal detail, bounded-loop, responsive, and accessibility gates pass. R4 must consume only the five public eligible concepts from the completed snapshot set.
