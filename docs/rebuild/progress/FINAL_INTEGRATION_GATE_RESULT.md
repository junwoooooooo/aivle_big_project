# Final Integration Gate Result

## [1] Authority

- Gate update: 2026-08-17 (Asia/Seoul)
- Branch: `full`
- E2E realignment start HEAD: `2f769033b62b08b60754ba66586ddbcbb2177266`
- Six-stage authority patch start HEAD / `origin/full`: `a1edd5ea264578c53cb4b61a1525bce289abd54e`
- Start tracked worktree: clean
- Commit/push/branch change: none
- Current worktree: uncommitted six-stage Journey authority restoration and evidence updates

### Current Journey authority correction

The previous Gate notes incorrectly treated an eight-stage UI regression as canonical.
The active top-level product taxonomy is now restored to six stages: 사업 기획, 사업 검증,
출시 준비, 가상 인터뷰, 마케팅 전략, 최종 보고서. Current Market Interview and Twin
Panel Survey functionality remains independent, but both modules are grouped under the
single top-level 가상 인터뷰 stage.

## [2] Current E2E contract audit

The Docker scripts had drifted behind Active Surface Cleanup. They created legacy
`AnalysisJob` work through removed `/api/v1/.../ai-tasks` and legacy Marketing
routes, treated job IDs as numbers, and asserted obsolete result/artifact tables.
The product was not changed to satisfy those scripts.

| Old harness dependency | Current authority |
|---|---|
| `/api/v1/projects/{id}/ai-tasks/smoke` | E2E-profile-only `/internal/e2e/projects/{id}/task-runs` command seam |
| `/api/v1/projects/{id}/ai-tasks/artifact-smoke` | E2E seam using `TaskRunService`, `TaskResult`, and `ProjectEvidenceArtifactService` |
| `/api/v1/jobs/{longId}` | `/api/v2/projects/{id}/task-runs/{stringId}` |
| legacy artifact result download | `/api/v3/projects/{id}/evidence-artifacts/{artifactId}/download` |
| legacy Marketing generate/rerun/versions | removed from generic infrastructure E2E; no concept-authority bypass |
| `analysis_jobs` | `task_runs` and `task_attempts` |
| `ai_task_results` | `task_results` with `ADOPTED` / `REJECTED` state |
| `ai_task_artifacts` | `project_evidence_artifacts` |
| `marketing_content_versions` | not used by the generic infrastructure harness |

The seam is annotated with `@Profile("e2e")`, lives only under `/internal/e2e`,
requires the existing authenticated project ownership context, and creates ordinary
current `TaskRun` records. It does not add a legacy public product route or a new
orchestration authority.

## [3] Normal Docker E2E realignment

Implemented checks:

- frontend `/healthz`, backend Actuator, AI liveness/readiness, and a frontend-proxied backend readiness request
- signup and project ownership through intentionally active endpoints
- asynchronous current TaskRun creation, polling, and string/UUID ID handling
- canonical TaskResult adoption and result retrieval
- same-key/same-input idempotent replay without duplicate execution
- current evidence artifact persistence and download
- current `/api/v3/.../recent-jobs` projection
- preservation of an earlier successful result after a later run
- provider-free deterministic execution under `.env.e2e.example`

The readiness race is addressed by polling a meaningful request through the frontend
reverse proxy to the backend. No fixed startup delay was added.

## [4] Failure Docker E2E realignment

All requested scenarios now start current TaskRuns and assert current persistence:

- `ai-down`: AI readiness dependency is actually stopped before the E2E task starts
- `minio-down`: current artifact storage is invoked while MinIO is actually stopped
- `malformed`: rejected TaskResult history exists and no canonical result is adopted
- `checksum`: `HASH_MISMATCH` is retained as rejected history and no result is adopted
- `timeout`: current lease/deadline recovery reaches `TIMED_OUT` with one attempt
- `stale`: late completion is rejected and cannot replace terminal/current authority

Each scenario also verifies that the previous successful TaskResult remains available,
failed work has no `ADOPTED` TaskResult, and public TaskRun output does not expose an
internal token/header. Failure scenarios are isolated on disposable volumes.

## [5] Regression guard

`scripts/verify-pipeline-cutover.mjs` now fails if active Docker E2E scripts restore:

