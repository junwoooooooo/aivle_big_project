# R5B User Verification

## Commands and success criteria

```powershell
git diff --check
Set-Location backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.planning.PlanningPatchAndSnapshotTests" --tests "com.aivle.backend.pipeline.planning.MeaningfulPlanningLabelTests" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests"
Set-Location ..\frontEnd
npm.cmd run test:run -- src/features/business-persona-integration/pages/BusinessPersonaIntegrationPage.test.jsx
npx.cmd eslint src/features/planning-revision src/features/business-persona-integration src/features/market-integration/pages/MarketIntegrationPage.jsx src/app/routing/AppRouter.jsx src/app/project-shell/ProjectModulePages.jsx
```

Success: diff check is silent, Java compiles, all selected backend tests pass, the external Shell test reports 1 passing test, and ESLint exits 0.

## Database and Docker

- Database reset: not required for a valid V11 database.
- Flyway V12 must apply successfully and preserve/backfill any prior non-PENDING R5A decisions.
- Rebuild required: `backend` and `frontend` only. PostgreSQL must be running; AI/external module images do not need rebuilding.

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml up -d --build backend frontend
docker compose -f compose.yaml -f compose.e2e.yaml exec postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "select version, success from flyway_schema_history where version='12';"
```

Success: V12 is successful, Hibernate validation passes, all four planning tables exist, proposal decisions are unique, and Snapshot hash/sequence/parent constraints are active.

## API and browser checks

1. Open `/app/projects/<projectId>/market` with a current completed R5A result.
2. Confirm the five user labels: 선택한 원안, 시장분석 제안, 의미 기반 시장분석 반영안, 최종 확정 기획, 이전 기획.
3. Decide every proposal through `/api/v3/projects/{projectId}/planning/change-proposals/{proposalId}/decisions`; verify partial adoption requires and applies the exact edited value.
4. Confirm the applied preview is deterministic on refresh and rejected proposals do not change it.
5. Finalize through `/planning/finalize`; repeat once and verify the same finalized Snapshot is returned rather than creating another round.
6. Verify Snapshot JSON matches `finalized-planning-snapshot-v1`, the stored/recomputed hash matches, sequence/parent are internal metadata, and the label is semantic.
7. Open `/app/projects/<projectId>/business-persona-test`. Verify the exact title/description, BM analysis, financial analysis, and Persona response areas.
8. Before adapters are connected, all three areas remain `NOT_CONNECTED`; no fake result appears.
9. Prepare both Handoffs and verify `BUSINESS_FINANCIAL` and `PERSONA_RESPONSE_TEST` receive the same finalized Snapshot ID/body/hash.
10. Verify the warning says results are not actual market probability and cannot automatically change final planning.
11. Create a newer finalized Snapshot from a later selected/market input and verify earlier external Runs become `STALE` but remain visible.
12. Check keyboard use, screen reader order, 390×844, 768×1024, 1280+, 200% zoom, reduced motion, and long Korean content.

## Logs on failure

```powershell
docker compose -f compose.yaml -f compose.e2e.yaml logs --since 30m backend frontend postgres
```

Collect request ID, project ID, selected/market/finalized Snapshot IDs and hashes, proposal and decision IDs, sequence/parent, Handoff/Run IDs and module, Flyway V12 row, failed constraint, safe backend error, and browser console/network evidence. Do not collect JWTs, internal keys, prompts, Provider bodies, or unnecessary business content.

## Next-stage condition

R5B passes only when deterministic application, exact partial values, immutable finalized hashing/history, V12 migration, shared finalized Handoffs, truthful statuses/staleness, probability warning, and responsive/accessibility checks all pass. Stop after integrated R5 verification; do not start R6 automatically.
