# V2-10E1 — Concept Selection Async Result

## Outcome

Implementation complete. Runtime acceptance remains pending under the Local Fast Execution Profile.

`REQUEST_ALTERNATIVE`와 legal-sensitive 변경의 Delta Legal 호출을 HTTP transaction 밖의 독립 TaskRun worker로 이동했다. 기존 proposal/final value는 provider 및 stale 검증을 통과한 domain commit 전까지 보존된다. provider-free action은 동기 완료 경로를 유지한다.

## Files changed

Backend:

- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java`
- `backend/src/main/java/com/aivle/backend/taskrun/service/ProjectJobQueryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/api/ConceptSelectionController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/api/SelectionApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionActionCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/worker/ConceptSelectionActionWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/ConceptSelection.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/repository/ConceptSelectionRepository.java`
- `backend/src/main/resources/db/migration/V3__v2_10e1_concept_selection_async.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/ConceptSelectionServiceV2Tests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/ConceptSelectionActionCompletionServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/ConceptSelectionControllerAsyncTests.java`

AI:

- `ai/app/api/executions.py`
- `ai/tests/test_internal_task_type_alignment.py`

Frontend:

- `frontEnd/src/features/concept-selection/hooks/useConceptSelection.js`
- `frontEnd/src/features/concept-selection/hooks/useConceptSelection.test.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx`

Governing documents:

- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`

## Contracts implemented

- 새 TaskRun type `CONCEPT_DELTA_LEGAL_REVIEW`를 추가하고 기존 `CONCEPT_HYPOTHESIS_ALTERNATIVE`와 Job Center에서 별도 type으로 분류한다.
- provider-backed hypothesis action은 `Idempotency-Key`를 요구하고 `202 Accepted`와 `taskRunId=jobId`, `QUEUED`, action/hypothesis/version을 반환한다.
- baseline 동일 accept 및 non-legal edit는 `200 OK` 동기 DB completion을 유지한다.
- Alternative command는 기존 decision을 즉시 reject하지 않고 pending action만 저장한다. worker success에서만 이전 proposal을 reject하고 다른 값의 versioned `AI_HYPOTHESIS` alternative를 저장한다.
- Delta Legal은 eligible result에서만 final value를 accept한다. `REDESIGNABLE`, `REJECTED`, `NEEDS_FACTS`는 TaskRun provider execution 성공과 구분된 `LEGAL_INELIGIBLE` domain outcome이다.
- provider/network/schema 실패는 TaskRun `FAILED`, selection action `FAILED`로 종료하며 기존 decision을 보존하고 새 command key retry를 허용한다.
- worker commit 전 current selection, decision ID/version, pending TaskRun ID, concept ID/hash를 재검사한다. 불일치는 `STALE_ACTION_RESULT`로 종료하며 최신 상태를 덮어쓰지 않는다.
- selection query에 active/pending/action/error 상태와 latest decisions를 함께 반환한다.
- Frontend는 action마다 새 command key를 만들고, 새로고침 시 Query pending 상태를 복원하며, terminal SSE 뒤 selection Query를 다시 조회한다.
- terminal JobEvent는 completion transaction이 반환된 뒤 한 번만 발행하며 기존 TaskRun/JobEvent terminal immutability를 유지한다.
- AI router는 command metadata를 provider strict input에서 분리해 Alternative와 Delta Legal task를 기존 strict service로 전달한다.

## Checks actually run

- `backend\\gradlew.bat compileJava` — success. 첫 실행은 잘못된 `SnapshotHasher` import로 1회 실패했고 import 수정 후 성공했다.
- `backend\\gradlew.bat testClasses` — success.
- Targeted Backend tests — 16 passed, 0 failed:
  - `ConceptSelectionControllerAsyncTests` 2
  - `ConceptSelectionServiceV2Tests` 5
  - `ConceptSelectionActionCompletionServiceTests` 4
  - `ConceptHypothesisDecisionTests` 5
- Targeted AI tests after final router change — 14 passed, 1 dependency deprecation warning:
  - `test_internal_task_type_alignment.py`
  - `test_concept_hypothesis_alternative.py`
  - `test_concept_legal_evidence.py`
- Earlier E1 AI targeted set including `test_concept_factory_schema.py` — 22 passed.
- Targeted Frontend Vitest — 10 passed, 0 failed:
  - `HypothesisDecisionPanel.test.jsx`
  - `useConceptSelection.test.jsx`
- Targeted ESLint for changed Concept Selection frontend files — success, no findings.
- `git diff --check` — success; Git emitted only existing LF-to-CRLF conversion warnings for AI files.

## Checks intentionally omitted

- Backend full regression and full postgres/Testcontainers suite
- AI full suite
- Frontend full baseline and production build
- Docker Compose/browser/provider smoke
- manual accessibility/mobile acceptance

These are intentionally deferred by `LOCAL_FAST_EXECUTION_PROFILE.md`.

## Remaining risks

- Real DB migration application, worker lease recovery, SSE reconnect, and Job Center active/recent movement need runtime verification.
- Real AI provider responses for both new TaskRun routes were not invoked.
- Same-key replay after an already-completed alternative may meet the newer proposal-version guard before replay lookup; normal in-flight HTTP replay and new-key retry are covered, but this completed-command replay edge should be exercised during runtime acceptance.
- Full-suite interactions outside Concept Selection were not evaluated in this Unit.

## Exact continuation point

Next Unit is `V2-10E2 — TECHOPS ASYNC HARDENING`. Begin only after this E1 result and user verification document exist. Do not implement E3/F/G work while executing E2.
