# V2-10E1 — Concept Selection Async User Verification

## Purpose

Verify the real database, backend worker, AI service, SSE/polling recovery, Query rendering, and Job Center behavior for Concept Selection Alternative and Delta Legal actions.

## 1. Automated targeted checks

From the repository root:

```powershell
cd backend
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.aivle.backend.pipeline.selection.ConceptSelectionControllerAsyncTests" --tests "com.aivle.backend.pipeline.selection.ConceptSelectionServiceV2Tests" --tests "com.aivle.backend.pipeline.selection.ConceptSelectionActionCompletionServiceTests" --tests "com.aivle.backend.pipeline.selection.ConceptHypothesisDecisionTests"

cd ..\ai
.\.venv\Scripts\python.exe -m pytest tests/test_internal_task_type_alignment.py tests/test_concept_hypothesis_alternative.py tests/test_concept_legal_evidence.py -q

cd ..\frontEnd
npm.cmd run test:run -- src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx src/features/concept-selection/hooks/useConceptSelection.test.jsx
npx.cmd eslint src/features/concept-selection/hooks/useConceptSelection.js src/features/concept-selection/hooks/useConceptSelection.test.jsx src/features/concept-selection/components/HypothesisDecisionPanel.jsx src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx src/features/concept-selection/api/conceptSelectionApi.js

cd ..
git diff --check
```

Expected time: 1–4 minutes with dependencies already installed.

Success criteria: all commands exit 0. A FastAPI deprecation warning and Git line-ending warnings are acceptable; test failures and whitespace errors are not.

## 2. Migration and startup

Start the normal local Postgres, backend, AI, and frontend stack. Confirm Flyway applies `V3__v2_10e1_concept_selection_async.sql` and backend startup has no JPA/schema validation error.

Collect on failure:

- Flyway migration name and SQL state
- backend startup exception from the first `Caused by`
- current `concept_selections` column definitions and constraints

## 3. Alternative action

Open `/projects/{projectId}/concepts/compare` with a completed Concept Factory run and a current selection containing an open AI hypothesis.

1. Click `다른 제안` once.
2. In browser Network, confirm the action request has a non-empty `Idempotency-Key` and returns `202` before provider completion.
3. Confirm response has distinct `taskRunId`/`jobId` identity with the same value, `status=QUEUED`, `actionType=REQUEST_ALTERNATIVE`, the hypothesis type, and current proposal version.
4. While running, confirm the old proposal/value and proposal version remain visible and the affected row is disabled with `다른 제안을 만들고 있습니다.`
5. Refresh the browser before completion. Confirm the pending message and disabled state restore from the selection Query.
6. After terminal SSE/polling, confirm the page refetches Query and shows `새 제안이 준비되었습니다.` with proposal version incremented by exactly one and a materially different value.
7. Confirm Job Center shows `CONCEPT HYPOTHESIS ALTERNATIVE` under active while queued/running and recent completed after success.

## 4. Alternative failure and retry

Using the normal safe provider-failure test mechanism, fail one Alternative provider call.

1. Confirm TaskRun becomes `FAILED` and the old proposal/version remains unchanged.
2. Confirm the UI shows `AI 요청을 완료하지 못했습니다. 다시 시도할 수 있습니다.`
3. Retry as a new user action and confirm a new `Idempotency-Key`, TaskRun ID, and Job ID are used.
4. Confirm the failed terminal job receives no later JobEvent.

Collect on failure: action response, selection Query response, TaskRun/TaskAttempt state, and ordered JobEvents by sequence. Do not collect provider prompt/raw response or secrets.

## 5. Delta Legal eligible and ineligible

For an open legal-sensitive hypothesis such as revenue model or price:

1. Choose `수정 후 채택` and enter a value different from the selected Concept baseline.
2. Confirm immediate `202`, task type `CONCEPT_DELTA_LEGAL_REVIEW`, and `변경된 조건의 법률 영향을 확인하고 있습니다.`
3. Before terminal completion, confirm `finalValue` remains null and the decision remains unaccepted.
4. With `IMPLEMENTABLE` or `IMPLEMENTABLE_WITH_CONTROLS`, confirm terminal TaskRun `SUCCEEDED`, accepted final value, and legal review `PASSED`.
5. With `REDESIGNABLE`, `REJECTED`, or `NEEDS_FACTS`, confirm TaskRun execution is `SUCCEEDED`, selection `actionStatus=LEGAL_INELIGIBLE`, decision is not accepted, and UI shows `법률 조건을 통과하지 못했습니다.`
6. With provider/network/schema failure, confirm TaskRun is `FAILED`, decision/final value is unchanged, and retry is available.
7. Confirm Job Center labels Delta runs as `CONCEPT DELTA LEGAL REVIEW`, distinct from `CONCEPT LEGAL REVIEW`.

## 6. Provider-free action

Accept an unchanged proposal or edit `PRE_MARKET_SOM`.

Success criteria: the endpoint returns `200`, no new Alternative/Delta TaskRun appears, and the decision completes synchronously.

## 7. Stale result guard

Pause a worker after provider execution but before commit using the supported local diagnostic breakpoint/failure hook. Change or supersede the current selection/decision, then resume the old worker.

Success criteria:

- old result cannot overwrite the newer selection or decision
- TaskRun ends with safe reason `STALE_ACTION_RESULT`
- no alternative/final value from the stale result is committed
- exactly one terminal JobEvent is present

## 8. Proceed condition

Proceed to V2-10E2 only when migration/startup, 202 behavior, pending refresh recovery, success/failure/ineligible separation, new-ID retry, stale protection, and Job Center labels all pass. If a failure occurs, retain E1 as runtime acceptance pending and capture only safe IDs, statuses, error codes, and event sequence metadata.
