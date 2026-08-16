# V2-10E2 — TechOps Proposal Async User Verification

## 1. Automated targeted checks

From the repository root:

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.techops.TechOpsControllerAsyncTests" --tests "com.aivle.backend.pipeline.techops.TechOpsServiceAsyncTests" --tests "com.aivle.backend.pipeline.techops.TechOpsProposalCompletionServiceTests" --tests "com.aivle.backend.pipeline.techops.TechOpsPreparationContractsTests" --tests "com.aivle.backend.pipeline.techops.TechOpsV2ContractTests"

cd ..\ai
.\.venv\Scripts\python.exe -m pytest tests/test_internal_task_type_alignment.py tests/test_tech_ops_proposal.py -q

cd ..\frontEnd
npm.cmd run test:run -- src/features/tech-ops/hooks/useTechOps.test.jsx src/features/tech-ops/pages/TechOpsPage.test.jsx src/features/tech-ops/model/techOpsModel.test.js
npx.cmd eslint src/features/tech-ops/api/techOpsApi.js src/features/tech-ops/hooks/useTechOps.js src/features/tech-ops/hooks/useTechOps.test.jsx src/features/tech-ops/pages/TechOpsPage.jsx src/features/tech-ops/pages/TechOpsPage.test.jsx

cd ..
git diff --check
```

Expected time: 1–5 minutes with dependencies installed. Success means every command exits 0. A FastAPI dependency deprecation warning and Git line-ending warnings are acceptable.

## 2. Migration and startup

Start the normal Postgres/backend/AI/frontend stack. Confirm Flyway applies `V4__v2_10e2_techops_proposal_async.sql` and backend starts without schema-validation errors.

Collect on failure: migration name, SQL state, first backend `Caused by`, and `tech_ops_input_preparations` columns/constraints.

## 3. Provider-free initialization and single batch task

Use a finalized Market Seed whose TechOps preparation is not yet created and at least one operating proposal is missing.

1. Open `/projects/{projectId}/tech-ops`.
2. Confirm initialize returns immediately with a preparation; it must not wait for provider completion.
3. Confirm Query has `proposalGenerationStatus=QUEUED|RUNNING`, one `activeProposalTaskRunId`, and UI text `AI 운영 가설 생성 중`.
4. Repeat the initialize HTTP request for the same preparation. Confirm no second active TaskRun is created.
5. Refresh during execution. Confirm pending state restores from Query.
6. On completion, confirm one batch result supplies real non-null values for delivery/production method, expected monthly throughput/sales, and technical/supply/operational constraints.

## 4. Alternative action

1. Click `다른 제안 요청` on a proposal with a current value.
2. Confirm a fresh `Idempotency-Key` and immediate `202` with `taskRunId=jobId`, `status=QUEUED`, field key, and next proposal version.
3. Confirm the previous proposal remains visible and unchanged while `새 제안 생성 중` is shown.
4. After terminal signal, confirm Query refresh shows exactly `proposalVersion + 1`, `source=AI_HYPOTHESIS`, `decision=PROPOSED`, `finalValue=null`, and a meaningfully different value.
5. Confirm Job Center shows `TECH OPS PROPOSAL` under active then recent completed/failed.

## 5. Failure, direct edit, and retry

Use the supported safe failure mechanism for an initial or alternative provider call.

1. Confirm TaskRun becomes `FAILED` and Query has `proposalGenerationStatus=FAILED` with only a safe error code.
2. Confirm UI shows `AI 제안 생성 실패 — 직접 입력하거나 다시 시도할 수 있습니다.`
3. Enter a valid value with `수정 후 확정`. Confirm it succeeds without provider access even while an older AI request is late.
4. Resume/complete the old worker and confirm it ends as `STALE_ACTION_RESULT` without overwriting the user value.
5. For failed initial generation, click `AI 제안 다시 시도`. Confirm a new command key and new TaskRun/Job ID.
6. For failed alternative, click `다른 제안 요청` again. Confirm a new TaskRun/Job ID.

## 6. Snapshot stale guard

Pause a proposal worker after provider response and finalize a valid TechOps Snapshot through a controlled local concurrency test. Resume the worker.

Success criteria: the finalized Snapshot remains immutable, the late worker cannot update proposals, TaskRun ends safely with `STALE_ACTION_RESULT`, and exactly one terminal JobEvent exists.

## 7. Logs to collect on failure

Collect only:

- preparation ID/revision/status/active task ID
- field proposal version and pending task ID
- source Market Seed Snapshot ID/hash
- TaskRun/TaskAttempt status and safe reason
- ordered JobEvent sequence/status

Do not collect provider prompts, raw responses, authorization headers, or secrets.

## 8. Proceed condition

Proceed to V2-10E3 only after initialization latency, single batch identity, 202 alternative, direct-edit-wins, new-ID retry, Snapshot stale guard, refresh recovery, and Job Center transitions pass. Otherwise retain E2 as runtime acceptance pending.
