# R3C User Verification

## Commands

```powershell
cd frontEnd
npm.cmd test -- --run src/features/concept-factory/model/conceptFactoryModel.test.js src/features/concept-factory/components/ConceptSlotCard.test.jsx
npm.cmd exec eslint -- src/features/concept-factory src/features/job-center src/app/routing/AppRouter.jsx src/app/layouts/AppShell.jsx
npm.cmd run build
```

Success: four targeted tests pass, targeted lint is clean, and the production build succeeds without route/import errors. The build was not run by Codex.

```powershell
cd ..\backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*"
```

Success: API response expansion compiles and Concept Factory targeted tests remain green.

## Browser checks

1. Open `/app/projects/{projectId}/concepts`; confirm no legacy Workboard or placeholder appears.
2. Start from a confirmed Idea Brief and verify five Slots appear without draft title/summary.
3. Verify desktop 3+2, tablet two-column, and mobile one-column layouts at 1280+, 768×1024, and 390×844.
4. Navigate away and return, then hard refresh. Current run, five Slots, active job, counters, and replayed Timeline must restore.
5. Interrupt SSE and verify bounded reconnection then Polling fallback; duplicated replay events must appear once and remain sequence ordered.
6. Filter Timeline by each Slot and return to All.
7. Before completion, even an `ELIGIBLE` Slot displays only `컨셉 준비됨 · 법률검토 통과`.
8. At completion, all five details appear in one render. Altering any gate field in a test/stub must hide all five.
9. Confirm each revealed concept shows planning, operating structure, legal status, controls, partners/qualifications, disclosures, prohibited variants, Evidence, and remaining facts.
10. Verify Needs Input links to Idea Brief; Retry resumes eligible-preserving execution; terminal failures announce with `role=alert`.
11. Verify keyboard focus, visible focus, Timeline/Evidence accordion behavior, screen-reader labels/live updates, 200% zoom, and reduced motion.
12. Open the global Job Center and confirm the Concept Factory run is listed and links back correctly.

## DB initialization and Docker rebuild

- DB reset is not required for a normal V9 database.
- Rebuild `backend` and `frontend` for R3C Docker verification. Rebuild `ai-server` only when executing the integrated R3 Provider gate.
- PostgreSQL is started, not rebuilt.

## Failure logs

```powershell
docker compose ps
docker compose logs frontend --since=20m
docker compose logs backend --since=20m
cd frontEnd
npm.cmd test -- --run src/features/concept-factory/model/conceptFactoryModel.test.js src/features/concept-factory/components/ConceptSlotCard.test.jsx --reporter=verbose
```

Collect browser console/network logs, failing project/run/job IDs, Last-Event-ID/sequence, transport state, Slot states, snapshot hashes, legal statuses, and reveal-gate reasons. Do not collect authorization, Provider bodies, prompts, user raw input, or legal text.

## Next-stage condition

R3C passes when the Workboard restores reliably, Timeline replay/SSE/fallback works, failure UX is accessible, drafts never leak, and exactly five valid concepts reveal simultaneously. Then complete every integrated R3 gate before authorizing R4.
