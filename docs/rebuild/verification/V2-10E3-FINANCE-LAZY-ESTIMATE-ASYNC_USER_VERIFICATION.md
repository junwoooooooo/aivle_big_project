# V2-10E3 — Finance Lazy Estimate + Async Hardening User Verification

## Preconditions

- Run the application with its normal database, AI provider credentials, worker scheduling, and SSE/polling configuration.
- Use a project with a finalized TechOps Input Snapshot and no finalized Finance Input Snapshot.
- Keep browser developer tools open for Network inspection.

## 1. Provider-free initialization

1. Open the Finance preparation page for the first time.
2. Confirm the page loads without waiting for multiple AI estimates.
3. Confirm inherited TechOps values are shown read-only and missing values remain empty.
4. Confirm eligible empty fields show `추천 없음` and an `AI 추천 받기` button.
5. Confirm `newCustomerCount` has no AI recommendation control.
6. If `threeYearTargets` was inherited from TechOps, confirm it is read-only and has no recommendation button.

Expected: initialization stores only preparation/help data. No `FINANCE_ESTIMATE` TaskRun is created until a recommendation button is pressed.

## 2. Lazy generation and terminal refresh

1. Press `AI 추천 받기` for one editable money field.
2. Confirm the POST to `/finance/preparation/assistance/{fieldKey}/generate` includes a non-empty `Idempotency-Key`.
3. Confirm the response is HTTP `202` and contains `taskRunId`, `jobId`, `QUEUED`, field key, and proposal version 1.
4. Confirm the field status changes to `추천 생성 중` while the TaskRun is queued/running.
5. Wait for terminal SSE/polling and confirm the page refreshes from Query.

Expected: only the requested field receives `proposalValue`, assumptions, explanation, confidence, source `AI_ESTIMATE`, decision `PROPOSED`, version 1, and status `AI 추천`. The final financial field remains unchanged.

## 3. Accept and edit-and-accept

1. On one generated proposal, press `AI 추천 채택`.
2. Confirm the response is HTTP `200`, no new TaskRun is created, and Query shows source `AI_ESTIMATE`, decision `ACCEPTED`, status `채택됨`.
3. Generate a proposal for another field, type a different valid value, and press `입력값으로 수정 후 채택`.
4. Confirm HTTP `200`, no provider call, source `USER_INPUT`, and decision `USER_EDITED_ACCEPTED`.

Expected: only explicit user decisions promote a proposed estimate into the final financial fields.

## 4. Alternative and stale-result protection

1. Generate a proposal and press `다른 추천 요청`.
2. Confirm HTTP `202`, a new TaskRun ID, and requested proposal version 2 while the version 1 proposal remains visible.
3. Before the worker finishes, directly save a different value or accept/edit the current proposal.
4. Wait for the old TaskRun to terminate and refresh Query.

Expected: the direct user action wins. The late result does not overwrite it and the old TaskRun terminates as a safe stale failure.

## 5. Technical failure and retry

1. Temporarily make the AI provider unavailable or use a controlled provider failure.
2. Request a recommendation and wait for terminal state.
3. Confirm the field and any prior proposal are unchanged, the UI shows `추천 생성 실패`, and no raw provider detail is exposed.
4. Enter a value directly and save it; confirm this still works.
5. Alternatively restore the provider and press the recommendation button again.

Expected: retry uses a fresh command key/new TaskRun. Direct input is never blocked by provider failure.

## 6. Snapshot and CAC

1. Leave one AI recommendation in `PROPOSED` state without accepting it.
2. Complete all required final financial fields through inherited/user/accepted values and finalize the snapshot.
3. Inspect the snapshot response/body.
4. Confirm the unaccepted proposal is not used in `values` and its `proposalValue` is absent from snapshot assistance.
5. Set marketing cost to 1,000 KRW, sales cost to 500 KRW, and new customer count to 30.

Expected: CAC is 50.00 KRW from the deterministic server formula, independent of AI assistance.

## 7. Refresh recovery

1. Start one estimate and reload the browser while it is queued/running.
2. Confirm Query restores `추천 생성 중` using `estimateStatus` and `activeTaskRunId`.
3. After terminal completion/failure, confirm Query restores `AI 추천` or `추천 생성 실패` without relying on missed SSE events.

## Acceptance record

Record the project ID, preparation ID, field key, command key, TaskRun IDs, HTTP statuses, terminal event, final Query state, and snapshot hash. Runtime acceptance remains pending until these checks pass in the user's environment.