- removed `/api/v1/.../ai-tasks` or `/api/v1/jobs` paths
- removed legacy Marketing paths
- legacy job/result/artifact/version tables
- numeric casts for Job/TaskRun IDs

It also asserts the E2E controller/service profile boundary and the use of current
TaskRun, TaskResult, and evidence-artifact authorities.

## [6] Focused test matrix

| Command | Result | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| `node scripts/verify-pipeline-cutover.mjs` | PASS | static assertions | 0 | 0 |
| PowerShell AST parse for both Docker E2E scripts | PASS | 2 scripts | 0 | 0 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example -FrontendPort 13001 -BackendPort 18080 -AiServerPort 18000 -PostgresPort 15432 -MinioPort 19000 -MinioConsolePort 19001 -PreflightOnly` | PASS | port preflight | 0 | 0 |
| `.\backend\gradlew.bat -p backend test --tests com.aivle.backend.taskrun.e2e.E2eTaskRunServiceTests --tests com.aivle.backend.pipeline.module.ActiveSurfaceCleanupTests --no-daemon --console=plain` | PASS | 5 | 0 | 0 |
| `git diff --check` | PASS | n/a | 0 | 0 |

The first sandboxed Gradle start could not download the wrapper. The same focused
command was run with approved network access; two compile defects in the new seam
were fixed, then the failed focused command was rerun and passed in 19 seconds.

## [7] Docker matrix

### Six-stage authority patch verification

| Command | Result | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| `node scripts/verify-pipeline-cutover.mjs` | PASS | static assertions | 0 | 0 |
| focused Journey/frontend Vitest command | PASS | 59 | 0 | 0 |
| focused `ProjectServicePresentationTests` Gradle command | PASS | 3 | 0 | 0 |
| `npm run lint` | PASS | error 0 | 0 | n/a |
| `npm run test:baseline` | PASS | 679 | 6 explicitly allowed failures; 0 unexpected | 0 |
| `npm run build` | PASS | production bundle | 0 | n/a |
| `git diff --check` | PASS | n/a | 0 | 0 |

The production build emitted a non-blocking chunk-size warning. No paid provider was called.

### User-observed authority

| Check | Status | Evidence |
|---|---|---|
| Normal stack boot/build | PASS | user-observed Docker run |
| PostgreSQL / MinIO / AI / Backend / Frontend health | PASS | all services healthy; `minio-init` exited 0 |
| Frontend `/healthz` on port 13000 | PASS | HTTP 200 |
| Clean Flyway V1 through V41 | PASS | exactly 40 successful migrations |
| Duplicate successful migration versions | PASS | zero rows |
| V41 uniqueness | PASS | exactly one successful `market interview profile panel` row |

These findings supersede the earlier statement that Docker installation, port 3000,
or Flyway V41 was the user-environment blocker. They were not reinvestigated.

### Current functional harness

| Check | Status | Evidence boundary |
|---|---|---|
| Old normal harness | FAIL (historical) | reached removed `/api/v1/.../ai-tasks/smoke` |
| Old failure harness | INVALID / NOT RUN | failed before reaching `ai-down` fault injection |
| Realigned normal harness | PASS | user independently observed the current TaskRun harness success |
| Realigned failure scenarios | PASS | user independently observed all six injected scenarios reaching PASS |

User runtime evidence now confirms the realigned harness after the earlier Codex
environment limitation: normal E2E passed, and `ai-down`, `minio-down`, `malformed`,
`checksum`, `timeout`, and `stale` each reached their fault injection and passed.

## [8] Real provider and manual browser

- Real provider: NOT RUN
- Browser/manual journey: NOT RUN in this patch
- Provider calls introduced by this harness: 0
- Docker in Codex environment: 0 runtime executions

## [9] Remaining risks

1. The broader Final Integration Gate still retains its separately documented paid-provider and manual-browser checkpoints.
2. The restored six-stage taxonomy still requires the focused/frontend verification recorded by the current patch before this document can represent a complete Gate.

## [10] Verdict

**E2E HARNESS REALIGNMENT = IMPLEMENTED**

**USER DOCKER RUNTIME EVIDENCE = PASS**

The Final Integration Gate is not declared PASS and `PRODUCTION READY` is not declared.
