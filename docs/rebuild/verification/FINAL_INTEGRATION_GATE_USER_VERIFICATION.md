# Final Integration Gate User Verification

## Current status

- Normal stack boot/build: **PASS — user observed**
- Service health: **PASS — user observed**
- Clean Flyway V1–V41: **PASS — user observed**
- V41 uniqueness: **PASS — user observed**
- Realigned normal functional E2E: **PASS — user observed**
- Realigned failure E2E: **PASS — user observed for all six scenarios**
- Real provider smoke: **NOT RUN**
- Browser acceptance: **NOT RUN**

Docker installation, the Windows port 3000 excluded range, migration collision, and
the current-TaskRun harness are not current blockers. The commands below are retained
as reproducible evidence instructions; they do not need to be repeated for this UX patch.

## 1. Normal current-pipeline Docker E2E

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example -FrontendPort 13001 -BackendPort 18080 -AiServerPort 18000 -PostgresPort 15432 -MinioPort 19000 -MinioConsolePort 19001
```

Expected terminal line:

```text
Docker E2E passed: currentTaskRun=..., replay=..., artifactTaskRun=..., artifact=...
```

This run must demonstrate:

- frontend reverse proxy and backend readiness
- signup/project ownership
- current string/UUID TaskRun lifecycle through `/api/v2`
- canonical TaskResult adoption
- same-key/same-input replay with one TaskRun ID
- current evidence artifact storage/download
- current project recent-job projection
- previous successful result preservation

If the command fails, return the first `Docker E2E failed:` line and the redacted
container diagnostics. Do not include environment secrets.

## 2. Current TaskRun failure/recovery E2E

Only after the normal command passes, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docker-failure-e2e.ps1 -EnvFile .env.e2e.example -Scenario all -FrontendPort 13001 -BackendPort 18080 -AiServerPort 18000 -PostgresPort 15432 -MinioPort 19000 -MinioConsolePort 19001
```

Required output:

```text
Failure scenario passed: ai-down
Failure scenario passed: minio-down
Failure scenario passed: malformed
Failure scenario passed: checksum
Failure scenario passed: timeout
Failure scenario passed: stale
All requested Docker failure scenarios passed.
```

Record each scenario separately as `PASS`, `FAIL`, or `NOT RUN`:

| Scenario | Required current-authority observation | Result |
|---|---|---|
| `ai-down` | AI service is stopped before injection; TaskRun fails retryably; no adopted result; prior result remains. | PASS — user observed |
| `minio-down` | MinIO is stopped before artifact write; no artifact metadata or adopted result is promoted. | PASS — user observed |
| `malformed` | TaskResult is `REJECTED`; TaskRun fails; no canonical result is promoted. | PASS — user observed |
| `checksum` | `HASH_MISMATCH` rejected history exists; no canonical result is promoted. | PASS — user observed |
| `timeout` | One attempt reaches `TIMED_OUT`; no silent replay or result promotion occurs. | PASS — user observed |
| `stale` | Late result is rejected and cannot supersede terminal/current authority. | PASS — user observed |

The script uses disposable E2E volumes. Do not point it at a development or production
Compose environment.

## 3. What changed in the harness

The scripts must no longer call or query:

- `/api/v1/projects/{id}/ai-tasks/smoke`
- `/api/v1/projects/{id}/ai-tasks/artifact-smoke`
- `/api/v1/jobs/{id}`
- legacy Marketing generate/rerun/version endpoints
- `analysis_jobs`, `ai_task_results`, `ai_task_artifacts`, or `marketing_content_versions`

They now use an authenticated, `e2e`-profile-only `/internal/e2e` command seam to create
ordinary current TaskRuns, and observe the public current `/api/v2` TaskRun plus `/api/v3`
job/artifact surfaces. The seam is not present in local or production profiles.

## 4. Broader Gate checkpoints

This patch does not approve or execute a paid provider run. It also does not replace
the six-stage browser acceptance. Retain the broader
Final Integration Gate statuses as follows until separately evidenced:

Current official Journey:

1. 사업 기획 — Idea, Concept
2. 사업 검증 — Market, Business Model
3. 출시 준비 — Technology, Operations, Finance
4. 가상 인터뷰 — 시장 인터뷰, 트윈 패널 조사
5. 마케팅 전략 — Marketing
6. 최종 보고서

| Checkpoint | Status |
|---|---|
| Paid 20-person Market Interview | NOT RUN — explicit approval required |
| Paid 80-person Market Interview | NOT RUN — separate approval required |
| Official six-stage browser journey | NOT RUN |
| Browser token/privacy inspection | NOT RUN |

Do not report `FINAL INTEGRATION GATE = PASS` or `PRODUCTION READY` from the E2E harness
alone.

## Evidence to return

Return:

1. the final normal E2E success/failure line;
2. each of the six failure scenario lines;
3. any first failing assertion and redacted relevant container log excerpt;
4. confirmation that no API key, service token, Authorization header, or raw provider response was copied into the evidence.
